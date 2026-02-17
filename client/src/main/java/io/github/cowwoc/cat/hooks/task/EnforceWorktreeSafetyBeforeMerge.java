/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.task;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.TaskHandler;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Block work-merge subagent spawn when the shell's CWD is inside the worktree being deleted.
 * <p>
 * When the main agent's shell CWD is inside a worktree and a merge subagent removes that worktree,
 * the shell's CWD becomes invalid. All subsequent Bash commands fail with exit code 1 until Claude
 * Code is restarted.
 * <p>
 * This handler blocks Task spawning of cat:work-merge when the agent's CWD is inside a worktree
 * directory. The agent must first {@code cd /workspace} before spawning the merge subagent.
 */
public final class EnforceWorktreeSafetyBeforeMerge implements TaskHandler
{
  /**
   * Creates a new EnforceWorktreeSafetyBeforeMerge handler.
   */
  public EnforceWorktreeSafetyBeforeMerge()
  {
  }

  @Override
  public Result check(JsonNode toolInput, String sessionId, String cwd)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(cwd, "cwd").isNotNull();

    JsonNode subagentTypeNode = toolInput.get("subagent_type");
    String subagentType;
    if (subagentTypeNode != null)
      subagentType = subagentTypeNode.asString();
    else
      subagentType = "";

    if (!subagentType.equals("cat:work-merge"))
      return Result.allow();

    if (cwd.isEmpty())
      return Result.allow();

    if (isInsideWorktree(cwd))
    {
      String reason = """
        BLOCKED: Shell CWD is inside a worktree that will be deleted during merge.

        Current CWD: %s

        The merge subagent removes the worktree after merging. If the parent shell's CWD is inside
        the worktree when it is deleted, all subsequent Bash commands fail with exit code 1 until
        Claude Code is restarted.

        Required fix: Run the following command BEFORE spawning the merge subagent:
          cd /workspace

        Then retry spawning the merge subagent.""".formatted(cwd);
      return Result.block(reason);
    }

    return Result.allow();
  }

  /**
   * Check if the given path is inside a CAT worktree directory.
   * <p>
   * A worktree is identified by its path being under a {@code worktrees/} parent directory.
   *
   * @param cwd the current working directory path
   * @return true if the path is inside a worktree
   */
  private boolean isInsideWorktree(String cwd)
  {
    Path path = Paths.get(cwd);
    Path parent = path.getParent();
    while (parent != null)
    {
      Path fileName = parent.getFileName();
      if (fileName != null && "worktrees".equals(fileName.toString()))
        return true;
      parent = parent.getParent();
    }
    return false;
  }
}
