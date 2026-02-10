package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import tools.jackson.databind.JsonNode;

import java.util.regex.Pattern;

/**
 * Block cd into worktree directories to prevent shell corruption.
 * <p>
 * When a shell is inside a worktree directory and that worktree is removed
 * (git worktree remove), the shell loses its cwd reference and all subsequent
 * commands fail with exit code 1.
 */
public final class BlockWorktreeCd implements BashHandler
{
  private static final Pattern WORKTREE_CD_PATTERN =
    Pattern.compile("cd\\s+[\"']?.*\\.claude/cat/worktrees/");

  /**
   * Creates a new handler for blocking cd into worktree directories.
   */
  public BlockWorktreeCd()
  {
  }

  @Override
  public Result check(String command, JsonNode toolInput, JsonNode toolResult, String sessionId)
  {
    if (WORKTREE_CD_PATTERN.matcher(command).find())
    {
      return Result.block("""
        🚨 CD INTO WORKTREE BLOCKED

        ❌ Attempted: cd into /workspace/.claude/cat/worktrees/*
        ✅ Correct:   Use git -C <worktree-path> for operations

        WHY THIS IS BLOCKED:
        • If your shell is inside a worktree when it gets removed, the shell corrupts
        • All subsequent commands fail with exit code 1
        • This affects both the current agent AND parent agent sessions

        WHAT TO DO INSTEAD:
        • Use: git -C /workspace/.claude/cat/worktrees/<name> <command>
        • Or: Delegate to subagent which has its own shell session

        CONTEXT: Worktrees are temporary. When removed (git worktree remove), any shell
        sitting inside the directory loses its working directory reference.""");
    }

    return Result.allow();
  }
}
