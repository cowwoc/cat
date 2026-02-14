/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block rm -rf and git worktree remove when shell cwd is inside target directory.
 * <p>
 * M464, M491: Prevent shell session corruption from deleting current working directory.
 */
public final class BlockUnsafeRemoval implements BashHandler
{
  private static final Pattern RM_PATTERN =
    Pattern.compile("\\brm\\s+(-[^\\s]*r[^\\s]*\\s+)?([^\\s;&|]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern WORKTREE_REMOVE_PATTERN =
    Pattern.compile("\\bgit\\s+worktree\\s+remove\\s+([^\\s;&|]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CD_PATTERN =
    Pattern.compile("^cd\\s+['\"]?([^'\";&|]+)['\"]?");

  /**
   * Creates a new handler for blocking unsafe directory removal.
   */
  public BlockUnsafeRemoval()
  {
    // Handler class
  }

  @Override
  public Result check(String command, JsonNode toolInput, JsonNode toolResult, String sessionId)
  {
    String commandLower = command.toLowerCase();

    // Check rm -rf commands
    if (commandLower.contains("rm") && (commandLower.contains("-r") || commandLower.contains("-rf")))
    {
      Result rmResult = checkRmCommand(command);
      if (rmResult != null)
        return rmResult;
    }

    // Check git worktree remove commands
    if (commandLower.contains("git") && commandLower.contains("worktree") &&
        commandLower.contains("remove"))
    {
      Result worktreeResult = checkWorktreeRemove(command);
      if (worktreeResult != null)
        return worktreeResult;
    }

    return Result.allow();
  }

  /**
   * Checks if an rm command would delete the shell's current working directory.
   *
   * @param command the bash command
   * @return a block result if unsafe removal detected, null otherwise
   */
  private Result checkRmCommand(String command)
  {
    String cwd = extractCwd(command);
    Matcher matcher = RM_PATTERN.matcher(command);

    while (matcher.find())
    {
      String target = matcher.group(2);
      if (target == null || target.isEmpty())
        continue;

      // Resolve target path
      Path targetPath = resolvePath(target, cwd);
      Path cwdPath = Paths.get(cwd);

      // Check if cwd is inside target or equals target
      if (isInsideOrEqual(cwdPath, targetPath))
      {
        return Result.block(String.format("""
          UNSAFE DIRECTORY REMOVAL BLOCKED (M464, M491)

          Attempted: rm -rf %s
          Problem:   Shell's current directory is inside target directory
          Current:   %s

          WHY THIS IS BLOCKED:
          - Deleting your current directory corrupts the shell session
          - All subsequent Bash commands will fail with "Exit code 1"
          - Recovery requires restarting Claude Code entirely

          WHAT TO DO:
          1. Change directory first: cd /workspace (or parent directory)
          2. Then delete: rm -rf %s

          See: /cat:safe-rm for detailed safe removal protocol
          """, target, cwd, target));
      }
    }

    return null;
  }

  /**
   * Checks if a git worktree remove command would delete the shell's current working directory.
   *
   * @param command the bash command
   * @return a block result if unsafe removal detected, null otherwise
   */
  private Result checkWorktreeRemove(String command)
  {
    String cwd = extractCwd(command);
    Matcher matcher = WORKTREE_REMOVE_PATTERN.matcher(command);

    while (matcher.find())
    {
      String target = matcher.group(1);
      if (target == null || target.isEmpty())
        continue;

      // Resolve target path
      Path targetPath = resolvePath(target, cwd);
      Path cwdPath = Paths.get(cwd);

      // Check if cwd is inside target or equals target
      if (isInsideOrEqual(cwdPath, targetPath))
      {
        return Result.block(String.format("""
          UNSAFE WORKTREE REMOVAL BLOCKED (M464, M491)

          Attempted: git worktree remove %s
          Problem:   Shell's current directory is inside worktree being removed
          Current:   %s

          WHY THIS IS BLOCKED:
          - Removing a worktree while inside it corrupts the shell session
          - All subsequent Bash commands will fail with "Exit code 1"
          - Recovery requires restarting Claude Code entirely

          WHAT TO DO:
          1. Change directory first: cd /workspace
          2. Then remove worktree: git worktree remove %s --force

          See: /cat:safe-rm for detailed safe removal protocol
          """, target, cwd, target));
      }
    }

    return null;
  }

  /**
   * Extracts the effective cwd from a command (handles inline cd).
   *
   * @param command the bash command
   * @return the effective cwd
   */
  private String extractCwd(String command)
  {
    Matcher cdMatcher = CD_PATTERN.matcher(command);
    if (cdMatcher.find())
    {
      String target = cdMatcher.group(1).strip();
      Path targetPath = Paths.get(target);
      if (targetPath.isAbsolute())
        return target;
      return Paths.get(System.getProperty("user.dir"), target).normalize().toString();
    }
    return System.getProperty("user.dir");
  }

  /**
   * Resolves a path (absolute or relative) against a base directory.
   *
   * @param path the path to resolve
   * @param base the base directory for relative paths
   * @return the resolved absolute path
   */
  private Path resolvePath(String path, String base)
  {
    path = path.replaceAll("['\"]", "").strip();
    Path p = Paths.get(path);
    if (p.isAbsolute())
      return p.normalize();
    return Paths.get(base, path).normalize();
  }

  /**
   * Checks if cwdPath is inside targetPath or equal to it.
   *
   * @param cwdPath the current working directory path
   * @param targetPath the target deletion path
   * @return true if cwd would be deleted
   */
  private boolean isInsideOrEqual(Path cwdPath, Path targetPath)
  {
    try
    {
      // Normalize and resolve to real paths
      Path realCwd = cwdPath.toRealPath();
      Path realTarget = targetPath.toRealPath();

      // Check equality or if cwd starts with target
      return realCwd.equals(realTarget) || realCwd.startsWith(realTarget);
    }
    catch (IOException _)
    {
      // If paths don't exist, fall back to string comparison
      Path normalCwd = cwdPath.normalize();
      Path normalTarget = targetPath.normalize();
      return normalCwd.equals(normalTarget) || normalCwd.startsWith(normalTarget);
    }
  }
}
