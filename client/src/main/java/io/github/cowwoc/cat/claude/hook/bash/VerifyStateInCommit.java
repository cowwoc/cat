/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.Config;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.IssueStatus;
import io.github.cowwoc.cat.claude.hook.util.GitCommands;
import io.github.cowwoc.cat.claude.hook.util.IssueDiscovery;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates that bugfix/feature commits in CAT worktrees include index.json changes.
 * <p>
 * Blocks commits when index.json is not staged, and warns when index.json is staged but does not
 * contain a "closed" status.
 */
public final class VerifyStateInCommit implements BashHandler
{
  private static final Pattern IMPLEMENTATION_COMMIT_PATTERN = Pattern.compile(
    "git\\s+commit(?!-tree)(?!.*--amend).*-m\\s+.*?(bugfix|feature):", Pattern.DOTALL);
  private static final Pattern CD_PATTERN = Pattern.compile("(?:^|[;&|])\\s*cd\\s+([^;&|\\s]+)");

  /**
   * Creates a new handler for verifying index.json in commits.
   */
  public VerifyStateInCommit()
  {
    // Handler class
  }

  @Override
  public Result check(ClaudeHook scope)
  {
    String command = scope.getCommand();
    String workingDirectory = scope.getString("cwd");
    String sessionId = scope.getSessionId();
    requireThat(command, "command").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
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
      boolean indexJsonStaged = stagedFiles.stream().anyMatch(f -> f.endsWith("index.json"));

      if (!indexJsonStaged)
      {
        String specificPath = deriveIndexJsonPath(effectiveDirectory);
        String addTarget;
        if (specificPath == null)
        {
          // Branch does not follow CAT naming convention (e.g., manually created branch),
          // so we fall back to a glob pattern to cover all possible issue index files.
          addTarget = Config.CAT_DIR_NAME + "/issues/**/index.json";
        }
        else
          addTarget = specificPath;
        return Result.block(
          "**BLOCKED: index.json not included in bugfix/feature commit**\n" +
          "\n" +
          "When committing bugfix: or feature: changes in a CAT worktree,\n" +
          "index.json must be updated and staged in the same commit.\n" +
          "\n" +
          "Fix: Update index.json to reflect completion status, then stage it:\n" +
          "  git add " + addTarget);
      }

      String indexJsonContent = readStagedIndexJson(stagedFiles, effectiveDirectory);
      if (!indexJsonContent.isEmpty())
      {
        boolean isClosed = isClosedStatus(indexJsonContent, scope.getMapper());
        if (!isClosed)
        {
          return Result.warn(
            "index.json is staged but does not contain 'closed' status. " +
            "Verify the issue status is correct before committing.");
        }
      }

      return Result.allow();
    }
    catch (IOException e)
    {
      return Result.warn(
        "index.json is staged but does not contain 'closed' status. " +
        "Verify the issue status is correct before committing. (Error: " + e.getMessage() + ")");
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
   * Derives the specific index.json path for the current issue from the worktree branch name.
   * <p>
   * The branch name encodes the version and issue name in a structured format. This method
   * parses the branch name and constructs the corresponding issue directory path.
   *
   * @param directory the working directory of the worktree
   * @return the specific index.json path, or {@code null} if the branch name cannot be parsed
   */
  private String deriveIndexJsonPath(String directory)
  {
    try
    {
      String branch = GitCommands.runGit(Path.of(directory), "rev-parse", "--abbrev-ref", "HEAD").strip();
      return IssueDiscovery.branchToIndexJsonPath(branch);
    }
    catch (IOException _)
    {
      return null;
    }
  }

  /**
   * Checks whether the working directory is inside a CAT worktree.
   * <p>
   * A CAT issue worktree has a git directory ending with {@code worktrees/<branch-name>}
   * (the path returned by {@code git rev-parse --git-dir}). The {@code .cat} directory
   * exists in the main workspace too, so it cannot be used to detect worktrees.
   *
   * @param workingDirectory the working directory path
   * @return {@code true} if this is a CAT worktree
   */
  private boolean isInCatWorktree(String workingDirectory)
  {
    try
    {
      String gitDirPath = GitCommands.runGit(Path.of(workingDirectory), "rev-parse", "--git-dir");
      if (gitDirPath.isEmpty())
        return false;
      Path gitDir = Paths.get(gitDirPath);
      if (!gitDir.isAbsolute())
        gitDir = Paths.get(workingDirectory).resolve(gitDirPath).normalize();
      return GitCommands.isCatWorktreeGitDir(gitDir);
    }
    catch (IOException _)
    {
      return false;
    }
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
    String output = GitCommands.runGit(Path.of(directory), "diff", "--cached", "--name-only");
    if (output.isEmpty())
      return List.of();
    return Arrays.asList(output.split("\n"));
  }

  /**
   * Reads the content of the staged index.json file.
   *
   * @param stagedFiles the list of staged file paths
   * @param workingDirectory the working directory
   * @return the content of index.json, or empty string if it cannot be read
   */
  private String readStagedIndexJson(List<String> stagedFiles, String workingDirectory)
  {
    for (String file : stagedFiles)
    {
      if (file.endsWith("index.json"))
      {
        Path indexJsonPath = Path.of(workingDirectory).resolve(file);
        try
        {
          return Files.readString(indexJsonPath);
        }
        catch (IOException _)
        {
          return "";
        }
      }
    }
    return "";
  }

  /**
   * Returns whether the index.json content represents a closed issue.
   *
   * @param content the JSON content of the index.json file
   * @param mapper the JSON mapper to use for parsing
   * @return {@code true} if the status field is "closed", {@code false} otherwise
   * @throws IOException if the JSON cannot be parsed or is missing the required "status" field
   */
  private boolean isClosedStatus(String content, JsonMapper mapper) throws IOException
  {
    try
    {
      JsonNode root = mapper.readTree(content);
      JsonNode statusNode = root.get("status");
      if (statusNode == null || !statusNode.isString())
        throw new IOException("index.json is missing required 'status' field or it is not a string");
      return IssueStatus.fromString(statusNode.asString()) == IssueStatus.CLOSED;
    }
    catch (JacksonException e)
    {
      throw new IOException("Failed to parse index.json: " + e.getMessage(), e);
    }
  }
}
