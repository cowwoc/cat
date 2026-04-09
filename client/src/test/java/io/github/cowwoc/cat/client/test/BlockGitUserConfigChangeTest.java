/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.bash.BlockGitUserConfigChange;
import org.testng.annotations.Test;

import java.io.IOException;

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
  public void setUserNameIsBlocked() throws IOException
  {
    String command = "git config user.name \"Alice\"";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("user.name");
    }
  }

  /**
   * Verifies that setting git user.email is blocked.
   */
  @Test
  public void setUserEmailIsBlocked() throws IOException
  {
    String command = "git config user.email \"alice@example.com\"";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("user.email");
    }
  }

  /**
   * Verifies that the block message instructs the user to request the change explicitly.
   */
  @Test
  public void blockMessageMentionsExplicitUserRequest() throws IOException
  {
    String command = "git config user.name \"Bob\"";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("explicitly");
    }
  }

  /**
   * Verifies that --global scope for user.name is also blocked.
   */
  @Test
  public void setGlobalUserNameIsBlocked() throws IOException
  {
    String command = "git config --global user.name \"Carol\"";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("user.name");
    }
  }

  /**
   * Verifies that --global scope for user.email is also blocked.
   */
  @Test
  public void setGlobalUserEmailIsBlocked() throws IOException
  {
    String command = "git config --global user.email \"carol@example.com\"";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("user.email");
    }
  }

  /**
   * Verifies that reading git user.name (no value argument) is allowed.
   */
  @Test
  public void readUserNameIsAllowed() throws IOException
  {
    String command = "git config user.name";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that reading git user.email (no value argument) is allowed.
   */
  @Test
  public void readUserEmailIsAllowed() throws IOException
  {
    String command = "git config user.email";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that git config commands not targeting user identity are allowed.
   */
  @Test
  public void nonUserConfigIsAllowed() throws IOException
  {
    String command = "git config receive.denyCurrentBranch updateInstead";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that unrelated commands are allowed.
   */
  @Test
  public void nonGitConfigCommandIsAllowed() throws IOException
  {
    String command = "git commit -m \"feature: add something\"";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that --get for user.name (read-only) is allowed.
   */
  @Test
  public void getUserNameIsAllowed() throws IOException
  {
    String command = "git config --get user.name";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that --unset for user.name is blocked (it modifies identity config).
   */
  @Test
  public void unsetUserNameIsBlocked() throws IOException
  {
    String command = "git config --unset user.name";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that --unset-all for user.name is blocked.
   */
  @Test
  public void unsetAllUserNameIsBlocked() throws IOException
  {
    String command = "git config --unset-all user.name";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/tmp", "session-1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that --unset-all for user.email is blocked.
   */
  @Test
  public void unsetAllUserEmailIsBlocked() throws IOException
  {
    String command = "git config --unset-all user.email";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/tmp", "session-1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that --remove-section user is blocked.
   */
  @Test
  public void removeUserSectionIsBlocked() throws IOException
  {
    String command = "git config --remove-section user";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/tmp", "session-1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that git -c user.name=attacker is blocked.
   */
  @Test
  public void inlineUserNameViaMinusCIsBlocked() throws IOException
  {
    String command = "git -c user.name=attacker commit -m 'test'";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/tmp", "session-1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that git -c user.email=attacker@evil.com is blocked.
   */
  @Test
  public void inlineUserEmailViaMinusCIsBlocked() throws IOException
  {
    String command = "git -c user.email=attacker@evil.com commit -m 'test'";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/tmp", "session-1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that --unset user.email is blocked.
   */
  @Test
  public void unsetUserEmailIsBlocked() throws IOException
  {
    String command = "git config --unset user.email";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that --get user.email (read-only) is allowed.
   */
  @Test
  public void getUserEmailIsAllowed() throws IOException
  {
    String command = "git config --get user.email";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that non-identity user.* keys are allowed (e.g., user.signingkey).
   */
  @Test
  public void nonIdentityUserKeyIsAllowed() throws IOException
  {
    String command = "git config user.signingkey ABCDEF1234567890";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that GIT_AUTHOR_NAME env var is blocked.
   */
  @Test
  public void gitAuthorNameEnvVarIsBlocked() throws IOException
  {
    String command = "GIT_AUTHOR_NAME=attacker git commit -m 'test'";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that GIT_AUTHOR_EMAIL env var is blocked.
   */
  @Test
  public void gitAuthorEmailEnvVarIsBlocked() throws IOException
  {
    String command = "GIT_AUTHOR_EMAIL=attacker@evil.com git commit -m 'test'";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that GIT_COMMITTER_NAME env var is blocked.
   */
  @Test
  public void gitCommitterNameEnvVarIsBlocked() throws IOException
  {
    String command = "GIT_COMMITTER_NAME=attacker git commit -m 'test'";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that git commit --author is blocked.
   */
  @Test
  public void gitCommitAuthorFlagIsBlocked() throws IOException
  {
    String command = "git commit --author='Attacker <attacker@evil.com>' -m 'test'";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that printf appending to ~/.gitconfig is blocked.
   */
  @Test
  public void printfAppendToHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "printf '[user]\\nname=X\\n' >> ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that echo appending to ~/.gitconfig is blocked.
   */
  @Test
  public void echoAppendToHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "echo '  name = X' >> ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that echo redirecting to ~/.gitconfig (overwrite) is blocked.
   */
  @Test
  public void echoOverwriteHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "echo '[user]' > ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that tee targeting ~/.gitconfig is blocked.
   */
  @Test
  public void teeToHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "echo '[user]' | tee ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that tee -a (append) targeting ~/.gitconfig is blocked.
   */
  @Test
  public void teeAppendToHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "echo '  name = X' | tee -a ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that sed -i editing ~/.gitconfig is blocked.
   */
  @Test
  public void sedInPlaceEditHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "sed -i 's/name = Alice/name = Attacker/' ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that awk redirecting to ~/.gitconfig is blocked.
   */
  @Test
  public void awkWriteToHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "awk '{print}' input.txt > ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that cat with output redirect to ~/.gitconfig is blocked.
   */
  @Test
  public void catWriteToHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "cat > ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that writes using $HOME instead of ~ are also blocked.
   */
  @Test
  public void echoAppendUsingDollarHomeIsBlocked() throws IOException
  {
    String command = "echo '[user]' >> $HOME/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that writes to ~/.config/git/config are blocked.
   */
  @Test
  public void echoAppendToXdgGitConfigIsBlocked() throws IOException
  {
    String command = "echo '[user]' >> ~/.config/git/config";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that writes to /etc/gitconfig are blocked.
   */
  @Test
  public void echoAppendToEtcGitconfigIsBlocked() throws IOException
  {
    String command = "echo '[user]' >> /etc/gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that reading ~/.gitconfig with cat (no redirect) is allowed.
   */
  @Test
  public void catReadHomeDotGitconfigIsAllowed() throws IOException
  {
    String command = "cat ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that writing to an unrelated file (e.g., ~/notes.txt) is allowed.
   */
  @Test
  public void echoAppendToUnrelatedFileIsAllowed() throws IOException
  {
    String command = "echo hello >> ~/notes.txt";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that printf appending to a non-gitconfig file is allowed.
   */
  @Test
  public void printfAppendToUnrelatedFileIsAllowed() throws IOException
  {
    String command = "printf 'some content' >> ~/somefile.txt";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that tee with long-form flag (--append) targeting ~/.gitconfig is blocked.
   */
  @Test
  public void teeLongFormAppendToHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "echo '[user]' | tee --append ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that tee with combined flags (tee -ai) targeting ~/.gitconfig is blocked.
   */
  @Test
  public void teeCombinedFlagsToHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "echo '[user]' | tee -ai ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that cp targeting ~/.gitconfig is blocked.
   */
  @Test
  public void cpToHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "cp /tmp/evil.gitconfig ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that mv targeting ~/.gitconfig is blocked.
   */
  @Test
  public void mvToHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "mv /tmp/evil.gitconfig ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that install targeting ~/.gitconfig is blocked.
   */
  @Test
  public void installToHomeDotGitconfigIsBlocked() throws IOException
  {
    String command = "install -m 600 /tmp/evil.gitconfig ~/.gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that cp targeting ~/.config/git/config is blocked.
   */
  @Test
  public void cpToXdgGitConfigIsBlocked() throws IOException
  {
    String command = "cp /tmp/evil.gitconfig ~/.config/git/config";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that mv targeting /etc/gitconfig is blocked.
   */
  @Test
  public void mvToEtcGitconfigIsBlocked() throws IOException
  {
    String command = "mv /tmp/evil.gitconfig /etc/gitconfig";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that cp to an unrelated file is allowed.
   */
  @Test
  public void cpToUnrelatedFileIsAllowed() throws IOException
  {
    String command = "cp /tmp/notes.txt ~/notes.txt";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that mv to an unrelated file is allowed.
   */
  @Test
  public void mvToUnrelatedFileIsAllowed() throws IOException
  {
    String command = "mv /tmp/notes.txt ~/notes.txt";
    try (TestClaudeHook scope = TestUtils.bashHook(command, "/workspace", "session1"))
    {
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();

      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }
}
