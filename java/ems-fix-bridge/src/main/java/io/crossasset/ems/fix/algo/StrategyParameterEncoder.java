/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.algo;

import io.crossasset.ems.fix.FixMessage;
import java.util.Map;

/**
 * Encodes a validated algo selection onto an outbound order (task 11.16): {@code
 * TargetStrategy(847)} plus the {@code NoStrategyParameters(957)} repeating group — {@code
 * StrategyParameterName(958)} / {@code StrategyParameterType(959)} / {@code
 * StrategyParameterValue(960)} per entry, in the strategy's parameter-definition order
 * (deterministic wire bytes; brokers reject shuffled groups).
 *
 * <p>Callers validate first ({@link AlgoStrategy#validate}) — this encoder throws on violations
 * rather than ship a malformed algo order.
 */
public final class StrategyParameterEncoder {

  private StrategyParameterEncoder() {}

  /** Append 847 + the 957 group to {@code builder}; only supplied parameters are encoded. */
  public static FixMessage.Builder encode(
      FixMessage.Builder builder, AlgoStrategy strategy, Map<String, String> values) {
    var errors = strategy.validate(values);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException("algo parameters invalid: " + String.join("; ", errors));
    }
    builder.field(847, strategy.wireValue());
    long supplied =
        strategy.parameters().stream().filter(p -> values.get(p.name()) != null).count();
    if (supplied == 0) {
      return builder;
    }
    builder.field(957, supplied);
    for (AlgoStrategy.Parameter parameter : strategy.parameters()) {
      String value = values.get(parameter.name());
      if (value != null) {
        builder.repeatedField(958, parameter.name());
        builder.repeatedField(959, String.valueOf(parameter.type().fixCode));
        builder.repeatedField(960, value);
      }
    }
    return builder;
  }
}
