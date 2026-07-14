/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.posttrade.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class NetMoneyTest {

  @Test
  void constructionWithAllFields() {
    NetMoney nm = new NetMoney(1000, 50, 10, 25, 985);
    assertEquals(1000, nm.gross());
    assertEquals(50, nm.commission());
    assertEquals(10, nm.fees());
    assertEquals(25, nm.accruedInterest());
    assertEquals(985, nm.netMoney());
  }

  @Test
  void allZeros() {
    NetMoney nm = new NetMoney(0, 0, 0, 0, 0);
    assertEquals(0, nm.gross());
    assertEquals(0, nm.commission());
    assertEquals(0, nm.fees());
    assertEquals(0, nm.accruedInterest());
    assertEquals(0, nm.netMoney());
  }

  @Test
  void negativeNetMoneyForBuy() {
    // Buy: client pays, so netMoney is positive per sign convention
    NetMoney nm = new NetMoney(1000, 50, 10, 25, 985);
    assertEquals(985, nm.netMoney());
  }

  @Test
  void positiveNetMoneyForSell() {
    // Sell: client receives, so netMoney is positive per sign convention
    NetMoney nm = new NetMoney(1000, 50, 10, 25, 985);
    assertEquals(985, nm.netMoney());
  }

  @Test
  void equalityAndHashCode() {
    NetMoney nm1 = new NetMoney(1000, 50, 10, 25, 985);
    NetMoney nm2 = new NetMoney(1000, 50, 10, 25, 985);
    assertEquals(nm1, nm2);
    assertEquals(nm1.hashCode(), nm2.hashCode());
  }

  @Test
  void inequalityAndHashCode() {
    NetMoney nm1 = new NetMoney(1000, 50, 10, 25, 985);
    NetMoney nm2 = new NetMoney(1000, 50, 10, 25, 986);
    assertNotEquals(nm1, nm2);
  }

  @Test
  void differentGross() {
    NetMoney nm1 = new NetMoney(1000, 50, 10, 25, 985);
    NetMoney nm2 = new NetMoney(2000, 50, 10, 25, 1985);
    assertNotEquals(nm1, nm2);
  }

  @Test
  void differentCommission() {
    NetMoney nm1 = new NetMoney(1000, 50, 10, 25, 985);
    NetMoney nm2 = new NetMoney(1000, 60, 10, 25, 975);
    assertNotEquals(nm1, nm2);
  }

  @Test
  void differentFees() {
    NetMoney nm1 = new NetMoney(1000, 50, 10, 25, 985);
    NetMoney nm2 = new NetMoney(1000, 50, 20, 25, 975);
    assertNotEquals(nm1, nm2);
  }

  @Test
  void differentAccruedInterest() {
    NetMoney nm1 = new NetMoney(1000, 50, 10, 25, 985);
    NetMoney nm2 = new NetMoney(1000, 50, 10, 30, 980);
    assertNotEquals(nm1, nm2);
  }
}
