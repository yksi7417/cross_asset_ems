package io.crossasset.ems.transport.log;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StreamIdValidationTest {

  @Test
  void testOfFactoryMethod() {
    assertEquals("order.uuid-123", StreamId.of("order", "uuid-123").value());
  }

  @Test
  void testToString() {
    assertEquals("route.r1", StreamId.of("route", "r1").toString());
  }

  @Test
  void testAdminConstant() {
    assertEquals("admin", StreamId.ADMIN.value());
  }

  @Test
  void testNullValueThrows() {
    assertThrows(IllegalArgumentException.class, () -> new StreamId(null));
  }

  @Test
  void testBlankValueThrows() {
    assertThrows(IllegalArgumentException.class, () -> new StreamId("  "));
  }
}
