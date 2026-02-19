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

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockLockManipulation}.
 */
public final class BlockLockManipulationTest
{
  /**
   * Verifies that rm targeting a lock file is blocked and the error message mentions issue-lock.sh.
   */
  @Test
  public void rmLockFileIsBlockedWithIssueLockReference()
  {
    BlockLockManipulation handler = new BlockLockManipulation();
    String command = "rm -f .claude/cat/locks/task-123.lock";

    BashHandler.Result result = handler.check(command, "/workspace", null, null, "session1");

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("issue-lock.sh force-release");
  }

  /**
   * Verifies that rm targeting a lock file is blocked and the error message mentions /cat:cleanup for users.
   */
  @Test
  public void rmLockFileErrorMentionsCleanupForUsers()
  {
    BlockLockManipulation handler = new BlockLockManipulation();
    String command = "rm .claude/cat/locks/my-issue.lock";

    BashHandler.Result result = handler.check(command, "/workspace", null, null, "session1");

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("/cat:cleanup");
  }

  /**
   * Verifies that rm targeting the locks directory is blocked and the error message mentions issue-lock.sh.
   */
  @Test
  public void rmLocksDirIsBlockedWithIssueLockReference()
  {
    BlockLockManipulation handler = new BlockLockManipulation();
    String command = "rm -rf .claude/cat/locks/";

    BashHandler.Result result = handler.check(command, "/workspace", null, null, "session1");

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("issue-lock.sh force-release");
  }

  /**
   * Verifies that rm targeting the locks directory is blocked and the error message mentions /cat:cleanup
   * for users.
   */
  @Test
  public void rmLocksDirErrorMentionsCleanupForUsers()
  {
    BlockLockManipulation handler = new BlockLockManipulation();
    String command = "rm -rf .claude/cat/locks/";

    BashHandler.Result result = handler.check(command, "/workspace", null, null, "session1");

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("/cat:cleanup");
  }

  /**
   * Verifies that commands not targeting lock files are allowed.
   */
  @Test
  public void nonLockCommandIsAllowed()
  {
    BlockLockManipulation handler = new BlockLockManipulation();
    String command = "rm -rf /tmp/some-other-file";

    BashHandler.Result result = handler.check(command, "/workspace", null, null, "session1");

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that rm targeting lock files with force flag is blocked.
   */
  @Test
  public void rmWithForceFlagOnLockFileIsBlocked()
  {
    BlockLockManipulation handler = new BlockLockManipulation();
    String command = "rm -rf .claude/cat/locks/task-456.lock";

    BashHandler.Result result = handler.check(command, "/workspace", null, null, "session1");

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("issue-lock.sh force-release");
  }

  /**
   * Verifies that the locks directory block distinguishes skill-internal vs user-facing actions.
   */
  @Test
  public void locksDirBlockDistinguishesSkillInternalFromUserFacing()
  {
    BlockLockManipulation handler = new BlockLockManipulation();
    String command = "rm -r .claude/cat/locks";

    BashHandler.Result result = handler.check(command, "/workspace", null, null, "session1");

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("issue-lock.sh force-release");
    requireThat(result.reason(), "reason").contains("/cat:cleanup");
  }
}
