/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.bash.post.DetectFailures;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
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
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 0);
    toolResult.put("stdout", "BUILD FAILED");
    toolResult.put("stderr", "ERROR: something went wrong");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("mvn test", "", "test-session", toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

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
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "BUILD FAILED");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("mvn test", "", "test-session", toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

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
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "Command not found");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("nonexistent-cmd", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

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
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 2);
    toolResult.put("stdout", "Tests run: 5, Failures: 1\nBUILD FAILED");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("mvn verify", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that a non-test-runner command (./run-tests.sh) with failure output does not trigger a warning.
   * <p>
   * Pattern matching is scoped to known test runner commands only, preventing false positives from
   * arbitrary scripts that happen to produce failure-keyword output.
   */
  @Test
  public void nonTestRunnerCommandWithFailureOutputReturnsAllow()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "");
    toolResult.put("stderr", "FAILED: test suite execution");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("./run-tests.sh", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that a compiler invocation (javac) with ERROR output does not trigger a warning.
   * <p>
   * Pattern matching is scoped to known test runner commands; javac is not a test runner.
   */
  @Test
  public void compilerCommandWithErrorOutputReturnsAllow()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "ERROR: compilation failed");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("javac Main.java", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that a JVM invocation (java -jar) with exception output does not trigger a warning.
   * <p>
   * Pattern matching is scoped to known test runner commands; arbitrary java invocations are not test runners.
   */
  @Test
  public void jvmCommandWithExceptionOutputReturnsAllow()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "java.lang.NullPointerException: Cannot invoke method");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("java -jar app.jar", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that a server start script with FATAL output does not trigger a warning.
   * <p>
   * Pattern matching is scoped to known test runner commands; server scripts are not test runners.
   */
  @Test
  public void serverScriptWithFatalOutputReturnsAllow()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "FATAL: unrecoverable error");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("start-server.sh", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that git commands with fatal output do not trigger a warning.
   * <p>
   * A common false positive: git commands produce "fatal: not a git repository" which contains a
   * failure keyword but is not test runner output.
   */
  @Test
  public void gitCommandWithFatalOutputReturnsAllow()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "fatal: not a git repository");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("git status", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that git diff output containing failure keywords does not trigger a warning.
   * <p>
   * A common false positive: git diff shows lines from files that contain words like "FAILED" or
   * "test_failure" as part of diff content, but the command itself is not a test runner.
   */
  @Test
  public void gitDiffWithFailureKeywordsInOutputReturnsAllow()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 0);
    toolResult.put("stdout", """
      diff --git a/PLAN.md b/PLAN.md
      --- a/PLAN.md
      +++ b/PLAN.md
      @@ -1,3 +1,4 @@
      +This discusses test_failure detection and FAILURE patterns in diffs.
      """);
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("git diff", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that a cat command reading a file with failure keywords does not trigger a warning.
   * <p>
   * A common false positive: reading a file that contains "FAILURE" or "Exception" as documentation text.
   */
  @Test
  public void catCommandWithFailureKeywordsInOutputReturnsAllow()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "This file discusses Exception handling and FAILURE modes in test scenarios.");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("cat PLAN.md", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that get-output get-diff output with failure keywords does not trigger a warning.
   * <p>
   * A common false positive: the get-output get-diff skill renders diffs that include PLAN.md content
   * with words like "failure" or "test" in it.
   */
  @Test
  public void getOutputGetDiffWithFailureKeywordsReturnsAllow()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 0);
    toolResult.put("stdout", "Rendered diff: ERROR: test_failure detection would trigger here\nBUILD FAILED line");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("get-output get-diff", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
  }

  /**
   * Verifies that bats test runner failures trigger a warning.
   * <p>
   * bats is a known test runner and must still trigger detection on failures.
   */
  @Test
  public void batsTestRunnerFailureTriggersWarn()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "1..3\nnot ok 1 should pass\nFAILED (1 of 3 tests)");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("bats tests/", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that gradle test failures trigger a warning.
   * <p>
   * gradle test is a known test runner and must still trigger detection on failures.
   */
  @Test
  public void gradleTestFailureTriggersWarn()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "BUILD FAILED\n5 tests completed, 2 failed");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("gradle test", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that pattern matching is case-insensitive for known test runner commands.
   * <p>
   * The mvn test runner with lowercase "build failed" in output must still trigger a warning.
   */
  @Test
  public void patternMatchingIsCaseInsensitiveForTestRunners()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "build failed\ntests run: 1, failures: 1");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("mvn test", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that "npm run test" (alternate npm test invocation) triggers a warning on failure.
   * <p>
   * "npm run test" is a common alternative to "npm test" and must also be recognized as a test runner.
   */
  @Test
  public void npmRunTestFailureTriggersWarn()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    toolResult.put("stdout", "FAILED: 3 tests failed");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("npm run test", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }

  /**
   * Verifies that null stdout and stderr fields default to empty string (no NPE).
   */
  @Test
  public void nullStdoutAndStderrDefaultToEmpty()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 1);
    // stdout and stderr fields intentionally absent

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("some-command", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

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
    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("some-command", "", "test-session", null))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

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
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    // No exit_code field
    toolResult.put("stdout", "BUILD FAILED");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("mvn test", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

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
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exit_code", 42);
    toolResult.put("stdout", "BUILD FAILED");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("mvn test", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      requireThat(result.reason(), "reason").contains("42");
    }
  }

  /**
   * Verifies that the camelCase exitCode field (without underscore) is parsed correctly.
   */
  @Test
  public void camelCaseExitCodeFieldIsParsed()
  {
    JsonMapper mapper = new JsonMapper();
    ObjectNode toolResult = mapper.createObjectNode();
    toolResult.put("exitCode", 1);
    toolResult.put("stdout", "BUILD FAILED");
    toolResult.put("stderr", "");

    try (TestClaudeHook scope = TestUtils.bashHookWithToolResult("mvn test", "", "test-session",
      toolResult))
    {
      DetectFailures handler = new DetectFailures();
      BashHandler.Result result = handler.check(scope);

      // exitCode=1 (non-zero) with BUILD FAILED pattern → warn
      requireThat(result.reason(), "reason").isNotEmpty();
    }
  }
}
