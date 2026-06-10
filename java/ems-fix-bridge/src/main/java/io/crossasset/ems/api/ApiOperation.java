/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.api;

/**
 * The closed operation set of the API surface (task 8.4). Every action — from a trader's UI, a
 * native SDK, a FIX bridge, or an automation rule — is one of these, batched. Per arch-api-first.md
 * § Operation categories; automation/reference-data/admin categories join as their layers land.
 */
public enum ApiOperation {
  STAGE_ORDERS,
  AMEND_ORDERS,
  CANCEL_ORDERS,
  MARK_READY,
  ROUTE_ORDERS,
  CANCEL_ROUTES,
  SUBSCRIBE,
  UNSUBSCRIBE
}
