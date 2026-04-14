/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Shared glob-matching utility using JDK {@link PathMatcher}.
 * <p>
 * Caches compiled matchers for repeated use with the same glob pattern.
 * The cache is bounded to prevent unbounded memory growth from unique glob patterns.
 * Patterns are validated before compilation to prevent DoS via overly complex expressions.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class GlobMatcher
{
  private static final FileSystem FS = FileSystems.getDefault();
  private static final int MAX_CACHE_SIZE = 256;
  /**
   * Maximum allowed length for a glob pattern string.
   */
  private static final int MAX_GLOB_LENGTH = 200;
  /**
   * Maximum allowed number of {@code **} wildcard segments in a glob pattern.
   */
  private static final int MAX_WILDCARD_SEGMENTS = 3;
  private static final Map<String, PathMatcher> CACHE = Collections.synchronizedMap(
    new LinkedHashMap<>(MAX_CACHE_SIZE + 1, 0.75f, true)
    {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, PathMatcher> eldest)
      {
        return size() > MAX_CACHE_SIZE;
      }
    });

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
   * The cache is bounded to at most {@value MAX_CACHE_SIZE} entries using LRU eviction.
   * <p>
   * Patterns are validated before first use: patterns longer than {@value MAX_GLOB_LENGTH} characters
   * or containing more than {@value MAX_WILDCARD_SEGMENTS} {@code **} segments are rejected to
   * prevent expensive compilation from untrusted rule frontmatter.
   *
   * @param glob the glob pattern
   * @param path the path string to test
   * @return true if the path matches the glob
   * @throws NullPointerException if {@code glob} or {@code path} are null
   * @throws IllegalArgumentException if {@code glob} or {@code path} are blank, or if {@code glob} exceeds
   *   the maximum allowed complexity
   */
  public static boolean matches(String glob, String path)
  {
    requireThat(glob, "glob").isNotBlank();
    requireThat(path, "path").isNotBlank();
    PathMatcher matcher = CACHE.get(glob);
    if (matcher == null)
    {
      validateGlob(glob);
      matcher = FS.getPathMatcher("glob:" + glob);
      CACHE.put(glob, matcher);
    }
    return matcher.matches(FS.getPath(path));
  }

  /**
   * Validates that a glob pattern does not exceed complexity limits.
   * <p>
   * Rejects patterns that are longer than {@value MAX_GLOB_LENGTH} characters or that contain
   * more than {@value MAX_WILDCARD_SEGMENTS} {@code **} wildcard segments. This prevents DoS
   * via expensive PathMatcher compilation from untrusted rule frontmatter.
   *
   * @param glob the glob pattern to validate
   * @throws IllegalArgumentException if the pattern exceeds any complexity limit
   */
  private static void validateGlob(String glob)
  {
    if (glob.length() > MAX_GLOB_LENGTH)
    {
      throw new IllegalArgumentException("Glob pattern exceeds maximum length of " + MAX_GLOB_LENGTH +
        " characters: length=" + glob.length());
    }
    int wildcardSegments = 0;
    int index = glob.indexOf("**");
    while (index >= 0)
    {
      ++wildcardSegments;
      index = glob.indexOf("**", index + 2);
    }
    if (wildcardSegments > MAX_WILDCARD_SEGMENTS)
    {
      throw new IllegalArgumentException("Glob pattern exceeds maximum of " + MAX_WILDCARD_SEGMENTS +
        " '**' segments: " + wildcardSegments + " found in: " + glob);
    }
  }
}
