/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.allocation;

/**
 * How a fill's quantity is split across the template's accounts. Per arch-allocation-service.md.
 *
 * <p>The MVP implements {@link #PRO_RATA} (split by share weight). {@link #AVG_PRICE} affects the
 * price stamped on each allocation rather than the quantity split; at single-fill granularity every
 * allocation carries the fill price, so it behaves like {@code PRO_RATA} for the split. {@link
 * #SEQUENCED} and {@link #CUSTOM} are reserved for post-MVP.
 */
public enum AllocationPolicy {
  PRO_RATA,
  AVG_PRICE,
  SEQUENCED,
  CUSTOM
}
