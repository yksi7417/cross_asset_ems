/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

/**
 * FIX 4.4 tag-number constants used by the client-facing gateway. Only the subset the MVP gateway
 * reads or writes is declared here; the gateway is staging-only on the inbound side
 * (NewOrderSingle) and emits ExecutionReport / BusinessMessageReject / session messages outbound.
 */
public final class FixTags {
  private FixTags() {}

  // ── Standard header / trailer ──────────────────────────────────────────────
  public static final int BEGIN_STRING = 8;
  public static final int BODY_LENGTH = 9;
  public static final int CHECKSUM = 10;
  public static final int MSG_TYPE = 35;
  public static final int MSG_SEQ_NUM = 34;
  public static final int SENDER_COMP_ID = 49;
  public static final int TARGET_COMP_ID = 56;
  public static final int SENDING_TIME = 52;

  // ── NewOrderSingle (inbound) ───────────────────────────────────────────────
  public static final int ACCOUNT = 1;
  public static final int CL_ORD_ID = 11;
  public static final int SECURITY_ID = 48; // carries the FIGI on this surface
  public static final int SIDE = 54;
  public static final int ORDER_QTY = 38;
  public static final int PRICE = 44;
  public static final int TIME_IN_FORCE = 59;

  // ── ExecutionReport (outbound) ─────────────────────────────────────────────
  public static final int ORDER_ID = 37;
  public static final int EXEC_ID = 17;
  public static final int ORD_STATUS = 39;
  public static final int EXEC_TYPE = 150;
  public static final int LEAVES_QTY = 151;
  public static final int CUM_QTY = 14;

  // ── Reject / session ───────────────────────────────────────────────────────
  public static final int TEXT = 58;
  public static final int REF_SEQ_NUM = 45;
  public static final int REF_MSG_TYPE = 372;
  public static final int BUSINESS_REJECT_REASON = 380;
  public static final int TEST_REQ_ID = 112;

  // ── Venue-facing gateway (8.2) ─────────────────────────────────────────────
  public static final int LAST_PX = 31;
  public static final int LAST_QTY = 32;
  public static final int ORD_TYPE = 40;
  public static final int ORIG_CL_ORD_ID = 41;
  public static final int ENCRYPT_METHOD = 98;
  public static final int CXL_REJ_REASON = 102;
  public static final int HEART_BT_INT = 108;
  public static final int CXL_REJ_RESPONSE_TO = 434;
}
