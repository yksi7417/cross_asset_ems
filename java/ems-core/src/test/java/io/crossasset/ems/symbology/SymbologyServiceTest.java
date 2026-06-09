package io.crossasset.ems.symbology;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SymbologyServiceTest {

  @Test
  void testResolveFigi() {
    SymbologyService service = new SimpleSymbologyService();

    SymbologyService.ResolveRequest request =
        new SymbologyService.ResolveRequest(
            UUID.randomUUID(),
            1L,
            "test-user",
            List.of(
                new SymbologyService.ResolveItem(
                    SymbologyService.IdType.ID_BB_GLOBAL, "BBG000B9XRX4", null, null, null)));

    List<SymbologyService.ResolveResult> results = service.resolve(request);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).figi()).contains("BBG000B9XRX4");
    assertThat(results.get(0).name()).contains("Apple Inc");
    assertThat(results.get(0).ticker()).contains("AAPL");
  }

  @Test
  void testResolveUnknown() {
    SymbologyService service = new SimpleSymbologyService();

    SymbologyService.ResolveRequest request =
        new SymbologyService.ResolveRequest(
            UUID.randomUUID(),
            1L,
            "test-user",
            List.of(
                new SymbologyService.ResolveItem(
                    SymbologyService.IdType.ID_ISIN, "UNKNOWN-ISIN", null, null, null)));

    List<SymbologyService.ResolveResult> results = service.resolve(request);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).figi()).isEmpty();
    assertThat(results.get(0).errorCode()).contains("EMS-REF-1001");
  }
}
