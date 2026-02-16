/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash.post;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verify commit type after git commit completes.
 * <p>
 * Defense-in-depth - catches commits that slip through PreToolUse validation.
 * This is a WARNING hook (does not block) since the commit already happened.
 * <p>
 * Trigger: PostToolUse for Bash (git commit commands)
 */
public final class VerifyCommitType implements BashHandler
{
  private static final List<String> CLAUDE_FACING_PATTERNS = Arrays.asList(
    "CLAUDE.md",
    "plugin/",            // CAT plugin directory (A007: M255, M306)
    ".claude/",
    "client/",
    "skills/",
    "concepts/",
    "commands/",
    "retrospectives/",
    "mistakes.json",      // Legacy single file
    "mistakes-",          // Split files: mistakes-YYYY-MM.json
    "retrospectives-",    // Split files: retrospectives-YYYY-MM.json
    "index.json");        // Retrospective index

  private static final List<Pattern> SOURCE_PATTERNS = Arrays.asList(
    Pattern.compile("\\.java$"),
    Pattern.compile("\\.py$"),
    Pattern.compile("\\.js$"),
    Pattern.compile("\\.ts$"),
    Pattern.compile("\\.go$"),
    Pattern.compile("\\.rs$"),
    Pattern.compile("src/"),
    Pattern.compile("lib/"));

  private static final Pattern COMMIT_TYPE_PATTERN = Pattern.compile("^([a-z]+):");

  /**
   * Creates a new handler for verifying commit types after commit.
   */
  public VerifyCommitType()
  {
    // Handler class
  }

  @Override
  public Result check(String command, String workingDirectory, JsonNode toolInput, JsonNode toolResult,
    String sessionId)
  {
    // Only check git commit commands
    if (!command.contains("git commit"))
      return Result.allow();

    // Skip amend (user is already fixing)
    if (command.contains("--amend"))
      return Result.allow();

    // Get commit message
    String commitMsg;
    try
    {
      commitMsg = GitCommands.getLatestCommitMessage();
    }
    catch (IOException _)
    {
      return Result.warn("Failed to retrieve latest commit message for type verification.");
    }
    if (commitMsg.isEmpty())
      return Result.allow();

    // Extract commit type
    Matcher typeMatcher = COMMIT_TYPE_PATTERN.matcher(commitMsg);
    if (!typeMatcher.find())
      return Result.allow();

    String commitType = typeMatcher.group(1);

    // Check for docs: used on Claude-facing files
    if (commitType.equals("docs"))
    {
      try
      {
        Result result = checkDocsOnClaudeFacing();
        if (result != null)
          return result;
      }
      catch (IOException _)
      {
        return Result.warn("Failed to retrieve commit files/hash for docs-on-Claude-facing check.");
      }
    }

    // Check for config: used on source code
    if (commitType.equals("config"))
    {
      try
      {
        Result result = checkConfigOnSourceCode();
        if (result != null)
          return result;
      }
      catch (IOException _)
      {
        return Result.warn("Failed to retrieve commit files/hash for config-on-source-code check.");
      }
    }

    return Result.allow();
  }

  /**
   * Checks if docs: commit type is used on Claude-facing files.
   *
   * @return a warning result if violation found, null otherwise
   * @throws IOException if the git command fails
   */
  private Result checkDocsOnClaudeFacing() throws IOException
  {
    String commitFiles = GitCommands.getLatestCommitFiles();
    String commitHash = GitCommands.getLatestCommitHash();

    for (String pattern : CLAUDE_FACING_PATTERNS)
    {
      if (commitFiles.contains(pattern))
      {
        return Result.warn(String.format("""

          POST-COMMIT WARNING: 'docs:' used for Claude-facing file

          Commit %s contains Claude-facing files (matched: %s)
          Claude-facing files should use 'config:', not 'docs:'

          Rule (M089): docs: = user-facing, config: = Claude-facing

          TO FIX: git commit --amend
            Then change 'docs:' to 'config:' in the commit message""", commitHash, pattern));
      }
    }
    return null;
  }

  /**
   * Checks if config: commit type is used on source code files.
   *
   * @return a warning result if violation found, null otherwise
   * @throws IOException if the git command fails
   */
  private Result checkConfigOnSourceCode() throws IOException
  {
    String commitFiles = GitCommands.getLatestCommitFiles();
    String commitHash = GitCommands.getLatestCommitHash();

    for (Pattern pattern : SOURCE_PATTERNS)
    {
      if (pattern.matcher(commitFiles).find())
      {
        return Result.warn(String.format("""

          POST-COMMIT WARNING: 'config:' used for source code

          Commit %s contains source code files (matched: %s)
          Source code should use: feature:, bugfix:, refactor:, test:, or performance:

          TO FIX: git commit --amend""", commitHash, pattern.pattern()));
      }
    }
    return null;
  }
}
