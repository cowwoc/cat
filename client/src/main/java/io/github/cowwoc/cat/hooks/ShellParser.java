/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared shell parsing utilities for hook handlers.
 * <p>
 * Provides tokenization of shell command strings (respecting single and double quotes) and
 * path resolution against a base directory.
 */
public final class ShellParser
{
  /**
   * Prevent instantiation.
   */
  private ShellParser()
  {
  }

  /**
   * Tokenizes a string by whitespace, respecting single and double quotes.
   * <p>
   * Quote characters are consumed (stripped from the resulting tokens). Whitespace inside
   * quoted regions is preserved as part of the token.
   *
   * @param input the input string to tokenize
   * @return list of tokens
   * @throws NullPointerException if {@code input} is null
   */
  public static List<String> tokenize(String input)
  {
    assert that(input, "input").isNotNull().elseThrow();
    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;

    for (int i = 0; i < input.length(); ++i)
    {
      char c = input.charAt(i);

      if (c == '\'' && !inDoubleQuote)
      {
        inSingleQuote = !inSingleQuote;
        continue;
      }

      if (c == '"' && !inSingleQuote)
      {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }

      if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote)
      {
        if (current.length() > 0)
        {
          tokens.add(current.toString());
          current.setLength(0);
        }
        continue;
      }

      current.append(c);
    }

    if (current.length() > 0)
      tokens.add(current.toString());

    return tokens;
  }

  /**
   * Resolves a path (absolute or relative) against a base directory.
   * <p>
   * Strips surrounding quotes (single or double) before resolving. The resolved path is
   * normalized to remove redundant elements.
   *
   * @param path the path string to resolve
   * @param base the base directory for relative paths
   * @return the resolved normalized absolute path
   * @throws NullPointerException if {@code path} or {@code base} are null
   */
  public static Path resolvePath(String path, String base)
  {
    assert that(path, "path").isNotNull().elseThrow();
    assert that(base, "base").isNotNull().elseThrow();
    path = path.replaceAll("^['\"]|['\"]$", "").strip();
    Path p = Path.of(path);
    if (p.isAbsolute())
      return p.normalize();
    return Path.of(base, path).normalize();
  }
}
