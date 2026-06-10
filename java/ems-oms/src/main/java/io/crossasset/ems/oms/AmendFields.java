/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.oms;

import org.jspecify.annotations.Nullable;

/** Fields that can be changed on a pre-route amend. Null means keep the current value. */
public record AmendFields(@Nullable Long qty, @Nullable Long price) {
  public boolean isEmpty() {
    return qty == null && price == null;
  }
}
