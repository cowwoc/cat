package io.github.cowwoc.cat.hooks.write;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Enforce plugin file isolation on protected branches (M252).
 * <p>
 * Blocks Edit/Write operations on plugin/ files when on protected branches (v2.1, main).
 * All plugin development must happen in task-specific worktrees.
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
   * @throws NullPointerException if toolInput or sessionId is null
   * @throws IllegalArgumentException if sessionId is blank
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

    if (!isPluginFile(filePath))
      return FileWriteHandler.Result.allow();

    String branch = GitCommands.getCurrentBranch();

    if (isProtectedBranch(branch))
    {
      String message =
        "❌ BLOCKED: Cannot edit plugin files on protected branch '" + branch + "'.\n" +
        "\n" +
        "**Worktree Isolation Required (M252)**\n" +
        "\n" +
        "File: " + filePath + "\n" +
        "Branch: " + branch + "\n" +
        "\n" +
        "**Solution:**\n" +
        "1. Create task: `/cat:add <task-description>`\n" +
        "2. Work in isolated worktree: `/cat:work`\n" +
        "3. Make edits in task worktree (task branches like 'v2.1-task-name')\n" +
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
   * Check if branch is protected (v2.1, main, v*.*).
   *
   * @param branch the branch name
   * @return true if protected, false otherwise
   */
  private static boolean isProtectedBranch(String branch)
  {
    if (branch.isEmpty())
      return false;

    if (branch.equals("main") || branch.equals("v2.1"))
      return true;

    return branch.startsWith("v") && branch.contains(".");
  }

  /**
   * Check if file is under plugin/ directory.
   *
   * @param filePath the file path to check
   * @return true if under plugin/, false otherwise
   */
  private static boolean isPluginFile(String filePath)
  {
    Path path = Paths.get(filePath);
    for (Path part : path)
    {
      if (part.toString().equals("plugin"))
        return true;
    }
    return false;
  }
}
