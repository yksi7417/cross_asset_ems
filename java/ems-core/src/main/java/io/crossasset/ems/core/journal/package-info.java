/**
 * The event journal: the wire format the polyglot conformance gate compares byte-for-byte.
 *
 * <p>This package is the Java side of a three-language contract. {@link
 * io.crossasset.ems.core.journal.JournalCodec} defines a byte layout that the Rust (<code>
 * rust/ems-core</code>) and C++ (<code>cpp/ems-core</code>) ports must reproduce exactly — see
 * <code>conformance/README.md</code>. Changing the layout here changes it for all three.
 *
 * <p>It is {@link org.jspecify.annotations.NullMarked}: every type use is non-null unless
 * explicitly annotated {@code @Nullable}, and NullAway enforces that at compile time. The ports
 * translate that declared contract into {@code Option<T>} and {@code std::optional<T>} — those are
 * not guesses about which Java references could be null.
 */
@NullMarked
package io.crossasset.ems.core.journal;

import org.jspecify.annotations.NullMarked;
