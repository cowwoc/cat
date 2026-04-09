/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.WorktreeLock;
import io.github.cowwoc.cat.claude.hook.util.GitCommands;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Warn when a {@code git commit} is issued in the main workspace while an active worktree lock exists
 * for the current session (A003/PATTERN-003).
 * <p>
 * When an agent is working on an issue with an active worktree, commits to the main workspace bypass
 * worktree isolation and pollute the main branch with issue-specific changes.
 * <p>
 * The handler emits a warning (not a block) when all of the following are true:
 * <ol>
 *   <li>The command contains a {@code git commit}</li>
 *   <li>The effective working directory is NOT inside a CAT worktree</li>
 *   <li>An active worktree lock exists for the current session</li>
 * </ol>
 * <p>
 * The handler does NOT warn when:
 * <ul>
 *   <li>The commit is issued from inside a CAT worktree directory</li>
 *   <li>No active worktree lock exists for the current session</li>
 * </ul>
 */
public final class WarnMainWorkspaceCommit implements BashHandler
{
  private static final Pattern COMMIT_PATTERN =
    Pattern.compile("(^|[;&|])\\s*git\\s+commit(?:\\s|$)");
  private static final Pattern CD_PATTERN =
    Pattern.compile("(?:^|[;&|])\\s*cd\\s+(['\"]?)([^'\";&|]*?)\\1(?:\\s|$|[;&|])");

  /**
   * Creates a new handler for warning about main workspace commits.
   */
  public WarnMainWorkspaceCommit()
  {
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

    if (!COMMIT_PATTERN.matcher(command).find())
      return Result.allow();

    // Check if there is an active worktree lock for this session (before expensive worktree detection)
    JsonMapper mapper = scope.getJsonMapper();
    String issueId;
    try
    {
      issueId = WorktreeLock.findIssueIdForSession(scope.getCatWorkPath(), mapper, sessionId);
    }
    catch (IOException _)
    {
      // Cannot determine lock status — allow to fail open
      return Result.allow();
    }

    if (issueId == null)
      return Result.allow();

    // Determine effective working directory (handle "cd <path> && git commit" pattern)
    String effectiveDirectory;
    if (command.contains("cd ") || command.contains("cd\t"))
      effectiveDirectory = extractCdDirectory(command, workingDirectory);
    else
      effectiveDirectory = workingDirectory;

    // Check if the effective directory is inside a CAT worktree
    if (isInsideCatWorktree(effectiveDirectory))
      return Result.allow();

    Path worktreePath = scope.getCatWorkPath().resolve("worktrees").resolve(issueId);
    String warning = """
      ⚠️ MAIN WORKSPACE COMMIT DETECTED (A003/PATTERN-003)

      You are committing from the main workspace: %s
      But an active worktree exists for issue: %s
      Worktree path: %s

      Commits to the main workspace bypass worktree isolation and pollute the main branch \
      with issue-specific changes.

      Fix: Route this commit to the active worktree:
        cd %s && git commit ...

      If this is intentional (e.g., a planning or config commit unrelated to the active issue), \
      proceed.""".formatted(effectiveDirectory, issueId, worktreePath, worktreePath);

    return Result.warn(warning);
  }

  /**
   * Checks whether the given directory is inside a CAT worktree.
   * <p>
   * A directory is inside a CAT worktree if its git directory ends with {@code worktrees/<branch-name>},
   * matching the structure created by {@code /cat:work-agent}.
   *
   * @param directory the directory to check
   * @return {@code true} if the directory is inside a CAT worktree
   */
  private static boolean isInsideCatWorktree(String directory)
  {
    String gitDirPath;
    try
    {
      gitDirPath = GitCommands.runGit(Path.of(directory), "rev-parse", "--git-dir");
    }
    catch (IOException _)
    {
      // Not a git repository or git not available — not a worktree
      return false;
    }

    Path gitDir = Paths.get(gitDirPath);
    if (!gitDir.isAbsolute())
      gitDir = Paths.get(directory).resolve(gitDirPath).normalize();

    return GitCommands.isCatWorktreeGitDir(gitDir);
  }

  /**
   * Extracts the effective working directory from a command by looking for the last {@code cd} directive.
   * <p>
   * When a command contains {@code cd /path && git commit}, the git command runs in {@code /path},
   * not in the Claude session's {@code cwd}. This method detects that pattern and returns the last
   * {@code cd} target so that git operations run in the correct directory.
   * <p>
   * Supports quoted paths (single and double quotes) and unquoted paths.
   *
   * @param command  the bash command string
   * @param fallback the fallback directory if no {@code cd} is found
   * @return the extracted directory, or {@code fallback} if no {@code cd} is present
   */
  private static String extractCdDirectory(String command, String fallback)
  {
    Matcher cdMatcher = CD_PATTERN.matcher(command);
    String lastCdDir = fallback;
    while (cdMatcher.find())
    {
      String dir = cdMatcher.group(2).strip();
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
