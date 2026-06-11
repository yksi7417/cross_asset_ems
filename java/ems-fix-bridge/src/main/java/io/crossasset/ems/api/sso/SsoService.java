/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api.sso;

import io.crossasset.ems.aaa.CredentialKind;
import io.crossasset.ems.aaa.InMemoryAaaService;
import io.crossasset.ems.aaa.LogonCredentials;
import io.crossasset.ems.aaa.LogonOutcome;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Enterprise SSO on the AAA layer (task 18.9): OIDC authenticates, SCIM provisions — the vendor
 * due-diligence pair. {@link #logonWithIdToken} validates the IdP's ID token and establishes a
 * normal AAA session; identity comes from the SCIM-provisioned record when one exists (the
 * enterprise source of truth), falling back to token claims only when JIT provisioning is enabled.
 * IdP groups map to EMS permission tags through an explicit table — nothing implicit.
 *
 * <p>{@link ScimUser} create/update/deactivate mirrors SCIM 2.0 Users semantics: deactivation
 * removes the logon credential immediately (new logons fail; live sessions end through the normal
 * session lifecycle, as with any credential revocation).
 */
public final class SsoService {

  /** Internal credential namespace for SSO subjects. */
  static String credentialToken(String subject) {
    return "sso:" + subject;
  }

  /** A provisioned user (SCIM 2.0 Users subset). */
  public record ScimUser(
      String userName, // the IdP subject
      String displayName,
      String firm,
      String desk,
      Set<String> groups,
      boolean active) {}

  /** SSO logon outcome. */
  public sealed interface SsoLogon {
    record Accepted(long sessionId, String firm, String desk, String user) implements SsoLogon {}

    record Rejected(String reason) implements SsoLogon {}
  }

  private final InMemoryAaaService aaa;
  private final OidcValidator oidc;
  private final Map<String, String> groupToTag;
  private final boolean jitProvisioning;
  private final String defaultFirm;
  private final String defaultDesk;
  private final Map<String, ScimUser> scimUsers = new LinkedHashMap<>();

  /**
   * @param groupToTag IdP group → EMS permission tag (e.g. {@code ems-kill-switch → #kill-switch})
   * @param jitProvisioning allow logon for subjects without a SCIM record, identity from claims
   */
  public SsoService(
      InMemoryAaaService aaa,
      OidcValidator oidc,
      Map<String, String> groupToTag,
      boolean jitProvisioning,
      String defaultFirm,
      String defaultDesk) {
    this.aaa = Objects.requireNonNull(aaa, "aaa");
    this.oidc = Objects.requireNonNull(oidc, "oidc");
    this.groupToTag = Map.copyOf(Objects.requireNonNull(groupToTag, "groupToTag"));
    this.jitProvisioning = jitProvisioning;
    this.defaultFirm = Objects.requireNonNull(defaultFirm, "defaultFirm");
    this.defaultDesk = Objects.requireNonNull(defaultDesk, "defaultDesk");
  }

  // ── OIDC authentication ──────────────────────────────────────────────────────

  /** Validate the ID token and establish an AAA session. */
  public synchronized SsoLogon logonWithIdToken(String idToken, long nowMillis) {
    OidcValidator.Result result = oidc.validate(idToken, nowMillis);
    if (result instanceof OidcValidator.Result.Invalid invalid) {
      return new SsoLogon.Rejected(invalid.reason());
    }
    OidcValidator.Claims claims = ((OidcValidator.Result.Valid) result).claims();

    String firm;
    String desk;
    String user;
    Set<String> groups;
    ScimUser provisioned = scimUsers.get(claims.subject());
    if (provisioned != null) {
      if (!provisioned.active()) {
        return new SsoLogon.Rejected("user is deactivated (SCIM)");
      }
      firm = provisioned.firm();
      desk = provisioned.desk();
      user = provisioned.userName();
      groups = provisioned.groups();
    } else if (jitProvisioning) {
      firm = claims.firm() != null ? claims.firm() : defaultFirm;
      desk = claims.desk() != null ? claims.desk() : defaultDesk;
      user = claims.subject();
      groups = new LinkedHashSet<>(claims.groups());
    } else {
      return new SsoLogon.Rejected("subject not provisioned (SCIM record required)");
    }

    String token = credentialToken(claims.subject());
    aaa.registerCredential(token, firm, desk, user, mapTags(groups));
    LogonOutcome outcome = aaa.logon(LogonCredentials.fresh(CredentialKind.TOKEN, token));
    if (outcome instanceof LogonOutcome.Rejected rejected) {
      return new SsoLogon.Rejected(rejected.rejectCode() + ": " + rejected.message());
    }
    var session = ((LogonOutcome.Accepted) outcome).session();
    return new SsoLogon.Accepted(session.sessionId(), firm, desk, user);
  }

  // ── SCIM provisioning ────────────────────────────────────────────────────────

  /** Create or replace a user (SCIM POST/PUT). Active users get a logon credential at once. */
  public synchronized ScimUser provision(ScimUser user) {
    Objects.requireNonNull(user, "user");
    scimUsers.put(user.userName(), user);
    if (user.active()) {
      aaa.registerCredential(
          credentialToken(user.userName()),
          user.firm(),
          user.desk(),
          user.userName(),
          mapTags(user.groups()));
    } else {
      aaa.removeCredential(credentialToken(user.userName()));
    }
    return user;
  }

  /** Deactivate (SCIM PATCH active=false / DELETE): new logons fail immediately. */
  public synchronized boolean deactivate(String userName) {
    ScimUser user = scimUsers.get(userName);
    if (user == null) {
      return false;
    }
    scimUsers.put(
        userName,
        new ScimUser(
            user.userName(), user.displayName(), user.firm(), user.desk(), user.groups(), false));
    aaa.removeCredential(credentialToken(userName));
    return true;
  }

  public synchronized Optional<ScimUser> findUser(String userName) {
    return Optional.ofNullable(scimUsers.get(userName));
  }

  public synchronized List<ScimUser> listUsers() {
    return List.copyOf(scimUsers.values());
  }

  /** IdP groups → EMS tags through the explicit table; unmapped groups carry no permissions. */
  private Set<String> mapTags(Set<String> groups) {
    Set<String> tags = new LinkedHashSet<>();
    for (String group : groups) {
      String tag = groupToTag.get(group);
      if (tag != null) {
        tags.add(tag);
      }
    }
    return tags;
  }
}
