/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.bash.BlockLockManipulation;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockLockManipulation handler = new BlockLockManipulation();
      String locksPath = scope.getCatWorkPath().resolve("locks").resolve("task-123.lock").toString();
      String command = "rm -f " + locksPath;

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockLockManipulation handler = new BlockLockManipulation();
      String locksPath = scope.getCatWorkPath().resolve("locks").resolve("my-issue.lock").toString();
      String command = "rm " + locksPath;

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockLockManipulation handler = new BlockLockManipulation();
      String locksDir = scope.getCatWorkPath().resolve("locks").toString() + "/";
      String command = "rm -rf " + locksDir;

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockLockManipulation handler = new BlockLockManipulation();
      String locksDir = scope.getCatWorkPath().resolve("locks").toString() + "/";
      String command = "rm -rf " + locksDir;

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockLockManipulation handler = new BlockLockManipulation();
      String command = "rm -rf /tmp/some-other-file";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockLockManipulation handler = new BlockLockManipulation();
      String locksPath = scope.getCatWorkPath().resolve("locks").resolve("task-456.lock").toString();
      String command = "rm -rf " + locksPath;

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockLockManipulation handler = new BlockLockManipulation();
      String locksDir = scope.getCatWorkPath().resolve("locks").toString();
      String command = "rm -r " + locksDir;

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockLockManipulation handler = new BlockLockManipulation();
      String locksPath = scope.getCatWorkPath().resolve("locks").resolve("task-123.lock").toString();
      String command = "rm -f " + locksPath;

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockLockManipulation handler = new BlockLockManipulation();
      String locksDir = scope.getCatWorkPath().resolve("locks").toString() + "/";
      String command = "rm -rf " + locksDir;

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("issue-lock force-release");
    }
  }
}
