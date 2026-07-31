/**
 * The {@code ems-slice} binary: a pure function from an input event journal to an output event
 * journal.
 *
 * <p>No network, no clock, no filesystem beyond the two paths it is given. That is what makes the
 * conformance gate possible — see {@code conformance/README.md}.
 *
 * <p>Rust (<code>rust/ems-slice</code>) and C++ (<code>cpp/ems-it</code>) ship a binary with the
 * same name, the same CLI and byte-identical output. The behaviour here grows one component at a
 * time; today it covers the journal codec and deterministic identifiers only.
 */
@NullMarked
package io.crossasset.ems.it.slice;

import org.jspecify.annotations.NullMarked;
