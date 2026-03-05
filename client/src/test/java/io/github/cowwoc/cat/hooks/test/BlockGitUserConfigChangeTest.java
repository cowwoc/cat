/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.bash.BlockGitUserConfigChange;
import org.testng.annotations.Test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockGitUserConfigChange}.
 */
public final class BlockGitUserConfigChangeTest
{
  /**
   * Verifies that setting git user.name is blocked.
   */
  @Test
  public void setUserNameIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config user.name \"Alice\"";

    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("user.name");
  }

  /**
   * Verifies that setting git user.email is blocked.
   */
  @Test
  public void setUserEmailIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config user.email \"alice@example.com\"";

    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("user.email");
  }

  /**
   * Verifies that the block message instructs the user to request the change explicitly.
   */
  @Test
  public void blockMessageMentionsExplicitUserRequest()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config user.name \"Bob\"";

    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("explicitly");
  }

  /**
   * Verifies that --global scope for user.name is also blocked.
   */
  @Test
  public void setGlobalUserNameIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config --global user.name \"Carol\"";

    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("user.name");
  }

  /**
   * Verifies that --global scope for user.email is also blocked.
   */
  @Test
  public void setGlobalUserEmailIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config --global user.email \"carol@example.com\"";

    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("user.email");
  }

  /**
   * Verifies that reading git user.name (no value argument) is allowed.
   */
  @Test
  public void readUserNameIsAllowed()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config user.name";

    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that reading git user.email (no value argument) is allowed.
   */
  @Test
  public void readUserEmailIsAllowed()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config user.email";

    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that git config commands not targeting user identity are allowed.
   */
  @Test
  public void nonUserConfigIsAllowed()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config receive.denyCurrentBranch updateInstead";

    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that unrelated commands are allowed.
   */
  @Test
  public void nonGitConfigCommandIsAllowed()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git commit -m \"feature: add something\"";

    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that --get for user.name (read-only) is allowed.
   */
  @Test
  public void getUserNameIsAllowed()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config --get user.name";

    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that --unset for user.name is blocked (it modifies identity config).
   */
  @Test
  public void unsetUserNameIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config --unset user.name";

    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));

    requireThat(result.blocked(), "blocked").isTrue();
  }

  /**
   * Verifies that --unset-all for user.name is blocked.
   */
  @Test
  public void unsetAllUserNameIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config --unset-all user.name";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/tmp", "session-1"));
    requireThat(result.blocked(), "blocked").isTrue();
  }

  /**
   * Verifies that --unset-all for user.email is blocked.
   */
  @Test
  public void unsetAllUserEmailIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config --unset-all user.email";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/tmp", "session-1"));
    requireThat(result.blocked(), "blocked").isTrue();
  }

  /**
   * Verifies that --remove-section user is blocked.
   */
  @Test
  public void removeUserSectionIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config --remove-section user";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/tmp", "session-1"));
    requireThat(result.blocked(), "blocked").isTrue();
  }

  /**
   * Verifies that git -c user.name=attacker is blocked.
   */
  @Test
  public void inlineUserNameViaMinusCIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git -c user.name=attacker commit -m 'test'";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/tmp", "session-1"));
    requireThat(result.blocked(), "blocked").isTrue();
  }

  /**
   * Verifies that git -c user.email=attacker@evil.com is blocked.
   */
  @Test
  public void inlineUserEmailViaMinusCIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git -c user.email=attacker@evil.com commit -m 'test'";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/tmp", "session-1"));
    requireThat(result.blocked(), "blocked").isTrue();
  }

  /**
   * Verifies that --unset user.email is blocked.
   */
  @Test
  public void unsetUserEmailIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config --unset user.email";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));
    requireThat(result.blocked(), "blocked").isTrue();
  }

  /**
   * Verifies that --get user.email (read-only) is allowed.
   */
  @Test
  public void getUserEmailIsAllowed()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config --get user.email";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));
    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that non-identity user.* keys are allowed (e.g., user.signingkey).
   */
  @Test
  public void nonIdentityUserKeyIsAllowed()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git config user.signingkey ABCDEF1234567890";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));
    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that GIT_AUTHOR_NAME env var is blocked.
   */
  @Test
  public void gitAuthorNameEnvVarIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "GIT_AUTHOR_NAME=attacker git commit -m 'test'";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));
    requireThat(result.blocked(), "blocked").isTrue();
  }

  /**
   * Verifies that GIT_AUTHOR_EMAIL env var is blocked.
   */
  @Test
  public void gitAuthorEmailEnvVarIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "GIT_AUTHOR_EMAIL=attacker@evil.com git commit -m 'test'";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));
    requireThat(result.blocked(), "blocked").isTrue();
  }

  /**
   * Verifies that GIT_COMMITTER_NAME env var is blocked.
   */
  @Test
  public void gitCommitterNameEnvVarIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "GIT_COMMITTER_NAME=attacker git commit -m 'test'";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));
    requireThat(result.blocked(), "blocked").isTrue();
  }

  /**
   * Verifies that git commit --author is blocked.
   */
  @Test
  public void gitCommitAuthorFlagIsBlocked()
  {
    BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
    String command = "git commit --author='Attacker <attacker@evil.com>' -m 'test'";
    BashHandler.Result result = handler.check(TestUtils.bashInput(command, "/workspace", "session1"));
    requireThat(result.blocked(), "blocked").isTrue();
  }
}
