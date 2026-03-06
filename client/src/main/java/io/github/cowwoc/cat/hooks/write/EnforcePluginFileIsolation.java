/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.write;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Enforce source file isolation to issue worktrees.
 * <p>
 * Blocks Edit/Write operations on plugin/ and client/ files when not in an issue worktree.
 * A CAT issue worktree is identified by its git directory ending with {@code worktrees/<branch-name>},
 * matching the structure created by {@code /cat:work}. All source development must happen in issue
 * worktrees.
 */
public final class EnforcePluginFileIsolation implements FileWriteHandler
{
  /**
   * Creates a new EnforcePluginFileIsolation instance.
   */
  public EnforcePluginFileIsolation()
  {
  }

  /**
   * Check if the edit should be blocked due to plugin file isolation violation.
   *
   * @param toolInput the tool input JSON
   * @param sessionId the session ID
   * @return the check result
   * @throws NullPointerException if {@code toolInput} or {@code sessionId} is null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   */
  @Override
  public FileWriteHandler.Result check(JsonNode toolInput, String sessionId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    JsonNode filePathNode = toolInput.get("file_path");
    String filePath;
    if (filePathNode != null)
      filePath = filePathNode.asString();
    else
      filePath = "";

    if (filePath.isEmpty())
      return FileWriteHandler.Result.allow();

    if (!isProtectedSourceFile(filePath))
      return FileWriteHandler.Result.allow();

    String directory = findExistingAncestor(filePath);

    if (!isIssueWorktree(directory))
    {
      String message =
        "❌ BLOCKED: Cannot edit source files outside of an issue worktree.\n" +
        "\n" +
        "**Worktree Isolation Required**\n" +
        "\n" +
        "File: " + filePath + "\n" +
        "\n" +
        "**Solution:**\n" +
        "1. Create task: `/cat:add <task-description>`\n" +
        "2. Work in isolated worktree: `/cat:work`\n" +
        "3. Make edits in the issue worktree\n" +
        "\n" +
        "**Why this matters:**\n" +
        "- Keeps base branch stable\n" +
        "- Enables clean rollback\n" +
        "- Allows parallel work on multiple tasks\n" +
        "\n" +
        "If this is truly maintenance work on the base branch:\n" +
        "1. Create an issue for it\n" +
        "2. Use /cat:work to create proper worktree\n" +
        "3. Make changes in isolated environment\n";
      return FileWriteHandler.Result.block(message);
    }

    return FileWriteHandler.Result.allow();
  }

  /**
   * Check if the given directory is an issue worktree created by {@code /cat:work}.
   * <p>
   * A CAT issue worktree has a git directory ending with {@code worktrees/<branch-name>}
   * (the path returned by {@code git rev-parse --git-dir}).
   * <p>
   * This check is fail-safe: if the git command fails, the edit is blocked.
   *
   * @param directory the directory to check
   * @return true if the directory is an issue worktree, false otherwise
   */
  private static boolean isIssueWorktree(String directory)
  {
    try
    {
      String gitDirPath = GitCommands.runGit(Path.of(directory), "rev-parse", "--git-dir");
      if (gitDirPath.isEmpty())
        return false;
      Path gitDir = Paths.get(gitDirPath);
      if (!gitDir.isAbsolute())
        gitDir = Paths.get(directory).resolve(gitDirPath).normalize();
      return GitCommands.isCatWorktreeGitDir(gitDir);
    }
    catch (IOException _)
    {
      return false;
    }
  }

  /**
   * Find the first existing ancestor directory of a file path.
   *
   * @param filePath the file path to check
   * @return the first existing ancestor directory, or the file path itself if none found
   */
  private static String findExistingAncestor(String filePath)
  {
    Path path = Paths.get(filePath);
    Path current = path.getParent();
    while (current != null)
    {
      if (current.toFile().isDirectory())
        return current.toString();
      current = current.getParent();
    }
    return filePath;
  }

  /**
   * Check if file is under plugin/ or client/ directory.
   *
   * @param filePath the file path to check
   * @return true if under plugin/ or client/, false otherwise
   */
  private static boolean isProtectedSourceFile(String filePath)
  {
    Path path = Paths.get(filePath);
    for (Path part : path)
    {
      String name = part.toString();
      if (name.equals("plugin") || name.equals("client"))
        return true;
    }
    return false;
  }
}
