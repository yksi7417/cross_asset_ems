/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.it.slice;

import io.crossasset.ems.core.journal.DeterministicIds;
import io.crossasset.ems.core.journal.JournalEvent;
import io.crossasset.ems.core.journal.MalformedJournalException;
import io.crossasset.ems.transport.journal.JournalTransport;
import io.crossasset.ems.transport.journal.Transport;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@code ems-slice --input <journal> --output <journal> [--seed <n>]}
 *
 * <p>A pure function from an input journal to an output journal. No network, no clock, no
 * filesystem beyond those two paths. The Rust and C++ binaries accept the same arguments and must
 * produce byte-identical output — see {@code conformance/README.md}.
 *
 * <p>Exit codes: {@code 0} success, {@code 1} the input journal could not be read, {@code 2} a
 * usage error. These are part of the contract too: the harness distinguishes "this implementation
 * rejected the input" from "this implementation was invoked wrongly".
 */
public final class SliceMain {

  private static final int EXIT_OK = 0;
  private static final int EXIT_INPUT_ERROR = 1;
  private static final int EXIT_USAGE = 2;

  private SliceMain() {}

  public static void main(String[] args) {
    System.exit(run(args, System.err));
  }

  /**
   * Testable entry point. Returns the exit code instead of calling {@link System#exit}, so the
   * tests can assert on it.
   */
  public static int run(String[] args, PrintStream err) {
    @Nullable Path input = null;
    @Nullable Path output = null;
    long seed = 0;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      switch (arg) {
        case "--input" -> {
          if (++i >= args.length) {
            return usage(err, "--input requires a path");
          }
          input = Path.of(args[i]);
        }
        case "--output" -> {
          if (++i >= args.length) {
            return usage(err, "--output requires a path");
          }
          output = Path.of(args[i]);
        }
        case "--seed" -> {
          if (++i >= args.length) {
            return usage(err, "--seed requires a number");
          }
          try {
            seed = Long.parseLong(args[i]);
          } catch (NumberFormatException e) {
            return usage(err, "--seed is not a number: " + args[i]);
          }
          if (seed < 0) {
            return usage(err, "--seed must not be negative: " + seed);
          }
        }
        case "--help", "-h" -> {
          return usage(err, null);
        }
        default -> {
          return usage(err, "unknown argument: " + arg);
        }
      }
    }

    if (input == null) {
      return usage(err, "--input is required");
    }
    if (output == null) {
      return usage(err, "--output is required");
    }

    // The slice talks to a Transport, not to files. JournalTransport is the
    // deterministic implementation the conformance gate runs; an Aeron-backed
    // one is the live path, and nothing here can tell them apart.
    // See docs/decisions/0006-abstract-transport-journal-first.md.
    try (Transport transport = new JournalTransport(input, output)) {
      List<JournalEvent> events = transport.drain();
      for (JournalEvent event : new SliceRunner(new DeterministicIds(seed)).run(events)) {
        transport.publish(event);
      }
      transport.flush();
      return EXIT_OK;
    } catch (MalformedJournalException e) {
      // No stack trace: a malformed input journal is a data problem, and a
      // stack trace here would bury the line number that actually helps.
      err.println("ems-slice: malformed input journal: " + e.getMessage());
      return EXIT_INPUT_ERROR;
    } catch (UncheckedIOException e) {
      err.println("ems-slice: " + e.getMessage());
      return EXIT_INPUT_ERROR;
    }
  }

  private static int usage(PrintStream err, @Nullable String message) {
    if (message != null) {
      err.println("ems-slice: " + message);
    }
    err.println(
        """
        usage: ems-slice --input <journal> --output <journal> [--seed <n>]

          --input   input event journal (JSONL)
          --output  output event journal (JSONL), overwritten
          --seed    identifier generator seed, default 0

        A pure function from input journal to output journal. See conformance/README.md.""");
    return EXIT_USAGE;
  }
}
