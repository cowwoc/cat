/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates that bugfix/feature commits in CAT worktrees include STATE.md changes.
 * <p>
 * Blocks commits when STATE.md is not staged, and warns when STATE.md is staged but does not
 * contain a "completed" status.
 */
public final class VerifyStateInCommit implements BashHandler
{
  private static final Pattern IMPLEMENTATION_COMMIT_PATTERN = Pattern.compile(
    "git\\s+commit(?!.*--amend).*-m\\s+.*?(bugfix|feature):", Pattern.DOTALL);
  private static final Pattern CD_PATTERN = Pattern.compile("(?:^|[;&|])\\s*cd\\s+([^;&|\\s]+)");

  /**
   * Creates a new handler for verifying STATE.md in commits.
   */
  public VerifyStateInCommit()
  {
    // Handler class
  }

  @Override
  public Result check(String command, String workingDirectory, JsonNode toolInput, JsonNode toolResult,
    String sessionId)
  {
    requireThat(command, "command").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    if (!IMPLEMENTATION_COMMIT_PATTERN.matcher(command).find())
      return Result.allow();

    // If the command contains a "cd <path>", use that as the effective working directory.
    // This handles patterns like "cd /path/to/worktree && git commit ..." where the Claude
    // session cwd differs from where git is actually running.
    String effectiveDirectory = extractCdDirectory(command, workingDirectory);

    if (!isInCatWorktree(effectiveDirectory))
      return Result.allow();

    try
    {
      List<String> stagedFiles = getStagedFilesInDirectory(effectiveDirectory);
      boolean stateMdStaged = stagedFiles.stream().anyMatch(f -> f.endsWith("STATE.md"));

      if (!stateMdStaged)
      {
        return Result.block("""
          **BLOCKED: STATE.md not included in bugfix/feature commit**

          When committing bugfix: or feature: changes in a CAT worktree,
          STATE.md must be updated and staged in the same commit.

          Fix: Update STATE.md to reflect completion status, then stage it:
            git add .claude/cat/issues/**/STATE.md""");
      }

      String stateMdContent = readStagedStateMd(stagedFiles, effectiveDirectory);
      if (!stateMdContent.isEmpty() && !stateMdContent.contains("completed"))
      {
        return Result.warn(
          "STATE.md is staged but does not contain 'completed' status. " +
          "Verify the issue status is correct before committing.");
      }

      return Result.allow();
    }
    catch (IOException _)
    {
      return Result.allow();
    }
  }

  /**
   * Extracts the effective working directory from a command, checking for a leading {@code cd} that
   * changes directory before running git.
   * <p>
   * When a command contains {@code cd /path && git commit}, the git command runs in {@code /path},
   * not in the Claude session's {@code cwd}. This method detects that pattern and returns the
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
        lastCdDir = Paths.get(dir).normalize().toString();
    }
    return lastCdDir;
  }

  /**
   * Checks whether the working directory is inside a CAT worktree.
   *
   * @param workingDirectory the working directory path
   * @return {@code true} if this is a CAT worktree
   */
  private boolean isInCatWorktree(String workingDirectory)
  {
    Path workDir = Path.of(workingDirectory);

    // Check for .git/cat-base file (worktree marker)
    Path gitCatBase = workDir.resolve(".git").resolve("cat-base");
    if (Files.exists(gitCatBase))
      return true;

    // Check for .claude/cat directory
    Path claudeCat = workDir.resolve(".claude").resolve("cat");
    return Files.isDirectory(claudeCat);
  }

  /**
   * Gets the list of staged files in the specified directory.
   *
   * @param directory the directory to run the git command in
   * @return list of staged file paths
   * @throws IOException if the git command fails
   */
  private List<String> getStagedFilesInDirectory(String directory) throws IOException
  {
    String output = GitCommands.runGitCommandInDirectory(directory, "diff", "--cached", "--name-only");
    if (output.isEmpty())
      return List.of();
    return Arrays.asList(output.split("\n"));
  }

  /**
   * Reads the content of the staged STATE.md file.
   *
   * @param stagedFiles the list of staged file paths
   * @param workingDirectory the working directory
   * @return the content of STATE.md, or empty string if it cannot be read
   */
  private String readStagedStateMd(List<String> stagedFiles, String workingDirectory)
  {
    for (String file : stagedFiles)
    {
      if (file.endsWith("STATE.md"))
      {
        Path stateMdPath = Path.of(workingDirectory).resolve(file);
        try
        {
          return Files.readString(stateMdPath);
        }
        catch (IOException _)
        {
          return "";
        }
      }
    }
    return "";
  }
}
