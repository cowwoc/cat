/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.bash.post.DetectFailures;
import org.testng.annotations.Test;
import tools.jackson.databind.node.ObjectNode;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for DetectFailures.
 */
public final class DetectFailuresTest
{
  /**
   * Verifies that exit code 0 returns allow regardless of output content.
   */
  @Test
  public void exitCodeZeroReturnsAllow()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exit_code", 0);
      toolResult.put("stdout", "BUILD FAILED");
      toolResult.put("stderr", "ERROR: something went wrong");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(TestUtils.bashInput(scope, "mvn test", "", "test-session", toolResult));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that exit code != 0 with a failure pattern returns warn.
   */
  @Test
  public void nonZeroExitCodeWithFailurePatternReturnsWarn()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exit_code", 1);
      toolResult.put("stdout", "BUILD FAILED");
      toolResult.put("stderr", "");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(TestUtils.bashInput(scope, "mvn test", "", "test-session", toolResult));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that exit code != 0 without any failure pattern returns allow.
   */
  @Test
  public void nonZeroExitCodeWithoutFailurePatternReturnsAllow()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exit_code", 1);
      toolResult.put("stdout", "Command not found");
      toolResult.put("stderr", "");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(
        TestUtils.bashInput(scope, "nonexistent-cmd", "", "test-session", toolResult));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that the BUILD FAILED pattern in stdout triggers a warning.
   */
  @Test
  public void buildFailedPatternInStdoutTriggersWarn()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exit_code", 2);
      toolResult.put("stdout", "Tests run: 5, Failures: 1\nBUILD FAILED");
      toolResult.put("stderr", "");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(
        TestUtils.bashInput(scope, "mvn verify", "", "test-session", toolResult));

      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that the FAILED pattern in stderr triggers a warning.
   */
  @Test
  public void failedPatternInStderrTriggersWarn()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exit_code", 1);
      toolResult.put("stdout", "");
      toolResult.put("stderr", "FAILED: test suite execution");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(
        TestUtils.bashInput(scope, "./run-tests.sh", "", "test-session", toolResult));

      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that the ERROR pattern triggers a warning.
   */
  @Test
  public void errorPatternTriggersWarn()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exit_code", 1);
      toolResult.put("stdout", "ERROR: compilation failed");
      toolResult.put("stderr", "");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(
        TestUtils.bashInput(scope, "javac Main.java", "", "test-session", toolResult));

      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that the Exception pattern triggers a warning.
   */
  @Test
  public void exceptionPatternTriggersWarn()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exit_code", 1);
      toolResult.put("stdout", "java.lang.NullPointerException: Cannot invoke method");
      toolResult.put("stderr", "");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(
        TestUtils.bashInput(scope, "java -jar app.jar", "", "test-session", toolResult));

      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that the FATAL pattern triggers a warning.
   */
  @Test
  public void fatalPatternTriggersWarn()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exit_code", 1);
      toolResult.put("stdout", "FATAL: unrecoverable error");
      toolResult.put("stderr", "");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(
        TestUtils.bashInput(scope, "start-server.sh", "", "test-session", toolResult));

      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that failure patterns are detected case-insensitively.
   */
  @Test
  public void patternMatchingIsCaseInsensitive()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exit_code", 1);
      toolResult.put("stdout", "fatal: not a git repository");
      toolResult.put("stderr", "");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(
        TestUtils.bashInput(scope, "git status", "", "test-session", toolResult));

      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that null stdout and stderr fields default to empty string (no NPE).
   */
  @Test
  public void nullStdoutAndStderrDefaultToEmpty()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exit_code", 1);
      // stdout and stderr fields intentionally absent

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(
        TestUtils.bashInput(scope, "some-command", "", "test-session", toolResult));

      // No exception and returns allow (no failure pattern found)
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that null toolResult returns allow.
   */
  @Test
  public void nullToolResultReturnsAllow()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(TestUtils.bashInput(scope, "some-command", "", "test-session"));

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that a missing exit_code field defaults to 0 (returns allow).
   */
  @Test
  public void missingExitCodeDefaultsToZero()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      // No exit_code field
      toolResult.put("stdout", "BUILD FAILED");
      toolResult.put("stderr", "");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(TestUtils.bashInput(scope, "mvn test", "", "test-session", toolResult));

      // exit_code missing → defaults to 0 → allow
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that the warning message includes the exit code number.
   */
  @Test
  public void warningIncludesExitCodeNumber()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exit_code", 42);
      toolResult.put("stdout", "BUILD FAILED");
      toolResult.put("stderr", "");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(TestUtils.bashInput(scope, "mvn test", "", "test-session", toolResult));

      requireThat(result.reason(), "reason").contains("42");
    }
  }

  /**
   * Verifies that the camelCase exitCode field (without underscore) is parsed correctly.
   */
  @Test
  public void camelCaseExitCodeFieldIsParsed()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      ObjectNode toolResult = scope.getJsonMapper().createObjectNode();
      toolResult.put("exitCode", 1);
      toolResult.put("stdout", "BUILD FAILED");
      toolResult.put("stderr", "");

      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(TestUtils.bashInput(scope, "mvn test", "", "test-session", toolResult));

      // exitCode=1 (non-zero) with BUILD FAILED pattern → warn
      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }
}
