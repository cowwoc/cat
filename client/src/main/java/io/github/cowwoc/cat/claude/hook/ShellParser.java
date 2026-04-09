/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared shell parsing utilities for hook handlers.
 * <p>
 * Provides tokenization of shell command strings (respecting single and double quotes) and
 * path resolution against a base directory.
 */
public final class ShellParser
{
  // Matches ${VAR} (group 1) or $VAR (group 2) for environment variable expansion.
  // Does not match $ARGUMENTS, $ARGUMENTS[N], or $N positional forms — those are skill-specific.
  // The $VAR form (group 2) stops at non-word characters.
  private static final Pattern ENV_VAR_EXPAND_PATTERN =
    Pattern.compile("\\$\\{([^}]+)}|\\$(\\w+)");

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
   * <p>
   * Inside double-quoted regions, POSIX backslash escape sequences are processed: {@code \"},
   * {@code \\}, and {@code \$} are each replaced by the character after the backslash. All other
   * backslash sequences inside double quotes are passed through literally (backslash retained).
   * Inside single-quoted regions, no escape processing is performed.
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

      if (inDoubleQuote && c == '\\' && i + 1 < input.length())
      {
        char next = input.charAt(i + 1);
        if (next == '"' || next == '\\' || next == '$')
        {
          current.append(next);
          ++i;
          continue;
        }
      }

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

    if (!current.isEmpty())
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

  /**
   * Expands {@code $VAR} and {@code ${VAR}} references in a string using the hook process
   * environment.
   * <p>
   * Returns {@code null} if any variable is unset, so the caller can fall back to conservative
   * behavior rather than evaluating a partially-expanded path.
   *
   * @param target the string containing variable references to expand
   * @return the fully expanded string, or {@code null} if any variable was undefined
   * @throws NullPointerException if {@code target} is null
   */
  public static String expandEnvVars(String target)
  {
    requireThat(target, "target").isNotNull();
    Matcher varMatcher = ENV_VAR_EXPAND_PATTERN.matcher(target);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;
    while (varMatcher.find())
    {
      result.append(target, lastEnd, varMatcher.start());
      // group(1) is the name from ${VAR}; group(2) is the name from $VAR
      String varName;
      if (varMatcher.group(1) != null)
        varName = varMatcher.group(1);
      else
        varName = varMatcher.group(2);
      String value = System.getenv(varName);
      if (value == null)
        return null;
      result.append(value);
      lastEnd = varMatcher.end();
    }
    result.append(target, lastEnd, target.length());
    return result.toString();
  }
}
