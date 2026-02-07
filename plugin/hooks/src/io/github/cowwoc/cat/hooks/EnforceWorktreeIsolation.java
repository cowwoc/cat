package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.util.GitCommands;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Hook: Enforce Worktree Isolation (M252).
 *
 * <p>Blocks Edit/Write operations on plugin/ files when on protected branches (v2.1, main).
 * All plugin development must happen in task-specific worktrees.</p>
 *
 * <p>TRIGGER: PreToolUse for Edit/Write</p>
 * <p>REGISTRATION: plugin/hooks/hooks.json (plugin hook)</p>
 */
public final class EnforceWorktreeIsolation
{
  private EnforceWorktreeIsolation()
  {
    // Utility class
  }

  /**
   * Entry point for the worktree isolation enforcement hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    try
    {
      HookInput input = HookInput.readFromStdin();

      String toolName = input.getToolName();
      if (!toolName.equals("Edit") && !toolName.equals("Write"))
      {
        HookOutput.empty();
        return;
      }

      JsonNode parameters = input.getObject("parameters");
      if (parameters == null)
      {
        HookOutput.empty();
        return;
      }

      JsonNode filePathNode = parameters.get("file_path");
      String filePath;
      if (filePathNode != null)
        filePath = filePathNode.asString();
      else
        filePath = "";

      if (filePath.isEmpty())
      {
        HookOutput.empty();
        return;
      }

      if (!isPluginFile(filePath))
      {
        HookOutput.empty();
        return;
      }

      JsonNode context = input.getObject("context");
      String cwd;
      if (context != null)
      {
        JsonNode cwdNode = context.get("cwd");
        if (cwdNode != null)
          cwd = cwdNode.asString();
        else
          cwd = System.getProperty("user.dir");
      }
      else
      {
        cwd = System.getProperty("user.dir");
      }

      String branch = GitCommands.getCurrentBranch(cwd);

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
        HookOutput.block(message);
        return;
      }

      HookOutput.empty();
    }
    catch (Exception e)
    {
      String errorMessage =
        "❌ Hook error: " + e.getMessage() + "\n" +
        "\n" +
        "Blocking as fail-safe. Please verify your working environment.";
      HookOutput.block(errorMessage);
    }
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
