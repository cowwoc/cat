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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detect concatenated commit messages after git operations.
 * <p>
 * Trigger: PostToolUse for Bash (git rebase/commit/merge commands)
 */
public final class DetectConcatenatedCommit implements BashHandler
{
  private static final Pattern GIT_OPERATION_PATTERN = Pattern.compile("(rebase|commit|merge)");
  private static final Pattern CO_AUTHORED_PATTERN =
    Pattern.compile("^Co-Authored-By:", Pattern.MULTILINE);

  /**
   * Creates a new handler for detecting concatenated commit messages.
   */
  public DetectConcatenatedCommit()
  {
    // Handler class
  }

  @Override
  public Result check(String command, String workingDirectory, JsonNode toolInput, JsonNode toolResult,
    String sessionId)
  {
    // Only process git commands
    if (!command.contains("git"))
      return Result.allow();

    // Only check commands that can create commits
    if (!GIT_OPERATION_PATTERN.matcher(command).find())
      return Result.allow();

    // Get the most recent commit message
    String commitMsg;
    try
    {
      commitMsg = GitCommands.getLatestCommitMessage();
    }
    catch (IOException _)
    {
      return Result.warn("Failed to retrieve latest commit message for concatenation check.");
    }
    if (commitMsg.isEmpty())
      return Result.allow();

    // Count Co-Authored-By lines
    int coAuthoredCount = countMatches(CO_AUTHORED_PATTERN, commitMsg);

    if (coAuthoredCount > 1)
    {
      String commitHash;
      try
      {
        commitHash = GitCommands.getLatestCommitHash();
      }
      catch (IOException _)
      {
        commitHash = "unknown";
      }
      return Result.warn(String.format("""

        CONCATENATED COMMIT MESSAGE DETECTED

        Commit %s has %d 'Co-Authored-By' lines.
        This indicates a concatenated message from ad-hoc squashing.

        CLAUDE.md - Always Use git-squash Skill:
          Ad-hoc 'git rebase -i' with squash produces concatenated messages.
          Squashed commits need UNIFIED messages describing the final result.

        RECOMMENDATION:
          Use the git-squash skill which enforces writing a new unified message.
          The skill prompts you to describe what the final code DOES.

        TO FIX THIS COMMIT:
          1. git reset --soft HEAD~1  # Unstage commit
          2. git commit  # Write unified message
          Or use: git commit --amend  # Rewrite message""", commitHash, coAuthoredCount));
    }

    return Result.allow();
  }

  /**
   * Counts the number of matches for a pattern in a string.
   *
   * @param pattern the pattern to match
   * @param text the text to search
   * @return the number of matches
   */
  private int countMatches(Pattern pattern, String text)
  {
    Matcher matcher = pattern.matcher(text);
    int count = 0;
    while (matcher.find())
      ++count;
    return count;
  }
}
