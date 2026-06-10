/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.fix;

/**
 * Encodes an outbound {@code 35=j BusinessMessageReject}. Used when an inbound message cannot be
 * turned into a successful API operation: a malformed NewOrderSingle, a validator rejection, or a
 * message type that is not permitted on a staging-only FIX session (cancel/replace).
 *
 * <p>{@code BusinessRejectReason(380)} carries the FIX reason code; the EMS reject code and a
 * human-readable hint travel in {@code Text(58)} so the client sees both.
 */
public final class BusinessMessageRejectEncoder {

  private final String senderCompId;
  private final String targetCompId;

  public BusinessMessageRejectEncoder(String senderCompId, String targetCompId) {
    this.senderCompId = senderCompId;
    this.targetCompId = targetCompId;
  }

  /**
   * @param seqNum outbound sequence number.
   * @param refMsgType the {@code MsgType(35)} of the offending inbound message (tag 372).
   * @param businessRejectReason FIX {@code BusinessRejectReason(380)} code.
   * @param emsRejectCode the EMS {@code EMS-<CAT>-<NNNN>} reject code.
   * @param text human-readable detail.
   */
  public String encode(
      long seqNum, String refMsgType, int businessRejectReason, String emsRejectCode, String text) {
    String detail = text + " [" + emsRejectCode + "]";
    return FixMessage.builder()
        .field(FixTags.MSG_TYPE, "j")
        .field(FixTags.SENDER_COMP_ID, senderCompId)
        .field(FixTags.TARGET_COMP_ID, targetCompId)
        .field(FixTags.MSG_SEQ_NUM, seqNum)
        .field(FixTags.REF_MSG_TYPE, refMsgType)
        .field(FixTags.BUSINESS_REJECT_REASON, businessRejectReason)
        .field(FixTags.TEXT, detail)
        .build();
  }
}
