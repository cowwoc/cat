/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.bash.RemindGitSquash;
import org.testng.annotations.Test;

import java.io.IOException;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link RemindGitSquash}.
 */
public final class RemindGitSquashTest
{
  /**
   * Verifies that {@code git reset --soft} without a path argument is blocked.
   */
  @Test
  public void gitResetSoftWithoutPathIsBlocked()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      RemindGitSquash handler = new RemindGitSquash();
      String command = "git reset --soft HEAD~1";

      BashHandler.Result result = handler.check(TestUtils.bashInput(scope, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("/cat:git-squash");
    }
  }

  /**
   * Verifies that {@code git -C <path> reset --soft} with a path argument is blocked.
   * This form was previously not caught because the regex required "git" to immediately precede "reset".
   *
   * @throws IOException if an I/O error occurs creating the test scope
   */
  @Test
  public void gitResetSoftWithPathArgumentIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      RemindGitSquash handler = new RemindGitSquash();
      String worktreePath = scope.getCatWorkPath().resolve("worktrees").resolve("2.1-my-issue").toString();
      String command = "git -C " + worktreePath + " reset --soft v2.1";

      BashHandler.Result result = handler.check(TestUtils.bashInput(scope, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("/cat:git-squash");
    }
  }

  /**
   * Verifies that normal git commits are not blocked.
   */
  @Test
  public void normalGitCommitIsAllowed()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      RemindGitSquash handler = new RemindGitSquash();
      String command = "git commit -m \"feature: add something\"";

      BashHandler.Result result = handler.check(TestUtils.bashInput(scope, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that {@code git rebase -i} triggers a warning suggestion.
   */
  @Test
  public void gitRebaseInteractiveTriggersWarning()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      RemindGitSquash handler = new RemindGitSquash();
      String command = "git rebase -i HEAD~3";

      BashHandler.Result result = handler.check(TestUtils.bashInput(scope, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").contains("/cat:git-squash");
    }
  }
}
