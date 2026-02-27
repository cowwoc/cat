/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.ShellParser;
import org.testng.annotations.Test;

import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for ShellParser tokenization, including quoting and backslash escape handling.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained with no shared state.
 */
public class ShellParserTest
{
  /**
   * Verifies that plain unquoted tokens are split on whitespace.
   */
  @Test
  public void tokenizeSplitsOnWhitespace()
  {
    List<String> tokens = ShellParser.tokenize("foo bar baz");
    requireThat(tokens, "tokens").isEqualTo(List.of("foo", "bar", "baz"));
  }

  /**
   * Verifies that double-quoted strings are treated as a single token with quotes stripped.
   */
  @Test
  public void tokenizeHandlesDoubleQuotes()
  {
    List<String> tokens = ShellParser.tokenize("\"hello world\"");
    requireThat(tokens, "tokens").isEqualTo(List.of("hello world"));
  }

  /**
   * Verifies that single-quoted strings are treated as a single token with quotes stripped.
   */
  @Test
  public void tokenizeHandlesSingleQuotes()
  {
    List<String> tokens = ShellParser.tokenize("'hello world'");
    requireThat(tokens, "tokens").isEqualTo(List.of("hello world"));
  }

  /**
   * Verifies that a backslash-escaped double quote inside a double-quoted string is treated
   * as a literal double quote character, not a closing delimiter.
   * <p>
   * POSIX: inside double quotes, {@code \"} → {@code "}.
   */
  @Test
  public void tokenizeHandlesBackslashEscapedDoubleQuoteInsideDoubleQuotes()
  {
    List<String> tokens = ShellParser.tokenize("\"path with \\\"embedded\\\" quotes\"");
    requireThat(tokens, "tokens").isEqualTo(List.of("path with \"embedded\" quotes"));
  }

  /**
   * Verifies that a backslash-escaped backslash inside a double-quoted string produces
   * a single literal backslash character.
   * <p>
   * POSIX: inside double quotes, {@code \\} → {@code \}.
   */
  @Test
  public void tokenizeHandlesBackslashEscapedBackslashInsideDoubleQuotes()
  {
    List<String> tokens = ShellParser.tokenize("\"path\\\\to\\\\file\"");
    requireThat(tokens, "tokens").isEqualTo(List.of("path\\to\\file"));
  }

  /**
   * Verifies that a backslash-escaped dollar sign inside a double-quoted string produces
   * a literal dollar sign (suppressing variable expansion).
   * <p>
   * POSIX: inside double quotes, {@code \$} → {@code $}.
   */
  @Test
  public void tokenizeHandlesBackslashEscapedDollarInsideDoubleQuotes()
  {
    List<String> tokens = ShellParser.tokenize("\"cost: \\$5\"");
    requireThat(tokens, "tokens").isEqualTo(List.of("cost: $5"));
  }

  /**
   * Verifies that backslash followed by a non-escapable character inside a double-quoted string
   * is passed through literally (backslash retained).
   * <p>
   * POSIX: inside double quotes, only {@code \"}, {@code \\}, {@code \$}, {@code \`}, and
   * {@code \newline} are special. Other sequences retain the backslash. This implementation
   * handles {@code \"}, {@code \\}, and {@code \$}.
   */
  @Test
  public void tokenizePassesThroughBackslashBeforeNonEscapableCharInsideDoubleQuotes()
  {
    List<String> tokens = ShellParser.tokenize("\"value\\nmore\"");
    requireThat(tokens, "tokens").isEqualTo(List.of("value\\nmore"));
  }

  /**
   * Verifies that backslash sequences inside single-quoted strings are NOT processed
   * (single quotes are literal — no escaping).
   */
  @Test
  public void tokenizeDoesNotProcessBackslashInsideSingleQuotes()
  {
    List<String> tokens = ShellParser.tokenize("'value\\\"more'");
    requireThat(tokens, "tokens").isEqualTo(List.of("value\\\"more"));
  }

  /**
   * Verifies that an empty input string produces an empty token list.
   */
  @Test
  public void tokenizeHandlesEmptyInput()
  {
    List<String> tokens = ShellParser.tokenize("");
    requireThat(tokens, "tokens").isEmpty();
  }

  /**
   * Verifies that a mix of quoted and unquoted tokens are all parsed correctly.
   */
  @Test
  public void tokenizeHandlesMixedQuotingStyles()
  {
    List<String> tokens = ShellParser.tokenize("--flag \"path with spaces\" --other 'another path'");
    requireThat(tokens, "tokens").isEqualTo(List.of("--flag", "path with spaces", "--other", "another path"));
  }
}
