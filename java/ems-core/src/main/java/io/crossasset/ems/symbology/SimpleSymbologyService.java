package io.crossasset.ems.symbology;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SimpleSymbologyService is a basic implementation of the SymbologyService. It uses in-memory maps
 * to simulate instrument identifier lookups.
 */
public class SimpleSymbologyService implements SymbologyService {

  private record InstrumentData(String figi, String name, String ticker, String exchCode) {}

  private final Map<String, InstrumentData> figiMap = new ConcurrentHashMap<>();
  private final Map<String, String> secondaryMap = new ConcurrentHashMap<>(); // ID -> FIGI

  public SimpleSymbologyService() {
    // Seed with some sample data
    seed("BBG000B9XRX4", "Apple Inc", "AAPL", "XNAS", "US0378331005", "037833100", "2046251");
    seed("BBG000B9XRY1", "Microsoft Corp", "MSFT", "XNAS", "US5949181045", "594918104", "2588173");
  }

  private void seed(
      String figi, String name, String ticker, String exch, String isin, String cusip, String sedol) {
    InstrumentData data = new InstrumentData(figi, name, ticker, exch);
    figiMap.put(figi, data);
    secondaryMap.put(isin, figi);
    secondaryMap.put(cusip, figi);
    secondaryMap.put(sedol, figi);
    secondaryMap.put(ticker + ":" + exch, figi); // ticker keyed as TICKER:MIC to avoid collisions
  }

  @Override
  public List<ResolveResult> resolve(ResolveRequest request) {
    return request.items().stream().map(this::resolveItem).collect(Collectors.toList());
  }

  private ResolveResult resolveItem(ResolveItem item) {
    String lookupValue = item.value();
    String figi = null;

    if (item.type() == IdType.ID_BB_GLOBAL) {
      figi = lookupValue;
    } else if (item.type() == IdType.ID_TICKER) {
      String micCode = item.exchCode() != null ? item.exchCode() : item.micCode();
      figi = secondaryMap.get(lookupValue + ":" + micCode);
    } else {
      figi = secondaryMap.get(lookupValue);
    }

    if (figi == null || !figiMap.containsKey(figi)) {
      return new ResolveResult(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.of("EMS-REF-1001"),
          Optional.of("Instrument not found or license denied"));
    }

    InstrumentData data = figiMap.get(figi);
    return new ResolveResult(
        Optional.of(data.figi()),
        Optional.of(data.name()),
        Optional.of(data.ticker()),
        Optional.of(data.exchCode()),
        Optional.empty(),
        Optional.empty());
  }
}
