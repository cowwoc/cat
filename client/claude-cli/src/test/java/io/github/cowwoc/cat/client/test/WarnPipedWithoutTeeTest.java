/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.bash.WarnPipedWithoutTee;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link WarnPipedWithoutTee}.
 * <p>
 * Tests verify that piped bash commands without {@code tee} produce a warning, while commands
 * that already use {@code tee} or have no pipe are allowed.
 */
public final class WarnPipedWithoutTeeTest
{
  private static final String SESSION_ID = "00000000-0000-0000-0000-000000000000";
  private static final Path WORKING_DIR = Path.of("/tmp");

  /**
   * Verifies that a piped command without {@code tee} emits a warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void pipedCommandWithoutTeeEmitsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "some-command 2>&1 | grep pattern");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a piped command with {@code tee} is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void pipedCommandWithTeeIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "some-command 2>&1 | tee /tmp/log.txt | grep pattern");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a command without a pipe is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void commandWithoutPipeIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "git status");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that piping to {@code head} without {@code tee} emits a warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void simplePipeToHeadEmitsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "git log --oneline | head -5");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that piping to {@code tail} without {@code tee} emits a warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void simplePipeToTailEmitsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "cat file.txt | tail -20");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that piping to {@code wc} without {@code tee} emits a warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void simplePipeToWcEmitsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "echo \"hello\" | wc -c");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that piping to {@code sort} without {@code tee} emits a warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void simplePipeToSortEmitsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "ls | sort");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that piping to {@code grep} without {@code tee} emits a warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void pipeToGrepWithoutTeeEmitsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "mvn test 2>&1 | grep ERROR");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple pipes with {@code tee} present is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void multiplePipesWithTeeIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "cmd | tee /tmp/out.log | grep pattern | head -5");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the logical OR operator ({@code ||}) does not trigger a pipe warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void logicalOrDoesNotTrigger() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "test -f file.txt || echo missing");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a pipe character inside quotes does not trigger a pipe warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void pipeInsideQuotesDoesNotTrigger() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "echo 'a|b' > file.txt");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the warning message contains actionable guidance about using {@code tee}
   * and a log file.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void warningMessageContainsGuidance() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "some-command 2>&1 | grep pattern");

      requireThat(result.reason(), "reason").contains("tee");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that piping to {@code uniq} without {@code tee} emits a warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void simplePipeToUniqEmitsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "sort file.txt | uniq");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that piping to {@code cut} without {@code tee} emits a warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void simplePipeToCutEmitsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "echo \"a:b:c\" | cut -d: -f1");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that piping to {@code tr} without {@code tee} emits a warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void simplePipeToTrEmitsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "echo \"HELLO\" | tr A-Z a-z");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a chained command with a pipe without {@code tee} emits a warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void chainedCommandWithPipeWithoutTeeEmits() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "cd /tmp && some-command 2>&1 | grep error");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that piping to {@code awk} without {@code tee} emits a warning.
   * <p>
   * {@code awk} is not a simple formatter and requires tee for output preservation.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void pipeToAwkWithoutTeeEmitsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "ps aux | awk '{print $1}'");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that piping to {@code sed} without {@code tee} emits a warning.
   * <p>
   * {@code sed} is not a simple formatter and requires tee for output preservation.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void pipeToSedWithoutTeeEmitsWarning() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      BashHandler.Result result = check(scope, "cat file.txt | sed 's/old/new/'");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Runs the handler against a bash command payload that reuses the setup scope's paths.
   *
   * @param scope the setup scope
   * @param command the bash command
   * @return the handler result
   * @throws IOException if JSON construction fails
   */
  private static BashHandler.Result check(TestClaudeTool scope, String command) throws IOException
  {
    try (TestClaudeHook commandScope = TestUtils.bashHook(command, WORKING_DIR, SESSION_ID, scope))
    {
      return new WarnPipedWithoutTee(commandScope).check();
    }
  }
}
