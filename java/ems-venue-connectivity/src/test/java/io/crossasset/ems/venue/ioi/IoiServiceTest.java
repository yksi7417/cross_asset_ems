/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.venue.ioi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 9.4: qualifier preservation + strength ordering, hard client-segment visibility, the source-keyed
 * lifecycle (networks reference THEIR ids), expiry sweep, traded-against linkage.
 */
class IoiServiceTest {

  private final List<IoiService.Ioi> events = new ArrayList<>();
  private final IoiService service = new IoiService(events::add);

  private IoiService.Ioi natural() {
    return service.onNew(
        "AUTEX",
        "AX-1",
        "GS",
        "BBG000B9XRY4",
        2,
        500_000,
        true,
        IoiService.Qualifier.NATURAL,
        0,
        60_000,
        Set.of());
  }

  @Test
  void qualifiersRankByStrength_naturalBeatsInTouchWith() {
    service.onNew(
        "BBG_IOI",
        "B-1",
        "MS",
        "BBG000B9XRY4",
        2,
        100_000,
        false,
        IoiService.Qualifier.IN_TOUCH_WITH,
        0,
        60_000,
        Set.of());
    natural();

    List<IoiService.Ioi> visible = service.visibleTo("tier-1", "BBG000B9XRY4");
    assertThat(visible).hasSize(2);
    // NATURAL (dealer owns the position) outranks IN_TOUCH_WITH (dealer is talking to someone).
    assertThat(visible.get(0).qualifier()).isEqualTo(IoiService.Qualifier.NATURAL);
    assertThat(visible.get(0).dealer()).isEqualTo("GS");
  }

  @Test
  void clientSegments_gateHard_invisibleNotGreyed() {
    service.onNew(
        "LIQUIDNET",
        "L-1",
        "LQ",
        "BBG000B9XRY4",
        1,
        1_000_000,
        true,
        IoiService.Qualifier.SUPER_NATURAL,
        0,
        60_000,
        Set.of("tier-1"));

    assertThat(service.visibleTo("tier-1", "BBG000B9XRY4")).hasSize(1);
    assertThat(service.visibleTo("tier-3", "BBG000B9XRY4")).isEmpty(); // not greyed: GONE
  }

  @Test
  void lifecycle_isSourceKeyed_cancelReplaceExpire() {
    natural(); // AUTEX|AX-1
    // The network cancels by ITS id — we resolve to ours.
    assertThat(service.onCancel("AUTEX", "AX-1").orElseThrow().state())
        .isEqualTo(IoiService.State.CANCELLED);
    assertThat(service.onCancel("AUTEX", "AX-1")).isEmpty(); // not ACTIVE anymore
    assertThat(service.onCancel("AUTEX", "AX-UNKNOWN")).isEmpty();

    IoiService.Ioi second =
        service.onNew(
            "AUTEX",
            "AX-2",
            "GS",
            "BBG000B9XRY4",
            2,
            250_000,
            true,
            IoiService.Qualifier.UNWOUND,
            0,
            5_000,
            Set.of());
    service.sweep(5_000); // validity passed
    assertThat(service.find(second.ioiId()).orElseThrow().state())
        .isEqualTo(IoiService.State.EXPIRED);
    assertThat(service.visibleTo("any", "BBG000B9XRY4")).isEmpty();
  }

  @Test
  void tradedAgainst_linksTheIoiToOrderFlow() {
    IoiService.Ioi ioi = natural();
    assertThat(service.tradedAgainst(ioi.ioiId()).orElseThrow().state())
        .isEqualTo(IoiService.State.TRADED_AGAINST);
    assertThat(service.tradedAgainst(ioi.ioiId())).isEmpty(); // once

    // Every transition was published to the event stream in order.
    assertThat(events)
        .extracting(IoiService.Ioi::state)
        .containsExactly(IoiService.State.ACTIVE, IoiService.State.TRADED_AGAINST);
  }
}
