/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * OIDC ID-token validation (task 18.9), dependency-free: JWT parsing via Jackson + Base64url, RS256
 * through {@code java.security.Signature} (JWKS modulus/exponent configured as key material) and
 * HS256 through {@code javax.crypto.Mac} with constant-time comparison. Checks signature, issuer,
 * audience, expiry and not-before (with clock skew), then exposes the claims the identity mapping
 * consumes. SAML assertions slot in behind the same {@link SsoService} with a parser of their own —
 * the session-establishment path is shared.
 */
public final class OidcValidator {

  /** Key material: exactly one of HS256 secret or RS256 (n, e) must be configured. */
  public record Config(
      String issuer,
      String audience,
      long clockSkewMillis,
      @Nullable byte[] hs256Secret,
      @Nullable String rs256ModulusB64Url,
      @Nullable String rs256ExponentB64Url) {

    public static Config rs256(String issuer, String audience, String nB64Url, String eB64Url) {
      return new Config(issuer, audience, 60_000L, null, nB64Url, eB64Url);
    }

    public static Config hs256(String issuer, String audience, byte[] secret) {
      return new Config(issuer, audience, 60_000L, secret.clone(), null, null);
    }
  }

  /** Validated claims (the subset the EMS maps). */
  public record Claims(
      String subject,
      @Nullable String email,
      @Nullable String name,
      @Nullable String firm,
      @Nullable String desk,
      List<String> groups) {}

  /** Validation outcome. */
  public sealed interface Result {
    record Valid(Claims claims) implements Result {}

    record Invalid(String reason) implements Result {}
  }

  private final ObjectMapper mapper = new ObjectMapper();
  private final Config config;
  private final @Nullable PublicKey rsaKey;

  public OidcValidator(Config config) {
    this.config = Objects.requireNonNull(config, "config");
    if ((config.hs256Secret() == null) == (config.rs256ModulusB64Url() == null)) {
      throw new IllegalArgumentException("configure exactly one of HS256 secret or RS256 key");
    }
    try {
      this.rsaKey =
          config.rs256ModulusB64Url() == null
              ? null
              : KeyFactory.getInstance("RSA")
                  .generatePublic(
                      new RSAPublicKeySpec(
                          new BigInteger(1, b64Url(config.rs256ModulusB64Url())),
                          new BigInteger(1, b64Url(config.rs256ExponentB64Url()))));
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid RS256 key material: " + e.getMessage(), e);
    }
  }

  /** Validate one compact-serialized JWT. Never throws on token input. */
  public Result validate(String jwt, long nowMillis) {
    try {
      String[] parts = jwt.split("\\.");
      if (parts.length != 3) {
        return new Result.Invalid("malformed token: expected 3 segments");
      }
      JsonNode header = mapper.readTree(b64Url(parts[0]));
      JsonNode payload = mapper.readTree(b64Url(parts[1]));
      byte[] signature = b64Url(parts[2]);
      byte[] signedBytes = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);

      String alg = header.path("alg").asText("");
      boolean signatureOk =
          switch (alg) {
            case "RS256" -> rsaKey != null && verifyRs256(signedBytes, signature);
            case "HS256" -> config.hs256Secret() != null && verifyHs256(signedBytes, signature);
            default -> false;
          };
      if (!signatureOk) {
        return new Result.Invalid("signature verification failed (alg=" + alg + ")");
      }

      if (!config.issuer().equals(payload.path("iss").asText())) {
        return new Result.Invalid("issuer mismatch");
      }
      if (!audienceMatches(payload.path("aud"))) {
        return new Result.Invalid("audience mismatch");
      }
      long skew = config.clockSkewMillis();
      if (payload.has("exp") && nowMillis > payload.get("exp").asLong() * 1000 + skew) {
        return new Result.Invalid("token expired");
      }
      if (payload.has("nbf") && nowMillis < payload.get("nbf").asLong() * 1000 - skew) {
        return new Result.Invalid("token not yet valid");
      }
      String subject = payload.path("sub").asText("");
      if (subject.isBlank()) {
        return new Result.Invalid("missing sub claim");
      }

      List<String> groups = new ArrayList<>();
      for (JsonNode group : payload.path("groups")) {
        groups.add(group.asText());
      }
      return new Result.Valid(
          new Claims(
              subject,
              textOrNull(payload, "email"),
              textOrNull(payload, "name"),
              textOrNull(payload, "firm"),
              textOrNull(payload, "desk"),
              List.copyOf(groups)));
    } catch (Exception e) {
      return new Result.Invalid("token parse failed: " + e.getMessage());
    }
  }

  private boolean verifyRs256(byte[] signedBytes, byte[] signature) throws Exception {
    Signature verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(rsaKey);
    verifier.update(signedBytes);
    return verifier.verify(signature);
  }

  private boolean verifyHs256(byte[] signedBytes, byte[] signature) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(config.hs256Secret(), "HmacSHA256"));
    return MessageDigest.isEqual(mac.doFinal(signedBytes), signature);
  }

  private boolean audienceMatches(JsonNode aud) {
    if (aud.isArray()) {
      for (JsonNode entry : aud) {
        if (config.audience().equals(entry.asText())) {
          return true;
        }
      }
      return false;
    }
    return config.audience().equals(aud.asText());
  }

  private static @Nullable String textOrNull(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  private static byte[] b64Url(String value) {
    return Base64.getUrlDecoder().decode(value);
  }
}
