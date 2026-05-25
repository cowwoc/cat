/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.bash.BlockDestructiveGitCommands;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockDestructiveGitCommands}.
 */
public final class BlockDestructiveGitCommandsTest
{
  /**
   * Verifies that force push is blocked for any branch.
   */
  @Test
  public void forcePushIsBlocked() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git push --force origin feature-x",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that short-form force push with global flags is blocked.
   */
  @Test
  public void forcePushWithGlobalFlagsIsBlocked() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git -C /tmp/repo push -f origin main",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that deleting a protected branch is blocked.
   */
  @Test
  public void protectedBranchForceDeleteIsBlocked() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git branch -D main",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that long-form protected branch force-delete is blocked.
   */
  @Test
  public void protectedBranchLongFormForceDeleteIs() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git branch --delete --force main",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that deleting a version branch is blocked.
   */
  @Test
  public void versionBranchForceDeleteIsBlocked() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git branch -D v2.1",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that full-tree checkout discard is blocked.
   */
  @Test
  public void checkoutDotIsBlocked() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git checkout -- .",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that checkout variants targeting the full tree are blocked.
   */
  @Test
  public void checkoutDotWithFlagsIsBlocked() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git checkout HEAD -- .",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that full-tree restore discard is blocked.
   */
  @Test
  public void restoreDotIsBlocked() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git --work-tree . restore --worktree --staged .",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that forced clean is blocked.
   */
  @Test
  public void forcedCleanIsBlocked() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git clean -fdx",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that destructive stash clear is blocked.
   */
  @Test
  public void stashClearIsBlocked() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git stash clear",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that destructive stash drop all is blocked.
   */
  @Test
  public void stashDropAllIsBlocked() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git stash drop --all",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that acknowledged commands bypass this guard.
   */
  @Test
  public void acknowledgedBypassIsAllowed() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git clean -fd # ACKNOWLEDGED",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that acknowledgment applies only to the matching command segment.
   */
  @Test
  public void acknowledgedDifferentSegmentDoesNot() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("echo '# ACKNOWLEDGED' && git clean -fd",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that quoted text does not count as an acknowledgment comment.
   */
  @Test
  public void quotedAcknowledgmentTextDoesNotBypass() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git clean -fd '# ACKNOWLEDGED'",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that double-quoted acknowledgment text does not count as a shell comment.
   */
  @Test
  public void doubleQuotedAcknowledgmentTextDoesNot() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git clean -fd \"# ACKNOWLEDGED\"",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that escaped hash does not count as an acknowledgment comment.
   */
  @Test
  public void escapedHashAcknowledgmentTextDoesNot() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("git clean -fd \\# ACKNOWLEDGED",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that prefixes before git do not bypass destructive command blocking.
   */
  @Test
  public void prefixedGitCommandIsBlocked() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook("env FOO=1 git -C /tmp/repo clean -fd",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that safe alternatives remain allowed.
   */
  @Test
  public void safeAlternativesAreAllowed() throws IOException
  {
    try (TestClaudeHook scope = TestUtils.bashHook(
      "git push --force-with-lease origin feature-x && git branch -d feature-x && git clean -n",
      Path.of("/workspace"), "session-1"))
    {
      BashHandler.Result result = new BlockDestructiveGitCommands(scope).check();

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }
}
