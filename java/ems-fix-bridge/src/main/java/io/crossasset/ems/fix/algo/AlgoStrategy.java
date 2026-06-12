/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix.algo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One broker algo definition (task 11.16) — the metadata an algo ticket renders and the order
 * path validates against. The shape mirrors FIXatdl's {@code <Strategy>}: a wire identifier
 * (TargetStrategy 847 value), provider, and typed parameters.
 *
 * @param broker the providing broker (catalog scope)
 * @param wireValue the value sent as {@code TargetStrategy(847)}
 * @param name human label ("VWAP", "TWAP", "POV"…)
 * @param parameters the algo's parameter panel, in definition order
 */
public record AlgoStrategy(
    String broker, String wireValue, String name, List<Parameter> parameters) {

  /**
   * One algo parameter, FIXatdl-shaped.
   *
   * @param type FIXatdl-ish value type driving validation + StrategyParameterType(959)
   * @param minValue inclusive lower bound for numerics (null = unbounded)
   * @param maxValue inclusive upper bound for numerics (null = unbounded)
   * @param enumValues allowed values for CHAR/STRING enums (empty = free)
   * @param defaultValue ticket prefill (null = none)
   */
  public record Parameter(
      String name,
      ValueType type,
      boolean required,
      Long minValue,
      Long maxValue,
      List<String> enumValues,
      String defaultValue) {

    public Parameter {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(type, "type");
      enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }
  }

  /** The FIXatdl value types this catalog supports, with their tag-959 wire codes. */
  public enum ValueType {
    INT(1),
    QTY(7),
    PRICE(8),
    CHAR(11),
    STRING(14),
    UTC_TIME(16),
    PERCENTAGE(22),
    BOOLEAN(13);

    public final int fixCode;

    ValueType(int fixCode) {
      this.fixCode = fixCode;
    }
  }

  public AlgoStrategy {
    Objects.requireNonNull(broker, "broker");
    Objects.requireNonNull(wireValue, "wireValue");
    Objects.requireNonNull(name, "name");
    parameters = List.copyOf(parameters);
  }

  /**
   * Validate a ticket's parameter values against this strategy. Returns one human-readable error
   * per violation; empty = routable. Unknown parameter names are violations too — a broker algo
   * silently dropping a parameter it never defined is how "limit at 50" becomes "market".
   */
  public List<String> validate(Map<String, String> values) {
    List<String> errors = new ArrayList<>();
    for (Parameter parameter : parameters) {
      String value = values.get(parameter.name());
      if (value == null || value.isBlank()) {
        if (parameter.required()) {
          errors.add(parameter.name() + ": required");
        }
        continue;
      }
      switch (parameter.type()) {
        case INT, QTY, PRICE, PERCENTAGE -> {
          long parsed;
          try {
            parsed = Long.parseLong(value);
          } catch (NumberFormatException e) {
            errors.add(parameter.name() + ": not a number: " + value);
            continue;
          }
          if (parameter.minValue() != null && parsed < parameter.minValue()) {
            errors.add(parameter.name() + ": " + parsed + " < min " + parameter.minValue());
          }
          if (parameter.maxValue() != null && parsed > parameter.maxValue()) {
            errors.add(parameter.name() + ": " + parsed + " > max " + parameter.maxValue());
          }
        }
        case BOOLEAN -> {
          if (!value.equals("Y") && !value.equals("N")) {
            errors.add(parameter.name() + ": boolean must be Y or N, got " + value);
          }
        }
        case CHAR, STRING, UTC_TIME -> {
          if (!parameter.enumValues().isEmpty() && !parameter.enumValues().contains(value)) {
            errors.add(
                parameter.name() + ": " + value + " not in " + parameter.enumValues());
          }
        }
      }
    }
    for (String supplied : values.keySet()) {
      if (parameters.stream().noneMatch(p -> p.name().equals(supplied))) {
        errors.add(supplied + ": unknown parameter for strategy " + name);
      }
    }
    return errors;
  }
}
