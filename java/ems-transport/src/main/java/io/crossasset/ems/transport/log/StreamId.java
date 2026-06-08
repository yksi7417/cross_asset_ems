package io.crossasset.ems.transport.log;

/**
 * StreamId identifies a unique sequence of events for a specific domain entity. Examples:
 * "order.UUID", "route.UUID", "session.ID", "admin".
 */
public record StreamId(String value) {
  public StreamId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Stream ID cannot be null or blank");
    }
  }

  public static StreamId of(String type, String id) {
    return new StreamId(type + "." + id);
  }

  public static final StreamId ADMIN = new StreamId("admin");

  @Override
  public String toString() {
    return value;
  }
}
