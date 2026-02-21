/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.ShellParser;
import io.github.cowwoc.cat.hooks.WorktreeContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block Bash file-write commands that would violate worktree isolation.
 * <p>
 * When an agent is working in an issue worktree, Bash file-writing patterns (shell redirects,
 * {@code tee}, {@code cp}, {@code mv}) that target paths inside the project directory but outside
 * the worktree are blocked. This prevents agents from bypassing the Edit/Write isolation hooks by
 * using Bash to write files directly to the main workspace.
 * <p>
 * Detected patterns:
 * <ul>
 *   <li>Shell output redirects: {@code > /path} and {@code >> /path} (quoted and unquoted paths)</li>
 *   <li>{@code tee /path} and {@code tee -a /path} (quoted and unquoted paths)</li>
 *   <li>{@code cp source /path} (destination argument)</li>
 *   <li>{@code mv source /path} (destination argument)</li>
 * </ul>
 * <p>
 * Only paths under the project directory are checked. Writes to {@code /tmp} or other locations
 * outside the project directory are allowed. If no session lock exists, all commands are allowed.
 * <p>
 * Commands containing shell variable references (e.g., {@code $VAR/path}) in write targets are
 * conservatively blocked because variable expansion cannot be verified statically.
 */
public final class BlockWorktreeIsolationViolation implements BashHandler
{
  // Matches shell output redirects: > /path or >> /path
  // Handles: echo "foo" > /path, cmd | tee > /path, etc.
  // Negative lookbehind excludes &> (stderr redirect)
  // Captured group matches: double-quoted strings (with backslash escape support),
  //   single-quoted strings, or unquoted paths
  private static final Pattern REDIRECT_PATTERN =
    Pattern.compile("(?<!&)>>?\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'|[^\\s;&|>]+)");

  // Matches: tee followed by optional flags, then captures the first filename
  // Flags: -a, -A, --append (may repeat)
  // Captured group matches: double-quoted strings (with backslash escape support),
  //   single-quoted strings, or unquoted paths
  private static final Pattern TEE_PATTERN =
    Pattern.compile("\\btee\\s+(?:-[aA]\\s+|--append\\s+)*(\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'|[^\\s;&|>]+)");

  // Matches "cp" as a standalone word (not part of another command name)
  private static final Pattern CP_PATTERN =
    Pattern.compile("(?:^|[;&|\\s])cp(?:\\s|$)");

  // Matches "mv" as a standalone word (not part of another command name)
  private static final Pattern MV_PATTERN =
    Pattern.compile("(?:^|[;&|\\s])mv(?:\\s|$)");

  private final Path projectDir;
  private final JsonMapper mapper;

  /**
   * Creates a new handler for blocking worktree isolation violations.
   *
   * @param scope the JVM scope providing access to shared resources
   * @throws NullPointerException if {@code scope} is null
   */
  public BlockWorktreeIsolationViolation(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.projectDir = scope.getClaudeProjectDir();
    this.mapper = scope.getJsonMapper();
  }

  @Override
  public Result check(String command, String workingDirectory, JsonNode toolInput, JsonNode toolResult,
    String sessionId)
  {
    requireThat(command, "command").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    WorktreeContext context = WorktreeContext.forSession(projectDir, mapper, sessionId);
    if (context == null)
      return Result.allow();

    // Extract all write targets from the command
    List<String> targets = extractWriteTargets(command);

    for (String target : targets)
    {
      // Block variable-expanded paths conservatively — they cannot be verified statically
      if (target.contains("$") || target.contains("`"))
      {
        String message = """
          WARNING: Cannot verify Bash redirect to variable-expanded path: %s

          If this targets a path outside your worktree, it bypasses worktree isolation.
          Use the Edit or Write tools with an explicit absolute path instead:
            Use: %s/plugin/file.txt
            Not: $CLAUDE_PROJECT_DIR/plugin/file.txt""".formatted(target, context.absoluteWorktreePath());
        return Result.block(message);
      }

      Path targetPath = ShellParser.resolvePath(target, workingDirectory);

      // Only check paths inside the project directory
      if (!targetPath.startsWith(context.absoluteProjectDirectory()))
        continue;

      // Allow paths inside the worktree
      if (targetPath.startsWith(context.absoluteWorktreePath()))
        continue;

      // Compute the corrected worktree-relative path
      Path correctedPath = context.correctedPath(targetPath);

      String message = """
        ERROR: Worktree isolation violation (Bash file write)

        You are working in worktree: %s
        But attempting to write outside it via Bash: %s

        Use the corrected worktree path instead:
          %s

        Do NOT use shell redirects, tee, cp, or mv to write files outside the worktree. \
        Use the Edit or Write tools with the corrected path.""".formatted(
        context.absoluteWorktreePath(),
        targetPath,
        correctedPath);

      return Result.block(message);
    }

    return Result.allow();
  }

  /**
   * Extracts all file write target paths from a bash command.
   * <p>
   * Detects shell output redirects ({@code >}, {@code >>}), {@code tee} (including all
   * space-separated targets after flags), {@code cp} destination, and {@code mv} destination.
   *
   * @param command the bash command to parse
   * @return list of target path strings found in the command
   */
  private List<String> extractWriteTargets(String command)
  {
    List<String> targets = new ArrayList<>();

    // Shell redirects: > /path or >> /path
    Matcher redirectMatcher = REDIRECT_PATTERN.matcher(command);
    while (redirectMatcher.find())
    {
      String target = redirectMatcher.group(1);
      if (!target.isEmpty())
        targets.add(target);
    }

    // tee /path [/path2 ...] — capture first target via TEE_PATTERN, then scan for more
    Matcher teeMatcher = TEE_PATTERN.matcher(command);
    while (teeMatcher.find())
    {
      String firstTarget = teeMatcher.group(1);
      if (!firstTarget.isEmpty())
        targets.add(firstTarget);

      // Find the start of the tee argument list (after "tee" keyword and any flags)
      // and collect all remaining space-separated arguments until a shell operator
      int teeKeywordEnd = teeMatcher.start(1);
      String afterFirstTarget = command.substring(teeKeywordEnd + firstTarget.length());
      List<String> additionalTargets = extractTeeAdditionalTargets(afterFirstTarget);
      targets.addAll(additionalTargets);
    }

    // cp source dest — destination is the last non-option argument
    String cpDest = extractCpMvDestination(command, CP_PATTERN);
    if (cpDest != null)
      targets.add(cpDest);

    // mv source dest — destination is the last non-option argument
    String mvDest = extractCpMvDestination(command, MV_PATTERN);
    if (mvDest != null)
      targets.add(mvDest);

    return targets;
  }

  /**
   * Returns the index of the first shell operator ({@code ;}, {@code &}, {@code |}) in
   * {@code text}, or {@code text.length()} if no operator is found.
   *
   * @param text the text to scan
   * @return the index of the first shell operator, or {@code text.length()} if none found
   */
  private int findShellOperatorIndex(String text)
  {
    for (int i = 0; i < text.length(); ++i)
    {
      char c = text.charAt(i);
      if (c == ';' || c == '&' || c == '|')
        return i;
    }
    return text.length();
  }

  /**
   * Extracts additional tee target filenames after the first one has been matched.
   * <p>
   * Reads space-separated tokens from the given string until a shell operator ({@code ;},
   * {@code &}, {@code |}) or end of string is reached. Option flags ({@code -a}, {@code -A},
   * {@code --append}) are skipped.
   *
   * @param afterFirstTarget the command text immediately following the first tee target
   * @return additional tee targets found after the first target
   */
  private List<String> extractTeeAdditionalTargets(String afterFirstTarget)
  {
    List<String> additionalTargets = new ArrayList<>();

    // Find the end of this tee command (stop at shell operators)
    int operatorIndex = findShellOperatorIndex(afterFirstTarget);
    String teeArgs = afterFirstTarget.substring(0, operatorIndex);

    // Tokenize and collect all non-flag arguments
    List<String> tokens = ShellParser.tokenize(teeArgs);
    for (String token : tokens)
    {
      if (token.equals("-a") || token.equals("-A") || token.equals("--append"))
        continue;
      if (token.startsWith("-"))
        continue;
      additionalTargets.add(token);
    }

    return additionalTargets;
  }

  /**
   * Extracts the destination path from a {@code cp} or {@code mv} command.
   * <p>
   * The destination is defined as the last non-option argument before any shell operator.
   * Returns {@code null} if the command does not contain the given verb pattern, or has fewer
   * than two non-option arguments (source and destination are both required).
   *
   * @param command the bash command to parse
   * @param verbPattern the pre-compiled pattern for the command verb
   * @return the destination path string, or {@code null} if not found
   */
  private String extractCpMvDestination(String command, Pattern verbPattern)
  {
    Matcher verbMatcher = verbPattern.matcher(command);
    if (!verbMatcher.find())
      return null;

    // Extract the portion after the verb until we hit a shell operator
    int verbEnd = verbMatcher.end();
    String afterVerb = command.substring(verbEnd);

    // Find the end of this command (shell operators)
    int operatorIndex = findShellOperatorIndex(afterVerb);
    String args = afterVerb.substring(0, operatorIndex);

    // Tokenize and find non-option arguments
    List<String> tokens = ShellParser.tokenize(args);
    List<String> nonOptionArgs = new ArrayList<>();
    boolean endOfOptions = false;

    for (String token : tokens)
    {
      if (token.equals("--"))
      {
        endOfOptions = true;
        continue;
      }
      if (!endOfOptions && token.startsWith("-"))
        continue;
      nonOptionArgs.add(token);
    }

    // Need at least source + destination (2 args)
    if (nonOptionArgs.size() < 2)
      return null;

    // Destination is the last argument
    return nonOptionArgs.get(nonOptionArgs.size() - 1);
  }
}
