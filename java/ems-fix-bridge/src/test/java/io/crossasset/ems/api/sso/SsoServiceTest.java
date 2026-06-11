/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.sso;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crossasset.ems.aaa.InMemoryAaaEventLog;
import io.crossasset.ems.aaa.InMemoryAaaService;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Enterprise SSO tests (task 18.9) with real crypto: RSA-signed ID tokens minted in-test and
 * verified through the JDK (no JOSE library), claim checks (issuer/audience/expiry/tamper), SCIM
 * provisioning precedence over JIT claims, group→tag mapping, and deactivation blocking logons.
 */
class SsoServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final String ISSUER = "https://idp.example.com";
  private static final String AUDIENCE = "ems-desktop";

  private static KeyPair keyPair;
  private static OidcValidator.Config rs256Config;

  @BeforeAll
  static void mintKeys() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keyPair = generator.generateKeyPair();
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    rs256Config =
        OidcValidator.Config.rs256(
            ISSUER,
            AUDIENCE,
            B64.encodeToString(publicKey.getModulus().toByteArray()),
            B64.encodeToString(publicKey.getPublicExponent().toByteArray()));
  }

  private SsoService sso(boolean jit) {
    return new SsoService(
        new InMemoryAaaService(new InMemoryAaaEventLog()),
        new OidcValidator(rs256Config),
        Map.of("ems-kill-switch", "#kill-switch", "ems-htb", "#htb-short-permitted"),
        jit,
        "firm-default",
        "desk-default");
  }

  private String idToken(String subject, long expEpochSeconds, List<String> groups)
      throws Exception {
    ObjectNode payload = MAPPER.createObjectNode();
    payload.put("iss", ISSUER);
    payload.put("aud", AUDIENCE);
    payload.put("sub", subject);
    payload.put("exp", expEpochSeconds);
    payload.put("email", subject + "@example.com");
    payload.put("firm", "firm-claims");
    payload.put("desk", "desk-claims");
    var groupsNode = payload.putArray("groups");
    groups.forEach(groupsNode::add);
    return sign(payload);
  }

  private String sign(ObjectNode payload) throws Exception {
    String header =
        B64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String body = B64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(keyPair.getPrivate());
    signer.update((header + "." + body).getBytes(StandardCharsets.US_ASCII));
    return header + "." + body + "." + B64.encodeToString(signer.sign());
  }

  @Test
  void validToken_jit_establishesSessionFromClaims() throws Exception {
    SsoService sso = sso(true);
    var outcome = sso.logonWithIdToken(idToken("alice", 2_000_000_000L, List.of()), 1_000L);

    var accepted = (SsoService.SsoLogon.Accepted) outcome;
    assertThat(accepted.sessionId()).isPositive();
    assertThat(accepted.firm()).isEqualTo("firm-claims");
    assertThat(accepted.desk()).isEqualTo("desk-claims");
    assertThat(accepted.user()).isEqualTo("alice");
  }

  @Test
  void tamperedPayload_rejected() throws Exception {
    SsoService sso = sso(true);
    String token = idToken("alice", 2_000_000_000L, List.of());
    String[] parts = token.split("\\.");
    String tampered =
        parts[0]
            + "."
            + B64.encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                    .replace("alice", "mallory")
                    .getBytes(StandardCharsets.UTF_8))
            + "."
            + parts[2];

    var outcome = sso.logonWithIdToken(tampered, 1_000L);
    assertThat(((SsoService.SsoLogon.Rejected) outcome).reason()).contains("signature");
  }

  @Test
  void expiredToken_wrongIssuer_wrongAudience_rejected() throws Exception {
    SsoService sso = sso(true);

    var expired = sso.logonWithIdToken(idToken("alice", 1L, List.of()), 10_000_000L);
    assertThat(((SsoService.SsoLogon.Rejected) expired).reason()).contains("expired");

    ObjectNode badIssuer = MAPPER.createObjectNode();
    badIssuer.put("iss", "https://evil.example.com");
    badIssuer.put("aud", AUDIENCE);
    badIssuer.put("sub", "alice");
    var issuerOutcome = sso.logonWithIdToken(sign(badIssuer), 0L);
    assertThat(((SsoService.SsoLogon.Rejected) issuerOutcome).reason()).contains("issuer");

    ObjectNode badAudience = MAPPER.createObjectNode();
    badAudience.put("iss", ISSUER);
    badAudience.put("aud", "other-app");
    badAudience.put("sub", "alice");
    var audienceOutcome = sso.logonWithIdToken(sign(badAudience), 0L);
    assertThat(((SsoService.SsoLogon.Rejected) audienceOutcome).reason()).contains("audience");
  }

  @Test
  void hs256SharedSecret_path() throws Exception {
    byte[] secret = "a-32-byte-shared-secret-for-hmac".getBytes(StandardCharsets.UTF_8);
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    SsoService sso =
        new SsoService(
            aaa,
            new OidcValidator(OidcValidator.Config.hs256(ISSUER, AUDIENCE, secret)),
            Map.of(),
            true,
            "firm-default",
            "desk-default");

    ObjectNode payload = MAPPER.createObjectNode();
    payload.put("iss", ISSUER);
    payload.put("aud", AUDIENCE);
    payload.put("sub", "bob");
    String header =
        B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String body = B64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
    javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
    String token =
        header
            + "."
            + body
            + "."
            + B64.encodeToString(
                mac.doFinal((header + "." + body).getBytes(StandardCharsets.US_ASCII)));

    assertThat(sso.logonWithIdToken(token, 0L)).isInstanceOf(SsoService.SsoLogon.Accepted.class);
  }

  @Test
  void scimRecord_winsOverClaims_andMapsGroupsToTags() throws Exception {
    InMemoryAaaService aaa = new InMemoryAaaService(new InMemoryAaaEventLog());
    SsoService sso =
        new SsoService(
            aaa,
            new OidcValidator(rs256Config),
            Map.of("ems-kill-switch", "#kill-switch"),
            false,
            "firm-default",
            "desk-default");
    sso.provision(
        new SsoService.ScimUser(
            "alice", "Alice T", "firm-scim", "desk-scim", Set.of("ems-kill-switch"), true));

    var outcome =
        (SsoService.SsoLogon.Accepted)
            sso.logonWithIdToken(idToken("alice", 2_000_000_000L, List.of()), 0L);

    assertThat(outcome.firm()).isEqualTo("firm-scim");
    assertThat(outcome.desk()).isEqualTo("desk-scim");
    var identity = aaa.sessionInfo(outcome.sessionId()).orElseThrow().identity();
    assertThat(identity.effectiveTags()).contains("#kill-switch");
  }

  @Test
  void noScimRecord_withoutJit_rejected() throws Exception {
    SsoService sso = sso(false);
    var outcome = sso.logonWithIdToken(idToken("ghost", 2_000_000_000L, List.of()), 0L);
    assertThat(((SsoService.SsoLogon.Rejected) outcome).reason()).contains("not provisioned");
  }

  @Test
  void scimDeactivation_blocksNewLogons() throws Exception {
    SsoService sso = sso(false);
    sso.provision(new SsoService.ScimUser("alice", "Alice T", "firm-a", "desk-1", Set.of(), true));
    assertThat(sso.logonWithIdToken(idToken("alice", 2_000_000_000L, List.of()), 0L))
        .isInstanceOf(SsoService.SsoLogon.Accepted.class);

    assertThat(sso.deactivate("alice")).isTrue();
    var afterDeactivation = sso.logonWithIdToken(idToken("alice", 2_000_000_000L, List.of()), 0L);
    assertThat(((SsoService.SsoLogon.Rejected) afterDeactivation).reason()).contains("deactivated");
    assertThat(sso.findUser("alice").orElseThrow().active()).isFalse();
  }
}
