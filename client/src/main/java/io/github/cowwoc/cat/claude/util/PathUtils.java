/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.util;

import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Path utilities.
 */
public final class PathUtils
{
  private PathUtils()
  {
  }

  /**
   * Resolves a relative path against a base directory and validates that the result does not escape the base
   * directory via path traversal.
   *
   * @param baseDir              the base directory (must already be absolute and normalized)
   * @param relativePath         the relative path to resolve
   * @param parameterDescription a description of the parameter for use in error messages
   * @return the resolved and normalized path
   * @throws NullPointerException     if any argument is null
   * @throws IllegalArgumentException if the resolved path escapes {@code baseDir}
   */
  public static Path normalize(Path baseDir, String relativePath,
    String parameterDescription)
  {
    requireThat(baseDir, "baseDir").isNotNull();
    requireThat(relativePath, "relativePath").isNotNull();
    requireThat(parameterDescription, "parameterDescription").isNotNull();

    Path resolved = baseDir.resolve(relativePath).toAbsolutePath().normalize();
    if (!resolved.startsWith(baseDir))
    {
      throw new IllegalArgumentException(parameterDescription + " contains path traversal: '" +
        relativePath + "'. Expected path under: " + baseDir);
    }
    return resolved;
  }
}
