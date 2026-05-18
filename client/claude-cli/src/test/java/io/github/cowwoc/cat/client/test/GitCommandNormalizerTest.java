/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.bash.GitCommandNormalizer;
import org.testng.annotations.Test;

import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link GitCommandNormalizer}.
 */
public final class GitCommandNormalizerTest
{
  /**
   * Verifies that global git flags are stripped before matching.
   */
  @Test
  public void stripsGlobalFlags()
  {
    List<String> commands = GitCommandNormalizer.extractNormalizedGitCommands(
      "git -C /tmp/repo -c user.name=bot --git-dir .git --work-tree=. status --short");

    requireThat(commands, "commands").isEqualTo(List.of("git status --short"));
  }

  /**
   * Verifies that chained commands are split and only git commands are returned.
   */
  @Test
  public void splitsChainedCommands()
  {
    List<String> commands = GitCommandNormalizer.extractNormalizedGitCommands(
      "echo hello && git -C /tmp status || git --work-tree=. checkout .; ls | git -c a=b clean -fd");

    requireThat(commands, "commands").isEqualTo(
      List.of("git status", "git checkout .", "git clean -fd"));
  }

  /**
   * Verifies that multiline commands are split like other shell command separators.
   */
  @Test
  public void splitsMultilineCommands()
  {
    List<String> commands = GitCommandNormalizer.extractNormalizedGitCommands(
      "echo hello\ngit -C /tmp/repo clean -fd\r\ngit status --short");

    requireThat(commands, "commands").isEqualTo(List.of("git clean -fd", "git status --short"));
  }

  /**
   * Verifies that common shell prefixes before {@code git} do not bypass normalization.
   */
  @Test
  public void recognizesShellPrefixes()
  {
    List<String> commands = GitCommandNormalizer.extractNormalizedGitCommands(
      "FOO=1 git -C /tmp clean -fd && env BAR=2 git push --force && command git checkout -- .");

    requireThat(commands, "commands").isEqualTo(
      List.of("git clean -fd", "git push --force", "git checkout -- ."));
  }

  /**
   * Verifies that compact global flag forms are normalized.
   */
  @Test
  public void stripsCompactGlobalFlags()
  {
    List<String> commands = GitCommandNormalizer.extractNormalizedGitCommands(
      "git -C/tmp/repo -cuser.name=bot --git-dir=.git --work-tree=/tmp/repo status");

    requireThat(commands, "commands").isEqualTo(List.of("git status"));
  }

  /**
   * Verifies that sudo-prefixed git commands are recognized.
   */
  @Test
  public void recognizesSudoPrefixes()
  {
    List<String> commands = GitCommandNormalizer.extractNormalizedGitCommands(
      "sudo git -C /tmp/repo clean -fd && sudo -u alice git status");

    requireThat(commands, "commands").isEqualTo(List.of("git clean -fd", "git status"));
  }

  /**
   * Verifies that command-wrapper forms with compact flags are normalized.
   */
  @Test
  public void recognizesCommandWrapperWithCompactFlags()
  {
    List<String> commands = GitCommandNormalizer.extractNormalizedGitCommands(
      "command git -C/tmp/repo status --short");

    requireThat(commands, "commands").isEqualTo(List.of("git status --short"));
  }

  /**
   * Verifies that empty input returns no commands.
   */
  @Test
  public void emptyInputReturnsNoCommands()
  {
    List<String> commands = GitCommandNormalizer.extractNormalizedGitCommands("");

    requireThat(commands, "commands").isEmpty();
  }

  /**
   * Verifies that non-git input returns no commands.
   */
  @Test
  public void nonGitInputReturnsNoCommands()
  {
    List<String> commands = GitCommandNormalizer.extractNormalizedGitCommands("echo hi && ls -la");

    requireThat(commands, "commands").isEmpty();
  }

  /**
   * Verifies that git without a subcommand remains a valid normalized git command.
   */
  @Test
  public void gitWithoutSubcommandIsReturned()
  {
    List<String> commands = GitCommandNormalizer.extractNormalizedGitCommands("git");

    requireThat(commands, "commands").isEqualTo(List.of("git"));
  }

  /**
   * Verifies null command is rejected.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void extractNormalizedGitCommandsRejectsNull()
  {
    GitCommandNormalizer.extractNormalizedGitCommands(null);
  }

  /**
   * Verifies null command is rejected by raw/normalized extractor.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void extractGitCommandsRejectsNull()
  {
    GitCommandNormalizer.extractGitCommands(null);
  }
}
