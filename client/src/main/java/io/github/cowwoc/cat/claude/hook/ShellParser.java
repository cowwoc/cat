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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

  // Matches a literal shell variable assignment at the start of a line:
  //   VAR="value"  (double-quoted: no backtick or backslash — allows $ for chained references)
  //   VAR='value'  (single-quoted: always literal)
  //   VAR=value    (unquoted: no whitespace/metacharacters; negative lookahead skips $(...))
  // Groups: 1=name, 2=double-quoted value, 3=single-quoted value, 4=unquoted value
  private static final Pattern SCRIPT_ASSIGNMENT_PATTERN = Pattern.compile(
    "(?m)^\\s*([A-Za-z_][A-Za-z0-9_]*)=(?:\"([^\"`\\\\]*)\"|'([^']*)'|(?!\\$\\()([^\\s\"'`\\\\|&;()<>\\n]+))");

  // Matches a variable assignment via mktemp command substitution, both $(...) and backtick forms:
  //   VAR=$(mktemp ...)
  //   VAR=`mktemp ...`
  // Groups: 1=variable name, 2=mktemp options (from $(...) form), 3=mktemp options (from backtick form)
  private static final Pattern MKTEMP_ASSIGNMENT_PATTERN =
    Pattern.compile("(?m)^\\s*([A-Za-z_][A-Za-z0-9_]*)=(?:\\$\\(mktemp\\b([^)]*)\\)|`mktemp\\b([^`]*)`)");

  // Extracts the argument following -p in a mktemp options string (e.g. "-p /some/path --suffix=.md")
  // Group 1 captures the path value, which may be quoted (single or double quotes) or unquoted.
  private static final Pattern MKTEMP_P_FLAG_PATTERN =
    Pattern.compile("-p\\s+(?:\"([^\"]*)\"|'([^']*)'|(\\S+))");

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
    return expandEnvVars(target, System::getenv);
  }

  /**
   * Expands {@code $VAR} and {@code ${VAR}} references in a string using the supplied lookup
   * function.
   * <p>
   * Returns {@code null} if any variable is unset (i.e., the lookup function returns {@code null}
   * for that variable name), so the caller can fall back to conservative behavior rather than
   * evaluating a partially-expanded path.
   *
   * @param target    the string containing variable references to expand
   * @param envLookup a function mapping variable names to their values; returns {@code null} if
   *                  the variable is unset
   * @return the fully expanded string, or {@code null} if any variable was undefined
   * @throws NullPointerException if {@code target} or {@code envLookup} are null
   */
  public static String expandEnvVars(String target, Function<String, String> envLookup)
  {
    requireThat(target, "target").isNotNull();
    requireThat(envLookup, "envLookup").isNotNull();
    Matcher varMatcher = ENV_VAR_EXPAND_PATTERN.matcher(target);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;
    while (varMatcher.find())
    {
      result.append(target, lastEnd, varMatcher.start());
      String varName = extractVarName(varMatcher);
      String value = envLookup.apply(varName);
      if (value == null)
        return null;
      result.append(value);
      lastEnd = varMatcher.end();
    }
    result.append(target, lastEnd, target.length());
    return result.toString();
  }

  /**
   * Returns the names of all {@code $VAR} and {@code ${VAR}} references in {@code target} whose
   * values are not present in {@code envLookup}.
   * <p>
   * This method performs a second-pass scan used when {@link #expandEnvVars(String, Function)}
   * already returned {@code null}, to report which specific variable(s) could not be resolved.
   *
   * @param target    the string containing variable references to inspect
   * @param envLookup a function mapping variable names to their values; returns {@code null} if
   *                  the variable is unset
   * @return a list of variable names for which {@code envLookup} returned {@code null}, in the
   *         order they appear in {@code target}; empty if all variables are defined
   * @throws NullPointerException if {@code target} or {@code envLookup} are null
   */
  public static List<String> findUndefinedVars(String target, Function<String, String> envLookup)
  {
    requireThat(target, "target").isNotNull();
    requireThat(envLookup, "envLookup").isNotNull();
    List<String> undefined = new ArrayList<>();
    Matcher varMatcher = ENV_VAR_EXPAND_PATTERN.matcher(target);
    while (varMatcher.find())
    {
      String varName = extractVarName(varMatcher);
      if (envLookup.apply(varName) == null)
        undefined.add(varName);
    }
    return undefined;
  }

  /**
   * Extracts the variable name from a matcher positioned on a match of
   * {@code ENV_VAR_EXPAND_PATTERN}.
   * <p>
   * Group 1 captures the name from the {@code ${VAR}} form; group 2 captures it from
   * the {@code $VAR} form. Exactly one of the two groups is non-null on every match.
   *
   * @param matcher a matcher that has just found a match
   * @return the captured variable name
   */
  private static String extractVarName(Matcher matcher)
  {
    if (matcher.group(1) != null)
      return matcher.group(1);
    return matcher.group(2);
  }

  /**
   * Scans {@code script} for literal variable assignments and returns them as a map.
   * <p>
   * Captured assignments:
   * <ul>
   *   <li>Single-quoted values — always captured verbatim.</li>
   *   <li>Double-quoted values with no {@code $} — captured as pure literals.</li>
   *   <li>Double-quoted values containing {@code $} but not {@code $(} — the references are
   *       expanded against previously captured assignments in the same script (chained
   *       resolution). If any referenced variable is unresolvable the assignment is skipped.</li>
   *   <li>Unquoted values (no whitespace or shell metacharacters) — captured as pure literals
   *       when they contain no {@code $}, or resolved via chained resolution when they do
   *       (same rules as double-quoted values).</li>
   * </ul>
   * Double-quoted or unquoted values containing {@code $(} (command substitution) are ignored
   * because they cannot be evaluated statically.
   * <p>
   * Assignments are processed in script order so that later assignments can reference earlier
   * ones. When the same variable is assigned multiple times, the last assignment wins (matching
   * bash semantics where each assignment shadows the previous).
   *
   * @param script the full bash command or script text to scan
   * @return a mutable map from variable name to its resolved value; empty if no resolvable
   *         assignments are found
   * @throws NullPointerException if {@code script} is null
   */
  public static Map<String, String> parseScriptAssignments(String script)
  {
    requireThat(script, "script").isNotNull();
    Map<String, String> assignments = new LinkedHashMap<>();
    Matcher assignmentMatcher = SCRIPT_ASSIGNMENT_PATTERN.matcher(script);
    while (assignmentMatcher.find())
    {
      String varName = assignmentMatcher.group(1);
      String rawValue;
      boolean isSingleQuoted;
      if (assignmentMatcher.group(2) != null)
      {
        rawValue = assignmentMatcher.group(2);
        isSingleQuoted = false;
      }
      else if (assignmentMatcher.group(3) != null)
      {
        rawValue = assignmentMatcher.group(3);
        isSingleQuoted = true;
      }
      else
      {
        // group 4: unquoted value — treat the same as double-quoted but with no surrounding quotes.
        // If the capture stopped at a mid-value '$(' boundary the value was part of a command
        // substitution that cannot be evaluated statically; skip it.
        rawValue = assignmentMatcher.group(4);
        int matchEnd = assignmentMatcher.end();
        if (rawValue.endsWith("$") && matchEnd < script.length() && script.charAt(matchEnd) == '(')
          continue;
        isSingleQuoted = false;
      }
      if (isSingleQuoted || !rawValue.contains("$"))
      {
        // pure literal
        assignments.put(varName, rawValue);
      }
      else if (rawValue.contains("$("))
      {
        // skip — command substitution
      }
      else
      {
        String expanded = expandEnvVars(rawValue, assignments::get);
        if (expanded != null)
        {
          // resolved chain
          assignments.put(varName, expanded);
        }
      }
    }
    return assignments;
  }

  /**
   * Scans {@code script} for variable assignments via {@code mktemp} command substitution and
   * returns a map from variable name to the {@code -p} directory path argument, if present.
   * <p>
   * Recognizes both the {@code $(...)} and backtick command substitution forms:
   * <ul>
   *   <li>{@code VARNAME=$(mktemp [-p <path>] ...)}
   *   <li>{@code VARNAME=`mktemp [-p <path>] ...`}
   * </ul>
   * <p>
   * When the mktemp invocation includes a {@code -p <path>} argument, the entry value is the
   * path string (stripped of any surrounding quotes). When no {@code -p} argument is present,
   * the entry value is {@code null}, indicating that the file's location is unresolvable at
   * hook evaluation time.
   * <p>
   * When the same variable is assigned multiple times, the last assignment wins (matching
   * bash semantics where each assignment shadows the previous).
   *
   * @param script the full bash command or script text to scan
   * @return a mutable map from variable name to the {@code -p} directory path (or {@code null}
   *         if {@code -p} was not supplied); empty if no mktemp assignments are found
   * @throws NullPointerException if {@code script} is null
   */
  public static Map<String, String> parseMktempAssignments(String script)
  {
    requireThat(script, "script").isNotNull();
    Map<String, String> assignments = new LinkedHashMap<>();
    Matcher assignmentMatcher = MKTEMP_ASSIGNMENT_PATTERN.matcher(script);
    while (assignmentMatcher.find())
    {
      String varName = assignmentMatcher.group(1);
      // Group 2 is the options string from the $(...) form; group 3 from the backtick form.
      String options;
      if (assignmentMatcher.group(2) != null)
        options = assignmentMatcher.group(2);
      else
        options = assignmentMatcher.group(3);
      String directory = extractMktempDirectory(options);
      assignments.put(varName, directory);
    }
    return assignments;
  }

  /**
   * Extracts the {@code -p <path>} argument from a mktemp options string.
   * <p>
   * Returns {@code null} when the options string contains no {@code -p} flag, signalling that
   * the generated file's parent directory is unknown.
   *
   * @param options the options portion of a mktemp invocation (everything after "mktemp")
   * @return the path argument following {@code -p}, with surrounding quotes stripped; or
   *         {@code null} if {@code -p} is not present
   */
  private static String extractMktempDirectory(String options)
  {
    assert that(options, "options").isNotNull().elseThrow();
    Matcher flagMatcher = MKTEMP_P_FLAG_PATTERN.matcher(options);
    if (!flagMatcher.find())
      return null;
    // Group 1: double-quoted path; group 2: single-quoted path; group 3: unquoted path.
    if (flagMatcher.group(1) != null)
      return flagMatcher.group(1);
    if (flagMatcher.group(2) != null)
      return flagMatcher.group(2);
    return flagMatcher.group(3);
  }
}
