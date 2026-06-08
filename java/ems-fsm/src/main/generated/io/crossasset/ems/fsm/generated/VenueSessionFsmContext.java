// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/venuesession.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py

package io.crossasset.ems.fsm.generated;

import org.jspecify.annotations.Nullable;

/** Mutable context carried by each {@link VenueSessionFsmRunner} instance. */
public final class VenueSessionFsmContext {

  private String sessionId;
  private long nextExpectedSeqIn;
  private long nextSendSeqOut;
  private long heartbeatIntervalSecs;
  private boolean testRequestOutstanding;
  private long resendWindowLow;
  private long resendWindowHigh;
  private String venueMic;

  public VenueSessionFsmContext(
      String sessionId,
      long nextExpectedSeqIn,
      long nextSendSeqOut,
      long heartbeatIntervalSecs,
      boolean testRequestOutstanding,
      long resendWindowLow,
      long resendWindowHigh,
      String venueMic
  ) {
    this.sessionId = sessionId;
    this.nextExpectedSeqIn = nextExpectedSeqIn;
    this.nextSendSeqOut = nextSendSeqOut;
    this.heartbeatIntervalSecs = heartbeatIntervalSecs;
    this.testRequestOutstanding = testRequestOutstanding;
    this.resendWindowLow = resendWindowLow;
    this.resendWindowHigh = resendWindowHigh;
    this.venueMic = venueMic;
  }

  public String sessionId() { return sessionId; }
  public long nextExpectedSeqIn() { return nextExpectedSeqIn; }
  public long nextSendSeqOut() { return nextSendSeqOut; }
  public long heartbeatIntervalSecs() { return heartbeatIntervalSecs; }
  public boolean testRequestOutstanding() { return testRequestOutstanding; }
  public long resendWindowLow() { return resendWindowLow; }
  public long resendWindowHigh() { return resendWindowHigh; }
  public String venueMic() { return venueMic; }

  /** Return a copy with the given field updated. */
  public VenueSessionFsmContext with(
      String sessionId, long nextExpectedSeqIn, long nextSendSeqOut, long heartbeatIntervalSecs, boolean testRequestOutstanding, long resendWindowLow, long resendWindowHigh, String venueMic
  ) {
    return new VenueSessionFsmContext(sessionId, nextExpectedSeqIn, nextSendSeqOut, heartbeatIntervalSecs, testRequestOutstanding, resendWindowLow, resendWindowHigh, venueMic);
  }
}
