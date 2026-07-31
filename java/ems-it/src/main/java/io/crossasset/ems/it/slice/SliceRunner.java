/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it.slice;

import io.crossasset.ems.core.journal.DeterministicIds;
import io.crossasset.ems.core.journal.JournalEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The slice, as far as it has been built.
 *
 * <p><strong>Today it covers component 1 only</strong>: the journal codec and deterministic
 * identifiers. An {@code OrderNew} becomes an {@code OrderAccepted} carrying a generated order id;
 * everything else passes through with its sequence renumbered; a {@code RunSummary} closes the
 * journal. There is no validation, no FSM, no routing and no venue — those are later components,
 * and pretending otherwise in the output would make the conformance corpus lie about what is
 * implemented.
 *
 * <p>The transformation is deliberately trivial. Its job at this stage is to exercise parse →
 * ordered map → identifier generation → encode across three languages and prove the bytes agree.
 * The logic grows; the byte-exactness requirement does not change.
 *
 * <p>Kept in lockstep with {@code rust/ems-slice/src/runner.rs} and {@code
 * cpp/ems-it/src/slice_runner.cpp}.
 */
public final class SliceRunner {

  /** Input event that opens an order. */
  private static final String TYPE_ORDER_NEW = "OrderNew";

  /** Output event acknowledging one. */
  private static final String TYPE_ORDER_ACCEPTED = "OrderAccepted";

  /** Final output event: makes the seed and the input size visible in the journal itself. */
  private static final String TYPE_RUN_SUMMARY = "RunSummary";

  /**
   * Fields copied from {@code OrderNew} onto {@code OrderAccepted}, in this order.
   *
   * <p>An explicit list rather than "copy everything": an unknown field silently reaching the
   * output would be a divergence that only shows up once some other language's map happens to order
   * it differently.
   */
  private static final List<String> ECHOED_FIELDS =
      List.of("account", "figi", "price", "qty", "side");

  private final DeterministicIds ids;

  public SliceRunner(DeterministicIds ids) {
    this.ids = ids;
  }

  /** Runs the slice over {@code input}, returning the output journal. */
  public List<JournalEvent> run(List<JournalEvent> input) {
    List<JournalEvent> output = new ArrayList<>(input.size() + 1);
    long seq = 0;

    for (JournalEvent event : input) {
      if (TYPE_ORDER_NEW.equals(event.type())) {
        TreeMap<String, String> fields = new TreeMap<>();
        fields.put("orderId", ids.nextOrderId());
        for (String key : ECHOED_FIELDS) {
          String value = event.fields().get(key);
          if (value != null) {
            fields.put(key, value);
          }
        }
        output.add(new JournalEvent(++seq, TYPE_ORDER_ACCEPTED, fields));
      } else {
        output.add(event.withSeq(++seq));
      }
    }

    TreeMap<String, String> summary = new TreeMap<>();
    summary.put("events", Integer.toString(input.size()));
    summary.put("seed", Long.toString(ids.seed()));
    output.add(new JournalEvent(++seq, TYPE_RUN_SUMMARY, summary));

    return output;
  }
}
