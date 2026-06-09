/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.symbology;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decorator that enforces per-firm license checks for secondary identifiers (ISIN, CUSIP, SEDOL),
 * records audit entries for all licensed-type access attempts, and meters successful accesses.
 *
 * <h3>Access control</h3>
 *
 * <ul>
 *   <li>FIGI ({@code ID_BB_GLOBAL}) and ticker ({@code ID_TICKER}): no license required; passed
 *       through to the delegate without audit or billing.
 *   <li>ISIN, CUSIP, SEDOL: require a per-firm license from {@link LicenseRegistry}; unlicensed
 *       access returns {@code EMS-REF-1001 license_denied}.
 * </ul>
 *
 * <h3>Audit discipline</h3>
 *
 * Both granted and denied attempts against licensed identifier types are recorded in the {@link
 * AccessAuditLog}. Denied entries capture licensing probe behaviour. Only successful accesses are
 * recorded in the {@link BillingMeter} (denied attempts must not be invoiced).
 *
 * <p>MeteredIdentifierAccess event emission to the event-log bus is deferred to the event-log
 * integration phase; this implementation uses in-process audit + meter only.
 *
 * <p>Task 4.2 — license-metering and audit.
 */
public class MeteredSymbologyService implements SymbologyService {

  private static final Set<IdType> LICENSED_TYPES =
      Set.of(IdType.ID_ISIN, IdType.ID_CUSIP, IdType.ID_SEDOL);

  private final SymbologyService delegate;
  private final LicenseRegistry licenseRegistry;
  private final AccessAuditLog auditLog;
  private final BillingMeter billingMeter;

  public MeteredSymbologyService(
      SymbologyService delegate,
      LicenseRegistry licenseRegistry,
      AccessAuditLog auditLog,
      BillingMeter billingMeter) {
    this.delegate = delegate;
    this.licenseRegistry = licenseRegistry;
    this.auditLog = auditLog;
    this.billingMeter = billingMeter;
  }

  @Override
  public List<ResolveResult> resolve(ResolveRequest request) {
    List<ResolveResult> delegateResults = delegate.resolve(request);
    List<ResolveResult> results = new ArrayList<>(request.items().size());

    for (int i = 0; i < request.items().size(); i++) {
      ResolveItem item = request.items().get(i);

      if (!LICENSED_TYPES.contains(item.type())) {
        results.add(delegateResults.get(i));
        continue;
      }

      if (!licenseRegistry.isLicensed(request.identity(), item.type())) {
        auditLog.record(
            new AccessRecord(
                request.requestId(),
                request.identity(),
                item.type(),
                item.value(),
                AccessOutcome.DENIED));
        results.add(
            new ResolveResult(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of("EMS-REF-1001"),
                Optional.of("License denied")));
        continue;
      }

      ResolveResult delegateResult = delegateResults.get(i);
      boolean found = delegateResult.errorCode().isEmpty();
      AccessOutcome outcome = found ? AccessOutcome.GRANTED : AccessOutcome.DENIED;
      auditLog.record(
          new AccessRecord(
              request.requestId(), request.identity(), item.type(), item.value(), outcome));
      if (found) {
        billingMeter.recordSuccess(request.identity(), item.type());
      }
      results.add(delegateResult);
    }

    return results;
  }
}
