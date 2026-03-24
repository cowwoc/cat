/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.bash.BlockLockManipulation;
import org.testng.annotations.Test;

import java.io.IOException;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockLockManipulation}.
 */
public final class BlockLockManipulationTest
{
  /**
   * Verifies that rm targeting a lock file is blocked and the error message mentions issue-lock.
   *
   * @throws IOException if an I/O error occurs creating the test scope
   */
  @Test
  public void rmLockFileIsBlockedWithIssueLockReference() throws IOException
  {
    String command = "rm -f /tmp/test-project/.cat/work/locks/task-123.lock";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockLockManipulation handler = new BlockLockManipulation();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("issue-lock force-release");
    }
  }

  /**
   * Verifies that rm targeting a lock file is blocked and the error message mentions /cat:cleanup for users.
   *
   * @throws IOException if an I/O error occurs creating the test scope
   */
  @Test
  public void rmLockFileErrorMentionsCleanupForUsers() throws IOException
  {
    String command = "rm /tmp/test-project/.cat/work/locks/my-issue.lock";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockLockManipulation handler = new BlockLockManipulation();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("/cat:cleanup");
    }
  }

  /**
   * Verifies that rm targeting the locks directory is blocked and the error message mentions issue-lock.
   *
   * @throws IOException if an I/O error occurs creating the test scope
   */
  @Test
  public void rmLocksDirIsBlockedWithIssueLockReference() throws IOException
  {
    String command = "rm -rf /tmp/test-project/.cat/work/locks/";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockLockManipulation handler = new BlockLockManipulation();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("issue-lock force-release");
    }
  }

  /**
   * Verifies that rm targeting the locks directory is blocked and the error message mentions /cat:cleanup
   * for users.
   *
   * @throws IOException if an I/O error occurs creating the test scope
   */
  @Test
  public void rmLocksDirErrorMentionsCleanupForUsers() throws IOException
  {
    String command = "rm -rf /tmp/test-project/.cat/work/locks/";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockLockManipulation handler = new BlockLockManipulation();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("/cat:cleanup");
    }
  }

  /**
   * Verifies that commands not targeting lock files are allowed.
   */
  @Test
  public void nonLockCommandIsAllowed()
  {
    String command = "rm -rf /tmp/some-other-file";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockLockManipulation handler = new BlockLockManipulation();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that rm targeting lock files with force flag is blocked.
   *
   * @throws IOException if an I/O error occurs creating the test scope
   */
  @Test
  public void rmWithForceFlagOnLockFileIsBlocked() throws IOException
  {
    String command = "rm -rf /tmp/test-project/.cat/work/locks/task-456.lock";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockLockManipulation handler = new BlockLockManipulation();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("issue-lock force-release");
    }
  }

  /**
   * Verifies that the locks directory block distinguishes skill-internal vs user-facing actions.
   *
   * @throws IOException if an I/O error occurs creating the test scope
   */
  @Test
  public void locksDirBlockDistinguishesSkillInternalFromUserFacing() throws IOException
  {
    String command = "rm -r /tmp/test-project/.cat/work/locks";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockLockManipulation handler = new BlockLockManipulation();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("issue-lock force-release");
      requireThat(result.reason(), "reason").contains("/cat:cleanup");
    }
  }

  /**
   * Verifies that rm targeting a lock file in the external CAT storage path is blocked.
   *
   * @throws IOException if an I/O error occurs creating the test scope
   */
  @Test
  public void rmLockFileInExternalStorageIsBlocked() throws IOException
  {
    String command = "rm -f /tmp/test-project/.cat/work/locks/task-123.lock";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockLockManipulation handler = new BlockLockManipulation();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("issue-lock force-release");
    }
  }

  /**
   * Verifies that rm targeting the locks directory in the external CAT storage path is blocked.
   *
   * @throws IOException if an I/O error occurs creating the test scope
   */
  @Test
  public void rmLocksDirInExternalStorageIsBlocked() throws IOException
  {
    String command = "rm -rf /tmp/test-project/.cat/work/locks/";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockLockManipulation handler = new BlockLockManipulation();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("issue-lock force-release");
    }
  }
}
