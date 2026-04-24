/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.ShellParser;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

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

  /**
   * Verifies that parentheses inside a double-quoted string are treated as literals,
   * not as shell glob or subshell metacharacters.
   */
  @Test
  public void tokenizeHandlesParenthesesInsideDoubleQuotes()
  {
    List<String> tokens = ShellParser.tokenize("\"occurrences: (3/5)\"");
    requireThat(tokens, "tokens").isEqualTo(List.of("occurrences: (3/5)"));
  }

  /**
   * Verifies that square brackets inside a double-quoted string are treated as literals,
   * not as shell glob character class metacharacters.
   */
  @Test
  public void tokenizeHandlesBracketsInsideDoubleQuotes()
  {
    List<String> tokens = ShellParser.tokenize("\"commits: [abc123,def456]\"");
    requireThat(tokens, "tokens").isEqualTo(List.of("commits: [abc123,def456]"));
  }

  /**
   * Verifies that curly braces inside a double-quoted string are treated as literals,
   * not as shell brace expansion metacharacters.
   */
  @Test
  public void tokenizeHandlesBracesInsideDoubleQuotes()
  {
    List<String> tokens = ShellParser.tokenize("\"{\\\"key\\\": \\\"value\\\"}\"");
    requireThat(tokens, "tokens").isEqualTo(List.of("{\"key\": \"value\"}"));
  }

  /**
   * Verifies that wildcards inside a double-quoted string are treated as literals,
   * not as shell glob expansion characters.
   */
  @Test
  public void tokenizeHandlesWildcardsInsideDoubleQuotes()
  {
    List<String> tokens = ShellParser.tokenize("\"pattern: *.java and file?.txt\"");
    requireThat(tokens, "tokens").isEqualTo(List.of("pattern: *.java and file?.txt"));
  }

  /**
   * Verifies that a mix of multiple tokens where some contain shell metacharacters works correctly.
   * The metacharacter-containing token must be double-quoted; unquoted tokens are split normally.
   */
  @Test
  public void tokenizeHandlesMixedTokensWithMetacharacters()
  {
    List<String> tokens = ShellParser.tokenize("skill-name \"arg with (parens) and [brackets]\" --flag");
    requireThat(tokens, "tokens").isEqualTo(List.of("skill-name", "arg with (parens) and [brackets]", "--flag"));
  }

  /**
   * Verifies that a null input throws AssertionError.
   */
  @Test(expectedExceptions = AssertionError.class)
  public void tokenizeThrowsAssertionErrorWhenInputIsNull()
  {
    ShellParser.tokenize(null);
  }

  /**
   * Verifies that an unclosed double-quoted string is handled leniently: the remaining content
   * after the opening quote is returned as a token with the quote stripped.
   */
  @Test
  public void tokenizeHandlesUnclosedDoubleQuote()
  {
    List<String> tokens = ShellParser.tokenize("\"unclosed content");
    requireThat(tokens, "tokens").isEqualTo(List.of("unclosed content"));
  }

  /**
   * Verifies that a path containing one undefined variable returns a list with that variable name.
   */
  @Test
  public void findUndefinedVarsReturnsOneUndefinedVariable()
  {
    List<String> result = ShellParser.findUndefinedVars("${SESSION_DIR}/file", name -> null);
    requireThat(result, "result").isEqualTo(List.of("SESSION_DIR"));
  }

  /**
   * Verifies that a path containing two undefined variables returns both variable names in order.
   */
  @Test
  public void findUndefinedVarsReturnsTwoUndefinedVariables()
  {
    List<String> result = ShellParser.findUndefinedVars(
      "${SESSION_DIR}/squash-complete-${ISSUE_ID}", name -> null);
    requireThat(result, "result").isEqualTo(List.of("SESSION_DIR", "ISSUE_ID"));
  }

  /**
   * Verifies that a path where all variables are defined returns an empty list.
   */
  @Test
  public void findUndefinedVarsReturnsEmptyListWhenAllDefined()
  {
    List<String> result = ShellParser.findUndefinedVars(
      "${SESSION_DIR}/file", name -> "/tmp");
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that when only some variables are defined the method returns only the
   * undefined names, leaving out defined ones.
   */
  @Test
  public void findUndefinedVarsReturnsOnlyUndefinedWhenMixed()
  {
    // SESSION_DIR is defined; ISSUE_ID is not
    List<String> result = ShellParser.findUndefinedVars(
      "${SESSION_DIR}/squash-complete-${ISSUE_ID}",
      name ->
      {
        if (name.equals("SESSION_DIR"))
          return "/tmp";
        return null;
      });
    requireThat(result, "result").isEqualTo(List.of("ISSUE_ID"));
  }

  /**
   * Verifies that an empty input string returns an empty list.
   */
  @Test
  public void findUndefinedVarsReturnsEmptyListForEmptyInput()
  {
    List<String> result = ShellParser.findUndefinedVars("", name -> null);
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that a path containing no variable references returns an empty list.
   */
  @Test
  public void findUndefinedVarsReturnsEmptyListForLiteralPath()
  {
    List<String> result = ShellParser.findUndefinedVars("/tmp/output.log", name -> null);
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that the unbraced {@code $VAR} form is recognized and reported when undefined.
   */
  @Test
  public void findUndefinedVarsRecognizesUnbracedForm()
  {
    List<String> result = ShellParser.findUndefinedVars("$SESSION_DIR/file", name -> null);
    requireThat(result, "result").isEqualTo(List.of("SESSION_DIR"));
  }

  /**
   * Verifies that a path mixing braced and unbraced forms reports both undefined variables.
   */
  @Test
  public void findUndefinedVarsHandlesMixedBracedAndUnbracedForms()
  {
    List<String> result = ShellParser.findUndefinedVars(
      "${SESSION_DIR}/squash-$ISSUE_ID", name -> null);
    requireThat(result, "result").isEqualTo(List.of("SESSION_DIR", "ISSUE_ID"));
  }

  /**
   * Verifies that the same variable referenced twice appears twice in the result list,
   * preserving occurrence order.
   */
  @Test
  public void findUndefinedVarsReturnsDuplicateOccurrences()
  {
    List<String> result = ShellParser.findUndefinedVars(
      "${SESSION_DIR}/${SESSION_DIR}", name -> null);
    requireThat(result, "result").isEqualTo(List.of("SESSION_DIR", "SESSION_DIR"));
  }

  /**
   * Verifies that passing a null target throws a NullPointerException.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void findUndefinedVarsThrowsNullPointerExceptionWhenTargetIsNull()
  {
    ShellParser.findUndefinedVars(null, name -> null);
  }

  /**
   * Verifies that a double-quoted assignment whose value references a variable defined
   * earlier in the same script is resolved to its fully-expanded concrete value.
   * <p>
   * This covers the chained-assignment pattern:
   * <pre>
   * WORKTREE_PATH="/tmp/worktrees/my-issue"
   * OUTPUT_FILE="${WORKTREE_PATH}/.cat/work/output.log"
   * </pre>
   * {@code OUTPUT_FILE} must resolve to {@code /tmp/worktrees/my-issue/.cat/work/output.log}.
   */
  @Test
  public void parseScriptAssignmentsResolvesChainedLiteralVariables()
  {
    String script = """
        WORKTREE_PATH="/tmp/worktrees/my-issue"
        OUTPUT_FILE="${WORKTREE_PATH}/.cat/work/output.log"
        """;
    Map<String, String> assignments = ShellParser.parseScriptAssignments(script);
    requireThat(assignments.get("OUTPUT_FILE"), "OUTPUT_FILE").
      isEqualTo("/tmp/worktrees/my-issue/.cat/work/output.log");
  }

  /**
   * Verifies that when an intermediate variable is undefined, any dependent variable is NOT
   * added to the assignments map.
   * <p>
   * If {@code UNDEFINED_VAR} was never assigned in the script, then
   * {@code OUTPUT_FILE="${UNDEFINED_VAR}/output.log"} cannot be resolved and must be omitted.
   */
  @Test
  public void parseScriptAssignmentsSkipsUnresolvableIntermediateVariable()
  {
    String script = """
        OUTPUT_FILE="${UNDEFINED_VAR}/output.log"
        """;
    Map<String, String> assignments = ShellParser.parseScriptAssignments(script);
    requireThat(assignments.containsKey("OUTPUT_FILE"), "containsKey(OUTPUT_FILE)").isFalse();
  }

  /**
   * Verifies that a three-link chain of variable assignments is fully resolved.
   * <p>
   * Given:
   * <pre>
   * BASE="/tmp/root"
   * MIDDLE="${BASE}/middle"
   * LEAF="${MIDDLE}/leaf"
   * </pre>
   * {@code LEAF} must resolve to {@code /tmp/root/middle/leaf}.
   */
  @Test
  public void parseScriptAssignmentsResolvesThreeLinkChain()
  {
    String script = """
        BASE="/tmp/root"
        MIDDLE="${BASE}/middle"
        LEAF="${MIDDLE}/leaf"
        """;
    Map<String, String> assignments = ShellParser.parseScriptAssignments(script);
    requireThat(assignments.get("LEAF"), "LEAF").isEqualTo("/tmp/root/middle/leaf");
  }

  /**
   * Verifies that a self-referential assignment is skipped when the variable was not
   * previously defined.
   * <p>
   * {@code FOO="${FOO}/extra"} cannot be resolved because {@code FOO} has not been assigned
   * earlier in the script, so it must not appear in the assignments map.
   */
  @Test
  public void parseScriptAssignmentsSkipsSelfReferentialAssignment()
  {
    String script = """
        FOO="${FOO}/extra"
        """;
    Map<String, String> assignments = ShellParser.parseScriptAssignments(script);
    requireThat(assignments.containsKey("FOO"), "containsKey(FOO)").isFalse();
  }

  /**
   * Verifies that a circular chain of variable assignments does not cause infinite loops or
   * errors, and that neither variable appears in the assignments map.
   * <p>
   * Given {@code A="${B}/x"} and {@code B="${A}/y"}, neither variable can be resolved since
   * each depends on the other, which was undefined at the time of processing.
   */
  @Test
  public void parseScriptAssignmentsHandlesCircularChain()
  {
    String script = """
        A="${B}/x"
        B="${A}/y"
        """;
    Map<String, String> assignments = ShellParser.parseScriptAssignments(script);
    requireThat(assignments.containsKey("A"), "containsKey(A)").isFalse();
    requireThat(assignments.containsKey("B"), "containsKey(B)").isFalse();
  }
}
