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
import io.github.cowwoc.cat.tool.util.WorktreeContext;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Validate dangerous git operations.
 * <p>
 * Warns or blocks commands like git push --force, reset --hard, etc.
 */
public final class ValidateGitOperations implements BashHandler
{
  private static final String RESET_HARD_PREFIX = "git reset --hard";

  private final ClaudeHook scope;

  /**
   * Creates a new handler for validating dangerous git operations.
   *
   * @param scope the JVM scope providing access to shared resources
   * @throws NullPointerException if {@code scope} is null
   */
  public ValidateGitOperations(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check()
  {
    for (GitCommandNormalizer.NormalizedGitCommand normalizedCommand :
      GitCommandNormalizer.extractGitCommands(scope.getCommand()))
    {
      String command = normalizedCommand.normalizedCommand();
      // Block: git reset --hard without explicit acknowledgment
      if (isResetHardCommand(command))
      {
        if (isResetHardAllowed(normalizedCommand.rawSegment()))
          continue;
        return Result.block("""
          **BLOCKED: git reset --hard can lose uncommitted work**

          This command discards all uncommitted changes permanently.

          If you're sure:
          - In a worktree: Use /cat:git-rebase skill
          - Main worktree: Add # ACKNOWLEDGED comment

          Consider: git stash to save work before reset.""");
      }
    }
    return Result.allow();
  }

  /**
   * Returns true if command is a reset-hard invocation.
   *
   * @param command normalized git command
   * @return true if reset-hard detected
   */
  private boolean isResetHardCommand(String command)
  {
    return command.startsWith(RESET_HARD_PREFIX);
  }

  /**
   * Returns true if a reset-hard command is explicitly acknowledged or is scoped to the active
   * CAT issue worktree.
   *
   * @param rawSegment the raw command segment containing reset-hard
   * @return true if reset-hard is allowed
   */
  private boolean isResetHardAllowed(String rawSegment)
  {
    if (GitCommandNormalizer.containsAcknowledgedComment(rawSegment))
      return true;
    Optional<WorktreeContext> context = WorktreeContext.forSession(scope.getCatWorkPath(),
      scope.getProjectPath(), scope.getJsonMapper(), scope.getSessionId());
    if (context.isEmpty())
      return false;

    Path worktreePath = context.get().absoluteWorktreePath();
    Path workingDirectory = Path.of(scope.getStringInput("cwd")).toAbsolutePath().normalize();
    GitCommandScopeResolver.GitScopeTarget target =
      GitCommandScopeResolver.resolve(rawSegment, workingDirectory);
    if (target.overridesScope() && !target.workingTree().startsWith(worktreePath))
      return false;
    if (target.overridesScope() && target.gitDirectory() != null &&
      !target.gitDirectory().startsWith(worktreePath))
      return false;
    return target.workingTree().startsWith(worktreePath);
  }
}
