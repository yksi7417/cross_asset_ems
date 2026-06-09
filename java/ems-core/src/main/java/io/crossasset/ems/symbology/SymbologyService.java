package io.crossasset.ems.symbology;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SymbologyService handles the resolution of financial instrument identifiers.
 *
 * <p>The canonical identifier is the FIGI. Other identifiers (ISIN, CUSIP, SEDOL) are treated as
 * licensed secondary identifiers.
 */
public interface SymbologyService {

  /** Represents a request to resolve one or more identifiers. */
  record ResolveRequest(UUID requestId, long clientSeq, String identity, List<ResolveItem> items) {}

  record ResolveItem(IdType type, String value, String exchCode, String micCode, String currency) {}

  public enum IdType {
    ID_BB_GLOBAL, // FIGI
    ID_ISIN,
    ID_CUSIP,
    ID_SEDOL,
    ID_TICKER
  }

  /** Resolution result for a single item. */
  record ResolveResult(
      Optional<String> figi,
      Optional<String> name,
      Optional<String> ticker,
      Optional<String> exchCode,
      Optional<String> errorCode,
      Optional<String> errorMessage) {}

  /**
   * Resolves multiple identifiers to their canonical FIGI and instrument details.
   *
   * @param request The resolution request.
   * @return A list of results corresponding to the requested items.
   */
  List<ResolveResult> resolve(ResolveRequest request);
}
