/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.validator;

import io.crossasset.ems.aaa.AaaService;
import io.crossasset.ems.aaa.Identity;
import io.crossasset.ems.aaa.Session;
import io.crossasset.ems.aaa.permission.AuthorizationResult;
import io.crossasset.ems.aaa.permission.TagPermissionEvaluator;
import io.crossasset.ems.instrument.InstrumentVersioned;
import io.crossasset.ems.instrument.SecurityMasterService;
import java.util.Objects;
import java.util.Optional;

/**
 * Concrete {@link ValidatorPipeline} that runs the eight fixed evaluation layers per
 * arch-validator.md. Layers are always evaluated in fixed order (SESSION → IDENTITY → REFERENCE →
 * PERMISSION → ASSET_CLASS → LIMITS → MARKET → ROUTE); the first failure short-circuits.
 *
 * <p>Layers 5–8 (ASSET_CLASS, LIMITS, MARKET, ROUTE) are stubs that always pass — they will be
 * wired in Phases 7, 9, and 10 respectively.
 *
 * <p>Optional dependencies: when {@code securityMasterService} is null the REFERENCE layer is
 * skipped; when {@code tagPermissionEvaluator} is null the PERMISSION layer is skipped. This lets
 * callers opt in to only the layers they have data for.
 *
 * <p>Task 6.2 — Layered evaluation pipeline.
 */
public final class LayeredValidatorPipeline implements ValidatorPipeline {

  private final AaaService aaaService;
  private final SecurityMasterService securityMasterService; // nullable
  private final TagPermissionEvaluator tagPermissionEvaluator; // nullable

  /** Full constructor — all four active layers enabled. */
  public LayeredValidatorPipeline(
      AaaService aaaService,
      SecurityMasterService securityMasterService,
      TagPermissionEvaluator tagPermissionEvaluator) {
    this.aaaService = Objects.requireNonNull(aaaService, "aaaService");
    this.securityMasterService = securityMasterService;
    this.tagPermissionEvaluator = tagPermissionEvaluator;
  }

  /** Minimal constructor — only SESSION and IDENTITY layers active. */
  public LayeredValidatorPipeline(AaaService aaaService) {
    this(aaaService, null, null);
  }

  @Override
  public ValidationResult validate(ValidationRequest request) {
    // Layer 1 — SESSION
    Optional<Session> sessionOpt = aaaService.sessionInfo(request.sessionId());
    if (sessionOpt.isEmpty()) {
      return reject(
          request.requestId(),
          "EMS-SES-1002",
          "SES",
          ValidationLayer.SESSION,
          "Session " + request.sessionId() + " not found or has expired.",
          "Talk to session admin.",
          null);
    }
    Session session = sessionOpt.get();

    // Layer 2 — IDENTITY
    // Session logon already resolved identity; no separate catalog code for identity-not-found.
    // This layer is a pass-through placeholder for future active-status checks.

    // Layer 3 — REFERENCE
    if (request.figi() != null && securityMasterService != null) {
      Optional<InstrumentVersioned> instrumentOpt =
          securityMasterService.currentSnapshot().lookup(request.figi());
      if (instrumentOpt.isEmpty()) {
        return reject(
            request.requestId(),
            "EMS-REF-2001",
            "REF",
            ValidationLayer.REFERENCE,
            "FIGI " + request.figi() + " not present in security master.",
            "Verify symbology onboarding for this instrument.",
            null);
      }
      InstrumentVersioned instrument = instrumentOpt.get();
      if (!instrument.isActive()) {
        return reject(
            request.requestId(),
            "EMS-REF-2002",
            "REF",
            ValidationLayer.REFERENCE,
            "Instrument "
                + request.figi()
                + " is not active (status: "
                + instrument.lifecycleStatus()
                + ").",
            "Verify symbology onboarding for this instrument.",
            null);
      }
    }

    // Layer 4 — PERMISSION
    if (request.tag() != null && tagPermissionEvaluator != null) {
      Identity identity = session.identity();
      AuthorizationResult authResult = tagPermissionEvaluator.authorize(identity, request.tag());
      if (authResult instanceof AuthorizationResult.Deny deny) {
        return reject(
            request.requestId(),
            deny.rejectCode(),
            "PRM",
            ValidationLayer.PERMISSION,
            deny.message(),
            "Talk to " + deny.adminHint() + ".",
            null);
      }
    }

    // Layers 5–8 — stubs (always pass until Phases 7, 9, 10)
    return new ValidationResult.Pass(request.requestId());
  }

  private static ValidationResult.Reject reject(
      String requestId,
      String code,
      String category,
      ValidationLayer layer,
      String message,
      String adminHint,
      String field) {
    return new ValidationResult.Reject(requestId, code, category, layer, message, adminHint, field);
  }
}
