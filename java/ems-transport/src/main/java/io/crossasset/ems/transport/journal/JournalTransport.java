/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.transport.journal;

import io.crossasset.ems.core.journal.JournalCodec;
import io.crossasset.ems.core.journal.JournalEvent;
import io.crossasset.ems.core.journal.MalformedJournalException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link Transport} backed by two journal files.
 *
 * <p>Deterministic by construction: no media driver, no network, no clock. This is what makes
 * byte-identical replay across three languages testable at all.
 *
 * <p>Published events are buffered and written on {@link #flush()}. Streaming them as they arrive
 * would be cheaper, and would mean a run that died halfway left a file that is indistinguishable
 * from a correct short one — which the conformance differ would report as a byte mismatch on a
 * later line, sending the reader looking for a logic bug that is not there.
 */
public final class JournalTransport implements Transport {

  private final Path input;
  private final Path output;
  private final List<JournalEvent> pending = new ArrayList<>();

  private boolean drained;
  private boolean closed;

  public JournalTransport(Path input, Path output) {
    this.input = input;
    this.output = output;
  }

  /**
   * @throws MalformedJournalException if the input journal does not parse
   * @throws UncheckedIOException if it cannot be read
   */
  @Override
  public List<JournalEvent> drain() {
    if (drained) {
      return List.of();
    }
    drained = true;
    try {
      return Collections.unmodifiableList(JournalCodec.read(input));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + input, e);
    }
  }

  @Override
  public void publish(JournalEvent event) {
    if (closed) {
      throw new IllegalStateException("publish after close");
    }
    pending.add(event);
  }

  @Override
  public void flush() {
    try {
      JournalCodec.write(output, pending);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot write " + output, e);
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    flush();
    closed = true;
  }
}
