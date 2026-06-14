/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.codex;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.client.test.TestCodexTool;
import io.github.cowwoc.cat.client.test.TestUtils;
import io.github.cowwoc.cat.codex.engine.CodexRunner;
import io.github.cowwoc.cat.codex.engine.CodexRunnerSupport;
import io.github.cowwoc.cat.codex.engine.CodexRunner.ParsedOutput;
import io.github.cowwoc.cat.engine.NestedRunnerEvent;
import io.github.cowwoc.cat.engine.NestedRunnerEngineSubstates;
import io.github.cowwoc.cat.engine.NestedRunnerSessionState;
import io.github.cowwoc.cat.engine.NestedRunnerState;
import io.github.cowwoc.cat.engine.NestedRunnerTurnState;

import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

/**
 * Tests for {@link CodexRunner}.
 */
public final class CodexRunnerTest
{
  /**
   * Verifies that --help documents omitted optional argument behavior.
   */
  @Test
  public void helpDocumentsOptionalArgumentBehavior() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, UTF_8);

      int exitCode = CodexRunner.run(new String[]{"--help"}, scope, out);
      String help = buffer.toString(UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(0);
      requireThat(help, "help").contains("omitted: current CAT project path");
      requireThat(help, "help").contains("omitted: stdout text only");
      requireThat(help, "help").contains("omitted: not persisted");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the Codex command uses stdin and writes the final answer to a file.
   */
  @Test
  public void buildCommandUsesCodexExecJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMinutes(3),
        Map.of("CODEX_TOOL", "codex-cli"));
      Path outputPath = tempDir.resolve("last-message.txt");

      List<String> command = runner.buildCommand("gpt-5.5", "high", tempDir, outputPath);

      requireThat(command, "command").isEqualTo(List.of("codex", "exec", "--json",
        "--output-last-message", outputPath.toString(), "--cd", tempDir.toString(),
        "--sandbox", "danger-full-access", "--model", "gpt-5.5", "-c",
        "model_reasoning_effort=\"high\"", "-"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that resumed Codex sessions use the resume subcommand and preserve JSON output.
   */
  @Test
  public void buildResumeCommandUsesResumeJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMinutes(3), Map.of());
      Path outputPath = tempDir.resolve("last-message.txt");

      List<String> command = runner.buildResumeCommand("session-123", "gpt-5.5", "high", tempDir,
        outputPath);

      requireThat(command, "command").isEqualTo(List.of("codex", "exec", "resume", "--json",
        "--output-last-message", outputPath.toString(), "--cd", tempDir.toString(), "--model",
        "gpt-5.5", "-c", "model_reasoning_effort=\"high\"", "--", "session-123", "-"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that nested Codex commands inherit additional workdirs alongside the primary cwd.
   */
  @Test
  public void buildCommandAppendsSharedWorkdirs() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMinutes(3),
        Map.of("ADDITIONAL_WORKDIRS", "/home/node/.cat/worktrees"));
      Path outputPath = tempDir.resolve("last-message.txt");

      List<String> command = runner.buildCommand("gpt-5.5", "high", tempDir, outputPath);

      requireThat(command.indexOf("--cd"), "cdIndex").isEqualTo(5);
      requireThat(command.get(6), "cwd").isEqualTo(tempDir.toString());
      requireThat(command.indexOf("--add-dir"), "firstAddDirIndex").isEqualTo(7);
      requireThat(command.get(8), "firstSharedDir").isEqualTo("/home/node/.cat/worktrees");
      requireThat(command, "command").containsExactly(List.of("codex", "exec", "--json",
        "--output-last-message", outputPath.toString(), "--cd", tempDir.toString(), "--add-dir",
        "/home/node/.cat/worktrees", "--model", "gpt-5.5", "-c",
        "model_reasoning_effort=\"high\"", "-"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that resumed nested Codex commands inherit additional workdirs alongside the primary cwd.
   */
  @Test
  public void buildResumeCommandAppendsSharedWorkdirs() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMinutes(3),
        Map.of("ADDITIONAL_WORKDIRS", "/home/node/.cat/worktrees"));
      Path outputPath = tempDir.resolve("last-message.txt");

      List<String> command = runner.buildResumeCommand("session-123", "gpt-5.5", "high", tempDir,
        outputPath);

      requireThat(command.indexOf("--cd"), "cdIndex").isEqualTo(6);
      requireThat(command.get(7), "cwd").isEqualTo(tempDir.toString());
      requireThat(command.indexOf("--add-dir"), "firstAddDirIndex").isEqualTo(8);
      requireThat(command.get(9), "firstSharedDir").isEqualTo("/home/node/.cat/worktrees");
      requireThat(command, "command").containsExactly(List.of("codex", "exec", "resume", "--json",
        "--output-last-message", outputPath.toString(), "--cd", tempDir.toString(), "--add-dir",
        "/home/node/.cat/worktrees", "--model", "gpt-5.5", "-c",
        "model_reasoning_effort=\"high\"", "--", "session-123", "-"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that resumed Codex sessions validate effort values the same way fresh sessions do.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid effort.*")
  public void buildResumeCommandRejectsInvalidEffort() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMinutes(3), Map.of());
      runner.buildResumeCommand("session-123", "gpt-5.5", "invalid", tempDir,
        tempDir.resolve("last-message.txt"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that resumed Codex session ids cannot be interpreted as CLI flags.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*must not start with.*")
  public void rejectFlagLikeResumeSessionId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMinutes(3), Map.of());
      runner.buildResumeCommand("--bad-session", "gpt-5.5", "high", tempDir,
        tempDir.resolve("last-message.txt"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that resumed sessions preserve the same external sandbox and cwd policy as new turns.
   */
  @Test
  public void resumeCommandKeepsExecutionPolicy() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMinutes(3),
        Map.of("CODEX_TOOL", "codex-cli"));
      Path outputPath = tempDir.resolve("last-message.txt");

      List<String> command = runner.buildResumeCommand("session-123", "gpt-5.5", "high", tempDir,
        outputPath);

      requireThat(command, "command").isEqualTo(List.of("codex", "exec", "resume", "--json",
        "--output-last-message", outputPath.toString(), "--cd", tempDir.toString(),
        "--sandbox", "danger-full-access", "--model", "gpt-5.5", "-c",
        "model_reasoning_effort=\"high\"", "--", "session-123", "-"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that normal Codex executions keep the default sandbox policy.
   */
  @Test
  public void buildCommandUsesDefaultSandboxOutside() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMinutes(3), Map.of());
      Path outputPath = tempDir.resolve("last-message.txt");

      List<String> command = runner.buildCommand("gpt-5.5", "high", tempDir, outputPath);

      requireThat(command, "command").isEqualTo(List.of("codex", "exec", "--json",
        "--output-last-message", outputPath.toString(), "--cd", tempDir.toString(),
        "--model", "gpt-5.5", "-c", "model_reasoning_effort=\"high\"", "-"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that nested runs inherit yolo mode when the parent approval policy is never.
   */
  @Test
  public void buildCommandInheritsYoloMode() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMinutes(3),
        Map.of("CODEX_APPROVAL_POLICY", "never"));
      Path outputPath = tempDir.resolve("last-message.txt");

      List<String> command = runner.buildCommand("gpt-5.5", "high", tempDir, outputPath);

      requireThat(command, "command").isEqualTo(List.of("codex", "exec", "--json",
        "--output-last-message", outputPath.toString(), "--cd", tempDir.toString(),
        "--dangerously-bypass-approvals-and-sandbox", "--model", "gpt-5.5", "-c",
        "model_reasoning_effort=\"high\"", "-"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that omitting the model is rejected.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*model.*")
  public void buildCommandRejectsMissingModel() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      Path outputPath = tempDir.resolve("last-message.txt");

      runner.buildCommand("", "high", tempDir, outputPath);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that omitting the effort is rejected.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*effort.*")
  public void buildCommandRejectsMissingEffort() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      Path outputPath = tempDir.resolve("last-message.txt");

      runner.buildCommand("gpt-5.5", "", tempDir, outputPath);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that unsupported Codex effort values are rejected.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid effort.*")
  public void buildCommandRejectsMinimalEffort() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      Path outputPath = tempDir.resolve("last-message.txt");

      runner.buildCommand("gpt-5.5", "minimal", tempDir, outputPath);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the process builder runs in the requested directory.
   */
  @Test
  public void buildProcessBuilderUsesWorkingDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      List<String> command = runner.buildCommand("gpt-5.5", "high", tempDir,
        tempDir.resolve("last-message.txt"));
      ProcessBuilder builder = runner.buildProcessBuilder(command, tempDir);

      requireThat(builder.directory().toPath(), "directory").isEqualTo(tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the process timeout is enforced while stdout remains open.
   */
  @Test
  public void executeProcessTimesOutBeforeStdoutCloses() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMillis(100));
      Path outputPath = tempDir.resolve("last-message.txt");
      List<String> command = List.of("bash", "-c", "printf '{\"type\":\"agent_message\"," +
        "\"message\":\"started\"}\\n'; sleep 5");

      CodexRunner.ProcessResult result = runner.executeProcess(command, "prompt", tempDir,
        outputPath);

      requireThat(result.error(), "error").isEqualTo("timeout");
      requireThat(result.elapsed().compareTo(Duration.ofSeconds(3)), "elapsed").isLessThan(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a blocked callback does not prevent timeout cleanup from returning.
   */
  @Test
  public void timeoutReturnsWhenCallbackBlocks() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMillis(200));
      Path outputPath = tempDir.resolve("last-message.txt");
      List<String> command = List.of("bash", "-c", "printf '{\"type\":\"turn.started\"," +
        "\"turn_id\":\"turn-1\"}\\n'; sleep 0.15");
      CountDownLatch callbackStarted = new CountDownLatch(1);
      CountDownLatch releaseCallback = new CountDownLatch(1);

      CodexRunner.ProcessResult result;
      try
      {
        result = runner.executeProcess(command, "prompt", tempDir, outputPath, null, true, event ->
        {
          callbackStarted.countDown();
          try
          {
            releaseCallback.await();
          }
          catch (InterruptedException _)
          {
            Thread.currentThread().interrupt();
          }
        });
      }
      finally
      {
        releaseCallback.countDown();
      }

      requireThat(callbackStarted.await(1, TimeUnit.SECONDS), "callbackStarted").isTrue();
      requireThat(result.error(), "error").isEqualTo("timeout");
      requireThat(result.elapsed().compareTo(Duration.ofMillis(320)), "elapsed").isLessThan(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that malformed JSONL output is reported as an error instead of throwing.
   */
  @Test
  public void executeProcessMalformedJsonl() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofSeconds(5));
      Path outputPath = tempDir.resolve("last-message.txt");
      List<String> command = List.of("bash", "-c", "printf '{bad json}\\n'");

      CodexRunner.ProcessResult result = runner.executeProcess(command, "prompt", tempDir,
        outputPath);

      requireThat(result.error(), "error").isNotBlank();
      requireThat(result.parsed().texts(), "texts").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that malformed Codex JSONL aborts promptly even if the child process keeps running.
   */
  @Test
  public void malformedJsonlReturnsPromptly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofMillis(200));
      Path outputPath = tempDir.resolve("last-message.txt");
      List<String> command = List.of("bash", "-c", """
        printf '{bad json}\n'
        sleep 5
        """);

      CodexRunner.ProcessResult result = runner.executeProcess(command, "prompt", tempDir,
        outputPath);

      requireThat(result.error(), "error").isNotBlank();
      requireThat(result.elapsed().compareTo(Duration.ofMillis(320)), "elapsed").isLessThan(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a failing Codex callback aborts promptly instead of waiting for process timeout.
   */
  @Test
  public void executeProcessReturnsWhenCallbackFails() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofSeconds(1));
      Path outputPath = tempDir.resolve("last-message.txt");
      CountDownLatch callbackStarted = new CountDownLatch(1);
      List<String> command = List.of("bash", "-c", """
        printf '{"type":"turn.started","turn_id":"turn-1"}\n'
        sleep 5
        """);

      CodexRunner.ProcessResult result = runner.executeProcess(command, "prompt", tempDir,
        outputPath, null, true, _ ->
        {
          callbackStarted.countDown();
          throw new IllegalStateException("listener boom");
        });

      requireThat(callbackStarted.await(1, TimeUnit.SECONDS), "callbackStarted").isTrue();
      requireThat(result.error(), "error").contains("listener boom");
      requireThat(result.elapsed().compareTo(Duration.ofSeconds(2)), "elapsed").isLessThan(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex JSONL output is parsed into text, tool use, and write content fields.
   */
  @Test
  public void parseOutputExtractsTextToolUsesAndWrite() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      String output = """
        {"type":"session","session_id":"session-123"}
        {"type":"agent_message","message":"hello from codex"}
        {"type":"tool_call","tool_name":"functions.exec_command","arguments":{"cmd":"ls"}}
        {"type":"tool_call","tool_name":"apply_patch","arguments":{"patch":"*** Begin Patch\\n*** End Patch"}}
        """;

      ParsedOutput parsed = runner.parseOutput(output);

      requireThat(parsed.sessionId(), "sessionId").isEqualTo("session-123");
      requireThat(parsed.texts(), "texts").isEqualTo(List.of("hello from codex"));
      requireThat(parsed.toolUses(), "toolUses").isEqualTo(List.of("functions.exec_command",
        "apply_patch"));
      requireThat(parsed.writeContents(), "writeContents").isEqualTo(List.of(
        "*** Begin Patch\n*** End Patch"));
      requireThat(parsed.turns().size(), "turns").isEqualTo(1);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that turn.completed means the session is ready for the next request, not terminated.
   */
  @Test
  public void turnCompletedLeavesSessionReady() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      String output = """
        {"type":"thread.started","thread_id":"session-123"}
        {"type":"turn.started","turn_id":"turn-1"}
        {"type":"tool_call","tool_name":"functions.exec_command","arguments":{"cmd":"pwd"}}
        {"type":"turn.completed","turn_id":"turn-1"}
        """;

      CodexRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
      requireThat(parsed.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.COMPLETED);
      requireThat(parsed.state().canSubmitTurn(), "canSubmitTurn").isTrue();
      requireThat(parsed.state().currentTurnId(), "currentTurnId").isEqualTo("turn-1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that one-shot Codex execution reports terminal completion after the process exits.
   */
  @Test
  public void executeProcessMarksCompleted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofSeconds(5));
      Path outputPath = tempDir.resolve("last-message.txt");
      String codexOutput = """
        {"type":"thread.started","thread_id":"session-123"}
        {"type":"turn.started","turn_id":"turn-1"}
        {"type":"agent_message","message":"done"}
        {"type":"turn.completed","turn_id":"turn-1"}
        """;
      Path script = tempDir.resolve("emit-codex.sh");
      Files.writeString(script, """
        #!/usr/bin/env bash
        cat >/dev/null
        cat <<'EOF'
        """ + codexOutput + """
        EOF
        """, UTF_8);
      requireThat(script.toFile().setExecutable(true), "scriptExecutable").isTrue();
      List<String> command = List.of("bash", script.toString());

      CodexRunner.ProcessResult result = runner.executeProcess(command, "prompt", tempDir,
        outputPath);

      requireThat(result.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.COMPLETED);
      requireThat(result.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.COMPLETED);
      requireThat(result.state().canSubmitTurn(), "canSubmitTurn").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex run-boundary validation accepts terminal completion without a session file.
   */
  @Test
  public void completedBoundaryAccepted() throws IOException
  {
    NestedRunnerState completed = new NestedRunnerState("session-123", "turn-1",
      "turn.completed", "", NestedRunnerTurnState.COMPLETED,
      NestedRunnerSessionState.COMPLETED, false, "", "");

    requireThat(CodexRunner.reachedExpectedBoundary(completed, false), "accepted").isTrue();
  }

  /**
   * Verifies that Codex CLI exit handling treats accepted one-shot boundaries as success even if
   * the nested process returned a non-zero exit code.
   */
  @Test
  public void cliExitCodeAcceptsCompletedBoundary() throws IOException
  {
    NestedRunnerState completed = new NestedRunnerState("session-123", "turn-1",
      "turn.completed", "", NestedRunnerTurnState.COMPLETED,
      NestedRunnerSessionState.COMPLETED, false, "", "");
    CodexRunner.ProcessResult result = new CodexRunner.ProcessResult(
      new ParsedOutput(List.of("done"), List.of(), List.of(), List.of(), "session-123"),
      completed, Duration.ZERO, "", 7);

    requireThat(CodexRunner.resolveCliExitCode(result, false), "exitCode").isEqualTo(0);
  }

  /**
   * Verifies that Codex can preserve a resumable boundary for managed multi-turn callers.
   */
  @Test
  public void executeProcessPreservesResumableState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofSeconds(5));
      Path outputPath = tempDir.resolve("last-message.txt");
      String codexOutput = """
        {"type":"thread.started","thread_id":"session-123"}
        {"type":"turn.started","turn_id":"turn-1"}
        {"type":"agent_message","message":"done"}
        {"type":"turn.completed","turn_id":"turn-1"}
        """;
      Path script = tempDir.resolve("emit-codex.sh");
      Files.writeString(script, """
        #!/usr/bin/env bash
        cat >/dev/null
        cat <<'EOF'
        """ + codexOutput + """
        EOF
        """, UTF_8);
      requireThat(script.toFile().setExecutable(true), "scriptExecutable").isTrue();
      List<String> command = List.of("bash", script.toString());

      CodexRunner.ProcessResult result = runner.executeProcess(command, "prompt", tempDir,
        outputPath, null, true);

      requireThat(result.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
      requireThat(result.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.COMPLETED);
      requireThat(result.state().canSubmitTurn(), "canSubmitTurn").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex streams state updates to direct runner callers while the turn executes.
   */
  @Test
  public void executeProcessStreamsStateUpdates() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofSeconds(5));
      Path outputPath = tempDir.resolve("last-message.txt");
      String codexOutput = """
        {"type":"thread.started","thread_id":"session-123"}
        {"type":"turn.started","turn_id":"turn-1"}
        {"type":"tool_call","tool_name":"Bash","arguments":{"cmd":"pwd"}}
        {"type":"tool_result","tool_name":"Bash","output":"/workspace"}
        {"type":"turn.completed","turn_id":"turn-1"}
        """;
      Path script = tempDir.resolve("emit-codex-events.sh");
      Files.writeString(script, """
        #!/usr/bin/env bash
        cat >/dev/null
        cat <<'EOF'
        """ + codexOutput + """
        EOF
        """, UTF_8);
      requireThat(script.toFile().setExecutable(true), "scriptExecutable").isTrue();
      List<String> command = List.of("bash", script.toString());
      List<NestedRunnerEvent> events = new ArrayList<>();

      CodexRunner.ProcessResult result = runner.executeProcess(command, "prompt", tempDir,
        outputPath, null, true, events::add);

      requireThat(result.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
      requireThat(events.size(), "eventCount").isEqualTo(4);
      requireThat(events.getFirst().rawLine(), "turnStartedRawLine").
        isEqualTo("{\"type\":\"turn.started\",\"turn_id\":\"turn-1\"}");
      requireThat(events.getFirst().state().turnState(), "turnStartedState").
        isEqualTo(NestedRunnerTurnState.WAITING_FOR_MODEL);
      requireThat(events.get(1).state().turnState(), "toolCallState").
        isEqualTo(NestedRunnerTurnState.WAITING_FOR_TOOL_RESULT);
      requireThat(events.get(2).state().turnState(), "toolResultState").
        isEqualTo(NestedRunnerTurnState.WORKING);
      requireThat(events.getLast().state().sessionState(), "completedState").
        isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
      requireThat(events.getLast().rawLine(), "completedRawLine").
        isEqualTo("{\"type\":\"turn.completed\",\"turn_id\":\"turn-1\"}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex preserves an accepted boundary even if the nested process exits non-zero.
   */
  @Test
  public void executeProcessAllowsNonZeroAcceptedExit() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope, Duration.ofSeconds(5));
      Path outputPath = tempDir.resolve("last-message.txt");
      String codexOutput = """
        {"type":"thread.started","thread_id":"session-123"}
        {"type":"turn.started","turn_id":"turn-1"}
        {"type":"agent_message","message":"done"}
        {"type":"turn.completed","turn_id":"turn-1"}
        """;
      Path script = tempDir.resolve("emit-codex-nonzero.sh");
      Files.writeString(script, """
        #!/usr/bin/env bash
        cat >/dev/null
        cat <<'EOF'
        """ + codexOutput + """
        EOF
        exit 7
        """, UTF_8);
      requireThat(script.toFile().setExecutable(true), "scriptExecutable").isTrue();
      List<String> command = List.of("bash", script.toString());

      CodexRunner.ProcessResult result = runner.executeProcess(command, "prompt", tempDir,
        outputPath);

      requireThat(result.error(), "error").isEmpty();
      requireThat(result.exitCode(), "exitCode").isEqualTo(7);
      requireThat(result.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.COMPLETED);
      requireThat(CodexRunner.resolveCliExitCode(result, false), "cliExitCode").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex tool events expose the waiting-for-tool-result substate.
   */
  @Test
  public void toolCallLeavesToolWaitState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      String output = """
        {"type":"thread.started","thread_id":"session-123"}
        {"type":"turn.started","turn_id":"turn-1"}
        {"type":"tool_call","tool_name":"functions.exec_command","arguments":{"cmd":"pwd"}}
        """;

      CodexRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WORKING);
      requireThat(parsed.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.WAITING_FOR_TOOL_RESULT);
      requireThat(parsed.state().canSubmitTurn(), "canSubmitTurn").isFalse();
      requireThat(parsed.state().engineSubstate(), "engineSubstate").
        isEqualTo(NestedRunnerEngineSubstates.WAITING_FOR_TOOL_RESULT);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex tool result events clear the waiting-for-tool-result substate.
   */
  @Test
  public void toolResultLeavesWorkingState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      String output = """
        {"type":"thread.started","thread_id":"session-123"}
        {"type":"turn.started","turn_id":"turn-1"}
        {"type":"tool_call","tool_name":"functions.exec_command","arguments":{"cmd":"pwd"}}
        {"type":"tool_result","tool_name":"functions.exec_command","content":"done"}
        """;

      CodexRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WORKING);
      requireThat(parsed.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.WORKING);
      requireThat(parsed.parsed().texts(), "texts").isEmpty();
      requireThat(parsed.parsed().toolUses(), "toolUses").
        containsExactly(List.of("functions.exec_command"));
      requireThat(parsed.state().canSubmitTurn(), "canSubmitTurn").isFalse();
      requireThat(parsed.state().engineSubstate(), "engineSubstate").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex reports the model-waiting substate once a turn has started.
   */
  @Test
  public void turnStartedLeavesModelWaitState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      String output = """
        {"type":"thread.started","thread_id":"session-123"}
        {"type":"turn.started","turn_id":"turn-1"}
        """;

      CodexRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WORKING);
      requireThat(parsed.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.WAITING_FOR_MODEL);
      requireThat(parsed.state().canSubmitTurn(), "canSubmitTurn").isFalse();
      requireThat(parsed.state().engineSubstate(), "engineSubstate").
        isEqualTo(NestedRunnerEngineSubstates.WAITING_FOR_MODEL);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex does not fabricate a turn id when the engine did not emit one.
   */
  @Test
  public void turnWithoutIdLeavesCurrentTurnIdEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      String output = """
        {"type":"thread.started","thread_id":"session-123"}
        {"type":"turn.started"}
        """;

      CodexRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().currentTurnId(), "currentTurnId").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex does not invent a waiting state before any stream evidence exists.
   */
  @Test
  public void emptyOutputStaysInUnknownState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);

      CodexRunner.ParsedSessionOutput parsed = runner.parseSessionOutput("");

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.UNKNOWN);
      requireThat(parsed.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.UNKNOWN);
      requireThat(parsed.state().engineSubstate(), "engineSubstate").isEmpty();
      requireThat(parsed.state().canSubmitTurn(), "canSubmitTurn").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that direct Codex runner sessions can append turns, persist state, reload, and close.
   */
  @Test
  public void managedSessionPersistsTurnByTurnState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunnerSupport runner = new CodexRunnerSupport(scope);
      Path sessionFile = tempDir.resolve("codex-session.json");
      Path outputPath = tempDir.resolve("last-message.txt");
      CodexRunnerSupport.ManagedSession session = runner.startSession(sessionFile, "gpt-5.5",
        "high", tempDir, outputPath);
      try (session)
      {
        requireThat(session.latestState().canSubmitTurn(), "initialCanSubmitTurn").isTrue();
        requireThat(session.snapshot().turns(), "initialTurns").isEmpty();

        Path firstScript = tempDir.resolve("emit-codex-turn1.sh");
        Files.writeString(firstScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          cat <<'EOF'
          {"type":"thread.started","thread_id":"session-123"}
          {"type":"turn.started","turn_id":"turn-1"}
          {"type":"agent_message","message":"hello"}
          {"type":"turn.completed","turn_id":"turn-1"}
          EOF
          """, UTF_8);
        requireThat(firstScript.toFile().setExecutable(true), "firstScriptExecutable").isTrue();
        session.submitTurn("turn one", List.of("bash", firstScript.toString()), _ -> {});

        requireThat(session.snapshot().sessionId(), "sessionId").isEqualTo("session-123");
        requireThat(session.latestState().sessionState(), "sessionState").
          isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
        requireThat(session.latestState().canSubmitTurn(), "canSubmitTurn").isTrue();
        requireThat(session.snapshot().turns().size(), "turnCount").isEqualTo(1);
        requireThat(session.snapshot().turns().getFirst().prompt(), "firstPrompt").isEqualTo("turn one");

        Path secondScript = tempDir.resolve("emit-codex-turn2.sh");
        Files.writeString(secondScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          cat <<'EOF'
          {"type":"thread.started","thread_id":"session-123"}
          {"type":"turn.started","turn_id":"turn-2"}
          {"type":"tool_call","tool_name":"functions.exec_command","arguments":{"cmd":"pwd"}}
          {"type":"turn.completed","turn_id":"turn-2"}
          EOF
          """, UTF_8);
        requireThat(secondScript.toFile().setExecutable(true), "secondScriptExecutable").isTrue();
        session.submitTurn("turn two", List.of("bash", secondScript.toString()), _ -> {});

        requireThat(session.snapshot().turns().size(), "updatedTurnCount").isEqualTo(2);
        requireThat(session.latestState().currentTurnId(), "latestTurnId").isEqualTo("turn-2");
        requireThat(session.snapshot().toParsedOutput().turns().size(), "parsedTurns").isEqualTo(2);

        CodexRunner.CodexSession reloaded = runner.loadSession(sessionFile, "gpt-5.5", "high", tempDir);
        requireThat(reloaded.sessionId(), "reloadedSessionId").isEqualTo("session-123");
        requireThat(reloaded.latestState().sessionState(), "reloadedSessionState").
          isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
      }
      requireThat(session.latestState().sessionState(), "closedSessionState").
        isEqualTo(NestedRunnerSessionState.COMPLETED);
      requireThat(session.latestState().canSubmitTurn(), "closedCanSubmitTurn").isFalse();
      requireThat(session.snapshot().latestState().sessionState(), "closedSnapshotSessionState").
        isEqualTo(NestedRunnerSessionState.COMPLETED);
      requireThat(Files.exists(sessionFile), "sessionFileExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a failed managed Codex turn invalidates the in-memory session handle.
   */
  @Test
  public void managedSessionRejectsReuseAfterFailure() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunnerSupport runner = new CodexRunnerSupport(scope);
      Path sessionFile = tempDir.resolve("codex-session.json");
      Path outputPath = tempDir.resolve("last-message.txt");
      try (CodexRunnerSupport.ManagedSession session = runner.startSession(sessionFile, "gpt-5.5",
        "high", tempDir, outputPath))
      {
        Path firstScript = tempDir.resolve("emit-codex-success.sh");
        Files.writeString(firstScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          cat <<'EOF'
          {"type":"thread.started","thread_id":"session-123"}
          {"type":"turn.started","turn_id":"turn-1"}
          {"type":"agent_message","message":"hello"}
          {"type":"turn.completed","turn_id":"turn-1"}
          EOF
          """, UTF_8);
        requireThat(firstScript.toFile().setExecutable(true), "firstScriptExecutable").isTrue();
        session.submitTurn("turn one", List.of("bash", firstScript.toString()), _ -> {});

        Path failedScript = tempDir.resolve("emit-codex-failed.sh");
        Files.writeString(failedScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          cat <<'EOF'
          {"type":"thread.started","thread_id":"session-123"}
          {"type":"turn.started","turn_id":"turn-2"}
          EOF
          """, UTF_8);
        requireThat(failedScript.toFile().setExecutable(true), "failedScriptExecutable").isTrue();
        session.submitTurn("turn two", List.of("bash", failedScript.toString()), _ -> {});

        requireThat(session.latestState().sessionState(), "failedSessionState").
          isEqualTo(NestedRunnerSessionState.WORKING);
        requireThat(session.latestState().canSubmitTurn(), "failedCanSubmitTurn").isFalse();
        requireThat(Files.exists(sessionFile), "sessionFileDeletedAfterFailure").isFalse();
        requireThat(session.snapshot().turns().size(), "persistedTurnCountAfterFailure").isEqualTo(1);

        try
        {
          session.submitTurn("turn three");
          throw new AssertionError("Expected submitTurn() to reject an invalidated session");
        }
        catch (IllegalStateException e)
        {
          requireThat(e.getMessage(), "errorMessage").
            contains("no longer resumable after the last turn");
        }
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a timed-out managed Codex turn invalidates the in-memory session handle.
   */
  @Test
  public void managedSessionRejectsReuseAfterTimeout() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunnerSupport runner = new CodexRunnerSupport(scope, Duration.ofMillis(100));
      Path sessionFile = tempDir.resolve("codex-session.json");
      Path outputPath = tempDir.resolve("last-message.txt");
      try (CodexRunnerSupport.ManagedSession session = runner.startSession(sessionFile, "gpt-5.5",
        "high", tempDir, outputPath))
      {
        Path firstScript = tempDir.resolve("emit-codex-success.sh");
        Files.writeString(firstScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          cat <<'EOF'
          {"type":"thread.started","thread_id":"session-123"}
          {"type":"turn.started","turn_id":"turn-1"}
          {"type":"agent_message","message":"hello"}
          {"type":"turn.completed","turn_id":"turn-1"}
          EOF
          """, UTF_8);
        requireThat(firstScript.toFile().setExecutable(true), "firstScriptExecutable").isTrue();
        session.submitTurn("turn one", List.of("bash", firstScript.toString()), _ -> {});

        Path timeoutScript = tempDir.resolve("emit-codex-timeout.sh");
        Files.writeString(timeoutScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          cat <<'EOF'
          {"type":"thread.started","thread_id":"session-123"}
          {"type":"turn.started","turn_id":"turn-2"}
          EOF
          sleep 5
          """, UTF_8);
        requireThat(timeoutScript.toFile().setExecutable(true), "timeoutScriptExecutable").isTrue();
        session.submitTurn("turn two", List.of("bash", timeoutScript.toString()), _ -> {});

        requireThat(session.latestState().sessionState(), "timedOutSessionState").
          isEqualTo(NestedRunnerSessionState.TIMEOUT);
        requireThat(session.latestState().turnState(), "timedOutTurnState").
          isEqualTo(NestedRunnerTurnState.TIMEOUT);
        requireThat(session.latestState().canSubmitTurn(), "timedOutCanSubmitTurn").isFalse();
        requireThat(Files.exists(sessionFile), "sessionFileDeletedAfterTimeout").isFalse();
        requireThat(session.snapshot().turns().size(), "persistedTurnCountAfterTimeout").isEqualTo(1);

        try
        {
          session.submitTurn("turn three");
          throw new AssertionError("Expected submitTurn() to reject an invalidated session");
        }
        catch (IllegalStateException e)
        {
          requireThat(e.getMessage(), "errorMessage").
            contains("no longer resumable after the last turn");
        }
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a Codex save failure invalidates the session instead of outrunning disk state.
   */
  @Test
  public void saveFailInvalidatesManagedSession() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunnerSupport runner = new CodexRunnerSupport(scope);
      Path sessionFile = tempDir.resolve("codex-session.json");
      Path outputPath = tempDir.resolve("last-message.txt");
      try (CodexRunnerSupport.ManagedSession session = runner.startSession(sessionFile, "gpt-5.5",
        "high", tempDir, outputPath))
      {
        Path firstScript = tempDir.resolve("emit-codex-success.sh");
        Files.writeString(firstScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          cat <<'EOF'
          {"type":"thread.started","thread_id":"session-123"}
          {"type":"turn.started","turn_id":"turn-1"}
          {"type":"agent_message","message":"hello"}
          {"type":"turn.completed","turn_id":"turn-1"}
          EOF
          """, UTF_8);
        requireThat(firstScript.toFile().setExecutable(true), "firstScriptExecutable").isTrue();
        session.submitTurn("turn one", List.of("bash", firstScript.toString()), _ -> {});
        Files.deleteIfExists(sessionFile);
        Files.createDirectory(sessionFile);

        Path secondScript = tempDir.resolve("emit-codex-second-success.sh");
        Files.writeString(secondScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          cat <<'EOF'
          {"type":"thread.started","thread_id":"session-123"}
          {"type":"turn.started","turn_id":"turn-2"}
          {"type":"agent_message","message":"hello again"}
          {"type":"turn.completed","turn_id":"turn-2"}
          EOF
          """, UTF_8);
        requireThat(secondScript.toFile().setExecutable(true), "secondScriptExecutable").isTrue();

        try
        {
          session.submitTurn("turn two", List.of("bash", secondScript.toString()), _ -> {});
          throw new AssertionError("Expected submitTurn() to fail when session persistence fails");
        }
        catch (IOException e)
        {
          requireThat(e.getMessage(), "errorMessage").contains("Session file");
        }
        Files.deleteIfExists(sessionFile);

        requireThat(session.latestState().sessionState(), "latestState.sessionState").
          isEqualTo(NestedRunnerSessionState.ERROR);
        requireThat(session.latestState().canSubmitTurn(), "latestState.canSubmitTurn").isFalse();
        requireThat(session.snapshot().latestState().sessionState(),
          "snapshot.latestState.sessionState").isEqualTo(NestedRunnerSessionState.ERROR);
        requireThat(session.snapshot().latestState().canSubmitTurn(),
          "snapshot.latestState.canSubmitTurn").isFalse();
        requireThat(session.snapshot().turns().size(), "persistedTurnCountAfterSaveFailure").
          isEqualTo(1);

        try
        {
          session.submitTurn("turn three");
          throw new AssertionError("Expected submitTurn() to reject an invalidated session");
        }
        catch (IllegalStateException e)
        {
          requireThat(e.getMessage(), "errorMessage").
            contains("no longer resumable after the last turn");
        }
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a session file cannot be reused from a different working directory.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*cwd does not match.*")
  public void loadSessionRejectsMismatchedCwd() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      Path firstCwd = Files.createDirectories(tempDir.resolve("first"));
      Path secondCwd = Files.createDirectories(tempDir.resolve("second"));
      Path sessionFile = secondCwd.resolve("codex-session.json");

      CodexRunner.CodexSession initial = runner.loadSession(sessionFile, "gpt-5.5", "high",
        secondCwd);
      CodexRunner.ParsedSessionOutput turn = runner.parseSessionOutput("""
        {"type":"thread.started","thread_id":"session-123"}
        {"type":"turn.started","turn_id":"turn-1"}
        {"type":"turn.completed","turn_id":"turn-1"}
        """);
      CodexRunner.CodexSession mismatchedCwd = initial.appendTurn("turn one", turn.parsed(),
        turn.state());
      mismatchedCwd = new CodexRunner.CodexSession(mismatchedCwd.sessionId(), mismatchedCwd.model(),
        mismatchedCwd.effort(), firstCwd.toRealPath().toString(), mismatchedCwd.turns(),
        mismatchedCwd.latestState());
      Files.writeString(sessionFile, scope.getJsonMapper().writeValueAsString(mismatchedCwd), UTF_8);

      runner.loadSession(sessionFile, "gpt-5.5", "high", secondCwd);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a persisted Codex session cannot resume unless the last state is ready for another turn.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*not ready for another turn.*")
  public void loadSessionRejectsNonResumableState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      Path sessionFile = tempDir.resolve("codex-session.json");
      CodexRunner.CodexSession session = new CodexRunner.CodexSession("session-123", "gpt-5.5",
        "high", tempDir.toRealPath().toString(),
        List.of(new CodexRunner.CodexSessionTurn("turn-1", List.of("answer"), List.of(), List.of())),
        new NestedRunnerState("session-123", "turn-1", "turn.failed", "",
          NestedRunnerTurnState.ERROR, NestedRunnerSessionState.ERROR, false, "", "boom"));
      runner.saveSession(sessionFile, session);

      runner.loadSession(sessionFile, "gpt-5.5", "high", tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a persisted Codex session with turns must retain the native session id.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*turns but no native session id.*")
  public void loadSessionRejectsTurnsWithoutSessionId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      Path sessionFile = tempDir.resolve("codex-session.json");
      CodexRunner.CodexSession session = new CodexRunner.CodexSession("", "gpt-5.5",
        "high", tempDir.toRealPath().toString(),
        List.of(new CodexRunner.CodexSessionTurn("turn-1", List.of("answer"), List.of(), List.of())),
        new NestedRunnerState("", "turn-1", "turn.completed", "",
          NestedRunnerTurnState.COMPLETED, NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST,
          true, "", ""));
      runner.saveSession(sessionFile, session);

      runner.loadSession(sessionFile, "gpt-5.5", "high", tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a persisted native Codex session id is not accepted without matching CAT turns.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*native session id but no turns.*")
  public void loadSessionRejectsSessionIdWithoutTurns() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      Path sessionFile = tempDir.resolve("codex-session.json");
      CodexRunner.CodexSession session = new CodexRunner.CodexSession("session-123", "gpt-5.5",
        "high", tempDir.toRealPath().toString(), List.of(),
        new NestedRunnerState("session-123", "", "turn.completed", "",
          NestedRunnerTurnState.COMPLETED, NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST,
          true, "", ""));
      runner.saveSession(sessionFile, session);

      runner.loadSession(sessionFile, "gpt-5.5", "high", tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that loading a persisted Codex session fills in the latest state session id when omitted.
   */
  @Test
  public void loadSessionNormalizesBlankStateId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      Path sessionFile = tempDir.resolve("codex-session.json");
      CodexRunner.CodexSession session = new CodexRunner.CodexSession("session-123", "gpt-5.5",
        "high", tempDir.toRealPath().toString(),
        List.of(new CodexRunner.CodexSessionTurn("turn-1", List.of("answer"), List.of(), List.of())),
        new NestedRunnerState("", "", "turn.completed", "",
          NestedRunnerTurnState.COMPLETED, NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST,
          true, "", ""));
      runner.saveSession(sessionFile, session);

      CodexRunner.CodexSession loaded = runner.loadSession(sessionFile, "gpt-5.5", "high",
        tempDir);

      requireThat(loaded.latestState().sessionId(), "latestState.sessionId").
        isEqualTo("session-123");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that managed Codex session files must stay under the requested cwd.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Session file must be under cwd.*")
  public void saveSessionRejectsPathOutsideCwd() throws IOException
  {
    Path parent = Files.createTempDirectory("test-");
    Path cwd = parent.resolve("cwd");
    Path outside = parent.resolve("outside");
    Files.createDirectories(cwd);
    Files.createDirectories(outside);
    try (TestCodexTool scope = new TestCodexTool(cwd, cwd))
    {
      CodexRunner runner = new CodexRunner(scope);
      Path sessionFile = outside.resolve("codex-session.json");
      CodexRunner.CodexSession session = new CodexRunner.CodexSession("", "gpt-5.5",
        "high", cwd.toRealPath().toString(), List.of(),
        new NestedRunnerState("", "", "", "", NestedRunnerTurnState.UNKNOWN,
          NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST, true, "", ""));

      runner.saveSession(sessionFile, session);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(parent);
    }
  }

  /**
   * Verifies that a symlinked ancestor cannot redirect a managed Codex session file outside cwd.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Session file must be under cwd.*")
  public void saveSessionRejectsSymlinkAncestor() throws IOException
  {
    Path parent = Files.createTempDirectory("test-");
    Path cwd = parent.resolve("cwd");
    Path outside = parent.resolve("outside");
    Files.createDirectories(cwd);
    Files.createDirectories(outside);
    try
    {
      Files.createSymbolicLink(cwd.resolve("link"), outside);
    }
    catch (UnsupportedOperationException | IOException e)
    {
      throw new org.testng.SkipException("Symbolic links are not available in this test environment", e);
    }
    try (TestCodexTool scope = new TestCodexTool(cwd, cwd))
    {
      CodexRunner runner = new CodexRunner(scope);
      Path sessionFile = cwd.resolve("link/newdir/codex-session.json");
      CodexRunner.CodexSession session = new CodexRunner.CodexSession("", "gpt-5.5",
        "high", cwd.toRealPath().toString(), List.of(),
        new NestedRunnerState("", "", "", "", NestedRunnerTurnState.UNKNOWN,
          NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST, true, "", ""));
      runner.saveSession(sessionFile, session);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(parent);
    }
  }

  /**
   * Verifies that relative Codex session paths are resolved under the requested cwd.
   */
  @Test
  public void saveSessionResolvesRelativePath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestCodexTool scope = new TestCodexTool(tempDir, tempDir))
    {
      CodexRunner runner = new CodexRunner(scope);
      Path sessionFile = Path.of(".cat/work/codex-session.json");
      CodexRunner.CodexSession session = new CodexRunner.CodexSession("", "gpt-5.5",
        "high", tempDir.toRealPath().toString(), List.of(),
        new NestedRunnerState("", "", "", "", NestedRunnerTurnState.UNKNOWN,
          NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST, true, "", ""));

      runner.saveSession(sessionFile, session);
      CodexRunner.CodexSession loaded = runner.loadSession(sessionFile, "gpt-5.5", "high",
        tempDir);

      requireThat(Files.exists(tempDir.resolve(".cat/work/codex-session.json")), "sessionExists").
        isTrue();
      requireThat(loaded.model(), "loaded.model").isEqualTo("gpt-5.5");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
