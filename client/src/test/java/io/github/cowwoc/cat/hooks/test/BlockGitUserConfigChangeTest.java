/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.bash.BlockGitUserConfigChange;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config user.name \"Alice\"";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config user.email \"alice@example.com\"";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config user.name \"Bob\"";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config --global user.name \"Carol\"";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config --global user.email \"carol@example.com\"";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

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
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config user.name";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that reading git user.email (no value argument) is allowed.
   */
  @Test
  public void readUserEmailIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config user.email";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that git config commands not targeting user identity are allowed.
   */
  @Test
  public void nonUserConfigIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config receive.denyCurrentBranch updateInstead";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that unrelated commands are allowed.
   */
  @Test
  public void nonGitConfigCommandIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git commit -m \"feature: add something\"";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that --get for user.name (read-only) is allowed.
   */
  @Test
  public void getUserNameIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config --get user.name";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that --unset for user.name is blocked (it modifies identity config).
   */
  @Test
  public void unsetUserNameIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config --unset user.name";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that --unset-all for user.name is blocked.
   */
  @Test
  public void unsetAllUserNameIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config --unset-all user.name";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/tmp", "session-1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that --unset-all for user.email is blocked.
   */
  @Test
  public void unsetAllUserEmailIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config --unset-all user.email";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/tmp", "session-1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that --remove-section user is blocked.
   */
  @Test
  public void removeUserSectionIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config --remove-section user";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/tmp", "session-1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that git -c user.name=attacker is blocked.
   */
  @Test
  public void inlineUserNameViaMinusCIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git -c user.name=attacker commit -m 'test'";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/tmp", "session-1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that git -c user.email=attacker@evil.com is blocked.
   */
  @Test
  public void inlineUserEmailViaMinusCIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git -c user.email=attacker@evil.com commit -m 'test'";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/tmp", "session-1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that --unset user.email is blocked.
   */
  @Test
  public void unsetUserEmailIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config --unset user.email";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that --get user.email (read-only) is allowed.
   */
  @Test
  public void getUserEmailIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config --get user.email";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that non-identity user.* keys are allowed (e.g., user.signingkey).
   */
  @Test
  public void nonIdentityUserKeyIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git config user.signingkey ABCDEF1234567890";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that GIT_AUTHOR_NAME env var is blocked.
   */
  @Test
  public void gitAuthorNameEnvVarIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "GIT_AUTHOR_NAME=attacker git commit -m 'test'";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that GIT_AUTHOR_EMAIL env var is blocked.
   */
  @Test
  public void gitAuthorEmailEnvVarIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "GIT_AUTHOR_EMAIL=attacker@evil.com git commit -m 'test'";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that GIT_COMMITTER_NAME env var is blocked.
   */
  @Test
  public void gitCommitterNameEnvVarIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "GIT_COMMITTER_NAME=attacker git commit -m 'test'";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that git commit --author is blocked.
   */
  @Test
  public void gitCommitAuthorFlagIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "git commit --author='Attacker <attacker@evil.com>' -m 'test'";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that printf appending to ~/.gitconfig is blocked.
   */
  @Test
  public void printfAppendToHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "printf '[user]\\nname=X\\n' >> ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that echo appending to ~/.gitconfig is blocked.
   */
  @Test
  public void echoAppendToHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "echo '  name = X' >> ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that echo redirecting to ~/.gitconfig (overwrite) is blocked.
   */
  @Test
  public void echoOverwriteHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "echo '[user]' > ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that tee targeting ~/.gitconfig is blocked.
   */
  @Test
  public void teeToHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "echo '[user]' | tee ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that tee -a (append) targeting ~/.gitconfig is blocked.
   */
  @Test
  public void teeAppendToHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "echo '  name = X' | tee -a ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that sed -i editing ~/.gitconfig is blocked.
   */
  @Test
  public void sedInPlaceEditHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "sed -i 's/name = Alice/name = Attacker/' ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that awk redirecting to ~/.gitconfig is blocked.
   */
  @Test
  public void awkWriteToHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "awk '{print}' input.txt > ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that cat with output redirect to ~/.gitconfig is blocked.
   */
  @Test
  public void catWriteToHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "cat > ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that writes using $HOME instead of ~ are also blocked.
   */
  @Test
  public void echoAppendUsingDollarHomeIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "echo '[user]' >> $HOME/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that writes to ~/.config/git/config are blocked.
   */
  @Test
  public void echoAppendToXdgGitConfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "echo '[user]' >> ~/.config/git/config";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that writes to /etc/gitconfig are blocked.
   */
  @Test
  public void echoAppendToEtcGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "echo '[user]' >> /etc/gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that reading ~/.gitconfig with cat (no redirect) is allowed.
   */
  @Test
  public void catReadHomeDotGitconfigIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "cat ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that writing to an unrelated file (e.g., ~/notes.txt) is allowed.
   */
  @Test
  public void echoAppendToUnrelatedFileIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "echo hello >> ~/notes.txt";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that printf appending to a non-gitconfig file is allowed.
   */
  @Test
  public void printfAppendToUnrelatedFileIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "printf 'some content' >> ~/somefile.txt";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that tee with long-form flag (--append) targeting ~/.gitconfig is blocked.
   */
  @Test
  public void teeLongFormAppendToHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "echo '[user]' | tee --append ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that tee with combined flags (tee -ai) targeting ~/.gitconfig is blocked.
   */
  @Test
  public void teeCombinedFlagsToHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "echo '[user]' | tee -ai ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that cp targeting ~/.gitconfig is blocked.
   */
  @Test
  public void cpToHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "cp /tmp/evil.gitconfig ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that mv targeting ~/.gitconfig is blocked.
   */
  @Test
  public void mvToHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "mv /tmp/evil.gitconfig ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that install targeting ~/.gitconfig is blocked.
   */
  @Test
  public void installToHomeDotGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "install -m 600 /tmp/evil.gitconfig ~/.gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that cp targeting ~/.config/git/config is blocked.
   */
  @Test
  public void cpToXdgGitConfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "cp /tmp/evil.gitconfig ~/.config/git/config";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that mv targeting /etc/gitconfig is blocked.
   */
  @Test
  public void mvToEtcGitconfigIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "mv /tmp/evil.gitconfig /etc/gitconfig";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isTrue();
    }
  }

  /**
   * Verifies that cp to an unrelated file is allowed.
   */
  @Test
  public void cpToUnrelatedFileIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "cp /tmp/notes.txt ~/notes.txt";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that mv to an unrelated file is allowed.
   */
  @Test
  public void mvToUnrelatedFileIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      BlockGitUserConfigChange handler = new BlockGitUserConfigChange();
      String command = "mv /tmp/notes.txt ~/notes.txt";

      BashHandler.Result result = handler.check(TestUtils.bashInput(mapper, command, "/workspace", "session1"));

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }
}
