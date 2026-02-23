/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared glob-matching utility using JDK {@link PathMatcher}.
 * <p>
 * Caches compiled matchers for repeated use with the same glob pattern.
 */
public final class GlobMatcher
{
  private static final FileSystem FS = FileSystems.getDefault();
  private static final Map<String, PathMatcher> CACHE = new ConcurrentHashMap<>();

  private GlobMatcher()
  {
  }

  /**
   * Returns true if the given path string matches the glob pattern.
   * <p>
   * Supports {@code *} (matches any characters except path separator),
   * {@code **} (matches any characters including path separator), and
   * {@code ?} (matches a single non-separator character).
   * <p>
   * Compiled matchers are cached by glob string to avoid repeated compilation.
   *
   * @param glob the glob pattern
   * @param path the path string to test
   * @return true if the path matches the glob
   * @throws NullPointerException if {@code glob} or {@code path} is null
   */
  public static boolean matches(String glob, String path)
  {
    PathMatcher matcher = CACHE.computeIfAbsent(glob,
      g -> FS.getPathMatcher("glob:" + g));
    return matcher.matches(FS.getPath(path));
  }
}
