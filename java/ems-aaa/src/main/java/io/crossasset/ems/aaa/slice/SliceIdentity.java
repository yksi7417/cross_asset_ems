/*
 * Copyright (c) 2026 Cross-Asset EMS Contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package io.crossasset.ems.aaa.slice;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Who is acting, and what they are entitled to.
 *
 * <p>{@code tags} is a {@link SortedSet} for the same reason journal fields are a {@code
 * SortedMap}: its iteration order can reach the output journal, and the conformance gate compares
 * that journal byte-for-byte across three languages.
 *
 * @param firm firm identifier
 * @param desk desk identifier
 * @param user user identifier
 * @param tags granted permission tags, iterating in lexicographic order
 */
public record SliceIdentity(String firm, String desk, String user, SortedSet<String> tags) {

  public SliceIdentity {
    Objects.requireNonNull(firm, "firm");
    Objects.requireNonNull(desk, "desk");
    Objects.requireNonNull(user, "user");
    tags = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(tags, "tags")));
  }

  /** True when this identity holds {@code tag}. */
  public boolean holds(String tag) {
    return tags.contains(tag);
  }
}
