/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.CatMetadata;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block git commit commands when the current branch does not match the expected issue branch.
 * <p>
 * When an agent is working inside a CAT issue worktree, it must commit only to the issue branch.
 * This hook detects {@code git commit} commands, identifies whether the current directory is a
 * CAT worktree (by checking for the {@code cat-branch-point} marker file), derives the expected
 * branch from the worktree's git directory path, and blocks the commit if the current branch
 * does not match.
 * <p>
 * Commands containing a {@code cd <path>} prefix are supported: the effective working directory
 * used for git operations is the last {@code cd} target found in the command.
 */
public final class BlockWrongBranchCommit implements BashHandler
{
  private static final Pattern COMMIT_PATTERN =
    Pattern.compile("(^|[;&|])\\s*git\\s+commit(?:\\s|$)");
  private static final Pattern CD_PATTERN =
    Pattern.compile("(?:^|[;&|])\\s*cd\\s+([^;&|\\s]+)");

  /**
   * Creates a new handler for blocking commits to the wrong branch.
   */
  public BlockWrongBranchCommit()
  {
    // Handler class
  }

  @Override
  public Result check(String command, String workingDirectory, JsonNode toolInput, JsonNode toolResult,
    String sessionId)
  {
    requireThat(command, "command").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    if (!COMMIT_PATTERN.matcher(command).find())
      return Result.allow();

    // Fast path: skip CD_PATTERN scan if no cd directive in command
    String effectiveDirectory;
    if (command.contains("cd ") || command.contains("cd\t"))
      effectiveDirectory = extractCdDirectory(command, workingDirectory);
    else
      effectiveDirectory = workingDirectory;

    String gitDirPath;
    try
    {
      gitDirPath = GitCommands.runGit(Path.of(effectiveDirectory), "rev-parse", "--git-dir");
    }
    catch (IOException _)
    {
      // Not a git repository or git not available — allow
      return Result.allow();
    }

    Path gitDir = Paths.get(gitDirPath);
    if (!gitDir.isAbsolute())
      gitDir = Paths.get(effectiveDirectory).resolve(gitDirPath).normalize();

    Path catBranchPointFile = gitDir.resolve(CatMetadata.BRANCH_POINT_FILE);
    if (!Files.exists(catBranchPointFile))
      return Result.allow();

    // The git dir for a worktree ends with "worktrees/<branch-name>"
    // e.g. /workspace/.git/worktrees/2.1-my-issue
    // Guard: verify structural precondition before deriving the expected branch
    Path gitDirParent = gitDir.getParent();
    if (gitDirParent == null || !"worktrees".equals(gitDirParent.getFileName().toString()))
      return Result.allow();
    String expectedBranch = gitDir.getFileName().toString();

    String currentBranch;
    try
    {
      currentBranch = GitCommands.getCurrentBranch(effectiveDirectory);
    }
    catch (IOException _)
    {
      // Cannot determine branch — allow with caution
      return Result.allow();
    }

    if (currentBranch.equals(expectedBranch))
      return Result.allow();

    return Result.block("""
      **BLOCKED: git commit on wrong branch**

      You are in a CAT worktree for issue branch: %s
      But the current branch is: %s

      This means you are committing to the wrong branch. This can happen when \
      a subagent checks out a different branch inside the worktree.

      **Recovery steps:**
      1. Switch back to the issue branch:
           git checkout %s
      2. Retry the commit.""".formatted(expectedBranch, currentBranch, expectedBranch));
  }

  /**
   * Extracts the effective working directory from a command by looking for the last {@code cd} directive.
   * <p>
   * When a command contains {@code cd /path && git commit}, the git command runs in {@code /path},
   * not in the Claude session's {@code cwd}. This method detects that pattern and returns the last
   * {@code cd} target so that git operations run in the correct directory.
   *
   * @param command the bash command string
   * @param fallback the fallback directory if no {@code cd} is found
   * @return the extracted directory, or {@code fallback} if no {@code cd} is present
   */
  private String extractCdDirectory(String command, String fallback)
  {
    Matcher cdMatcher = CD_PATTERN.matcher(command);
    String lastCdDir = fallback;
    while (cdMatcher.find())
    {
      String dir = cdMatcher.group(1).strip();
      if (!dir.isEmpty())
      {
        Path resolved = Paths.get(dir);
        if (!resolved.isAbsolute())
          resolved = Paths.get(lastCdDir).resolve(dir).normalize();
        lastCdDir = resolved.toString();
      }
    }
    return lastCdDir;
  }
}
