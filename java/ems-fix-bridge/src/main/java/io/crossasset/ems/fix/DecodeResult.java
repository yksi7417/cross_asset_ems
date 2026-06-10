/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

import io.crossasset.ems.oms.OrderRequest;

/**
 * Outcome of decoding an inbound FIX message into an API operation. Sealed so callers handle both
 * the success and the malformed-message paths without exceptions escaping the gateway.
 */
public sealed interface DecodeResult permits DecodeResult.Ok, DecodeResult.Missing {

  /** A well-formed message decoded into an {@link OrderRequest}. */
  record Ok(OrderRequest request) implements DecodeResult {}

  /** A required field was absent; {@code missingTag} is the FIX tag number that was expected. */
  record Missing(int missingTag) implements DecodeResult {}
}
