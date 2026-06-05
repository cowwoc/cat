/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.engine.ClaudeRunner;
import io.github.cowwoc.cat.engine.NestedRunnerEvent;
import io.github.cowwoc.cat.engine.NestedRunnerEngineSubstates;
import io.github.cowwoc.cat.engine.NestedRunnerSessionState;
import io.github.cowwoc.cat.engine.NestedRunnerState;
import io.github.cowwoc.cat.engine.NestedRunnerTurnState;
import io.github.cowwoc.cat.tool.skills.PrimingMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

/**
 * Tests for {@link ClaudeRunner}.
 */
public final class ClaudeRunnerTest
{
  /**
   * Verifies that createIsolatedConfig copies plugin source files into the cache directory.
   */
  @Test
  public void isolatedConfigCopiesPluginSource() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create a mock source config directory
      Path sourceConfig = tempDir.resolve("source-config");
      Files.createDirectories(sourceConfig);
      Files.writeString(sourceConfig.resolve("settings.json"), "{}");

      // Create a mock plugin source directory with a skill file
      Path pluginSource = tempDir.resolve("plugin");
      Path skillDir = pluginSource.resolve("skills").resolve("test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), "# Test Skill");
      Files.writeString(skillDir.resolve("first-use.md"), "# Instructions");

      // Create a mock jlink binary directory
      Path jlinkBin = tempDir.resolve("jlink-bin");
      Files.createDirectories(jlinkBin);
      Files.writeString(jlinkBin.resolve("test-tool"), "#!/bin/bash\necho hello");

      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        runner.createIsolatedConfig(sourceConfig, pluginSource, jlinkBin, "2.1");

        String isolatedDir = runner.getIsolatedConfigDir();
        requireThat(isolatedDir, "isolatedDir").isNotBlank();

        Path isolatedPath = Path.of(isolatedDir);

        // Verify original config file was copied
        requireThat(Files.exists(isolatedPath.resolve("settings.json")),
          "settingsCopied").isTrue();

        // Verify plugin source files were copied into the cache
        Path cacheSkillDir = isolatedPath.resolve("plugins").resolve("cache").
          resolve("cat").resolve("cat").resolve("2.1").
          resolve("skills").resolve("test-skill");
        requireThat(Files.exists(cacheSkillDir.resolve("SKILL.md")),
          "skillMdInCache").isTrue();
        requireThat(Files.exists(cacheSkillDir.resolve("first-use.md")),
          "firstUseMdInCache").isTrue();
        requireThat(Files.readString(cacheSkillDir.resolve("SKILL.md")),
          "skillMdContent").isEqualTo("# Test Skill");

        // Verify jlink binaries were copied
        Path cacheBinDir = isolatedPath.resolve("plugins").resolve("cache").
          resolve("cat").resolve("cat").resolve("2.1").
          resolve("client").resolve("bin");
        requireThat(Files.exists(cacheBinDir.resolve("test-tool")),
          "testToolInCache").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that close() deletes the isolated config directory.
   */
  @Test
  public void closeDeletesIsolatedConfig() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sourceConfig = tempDir.resolve("source-config");
      Files.createDirectories(sourceConfig);
      Path pluginSource = tempDir.resolve("plugin");
      Files.createDirectories(pluginSource);
      Path jlinkBin = tempDir.resolve("jlink-bin");
      Files.createDirectories(jlinkBin);

      Path isolatedPath;
      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        runner.createIsolatedConfig(sourceConfig, pluginSource, jlinkBin, "2.1");
        isolatedPath = Path.of(runner.getIsolatedConfigDir());
        requireThat(Files.exists(isolatedPath), "existsBeforeClose").isTrue();
      }
      // After close(), the directory should be deleted
      requireThat(Files.exists(isolatedPath), "existsAfterClose").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that createIsolatedConfig replaces existing cache contents rather than merging.
   */
  @Test
  public void isolatedConfigReplacesExistingCache() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create source config with pre-existing cache
      Path sourceConfig = tempDir.resolve("source-config");
      Path existingCache = sourceConfig.resolve("plugins").resolve("cache").
        resolve("cat").resolve("cat").resolve("2.1");
      Files.createDirectories(existingCache);
      Files.writeString(existingCache.resolve("old-file.txt"), "stale content");

      // Create new plugin source (should replace the old cache)
      Path pluginSource = tempDir.resolve("plugin");
      Files.createDirectories(pluginSource);
      Files.writeString(pluginSource.resolve("new-file.txt"), "fresh content");

      Path jlinkBin = tempDir.resolve("jlink-bin");
      Files.createDirectories(jlinkBin);

      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        runner.createIsolatedConfig(sourceConfig, pluginSource, jlinkBin, "2.1");

        Path isolatedCache = Path.of(runner.getIsolatedConfigDir()).resolve("plugins").
          resolve("cache").resolve("cat").resolve("cat").resolve("2.1");

        // Old file should be gone (cache was replaced, not merged)
        requireThat(Files.exists(isolatedCache.resolve("old-file.txt")),
          "oldFileRemoved").isFalse();
        // New file should be present
        requireThat(Files.exists(isolatedCache.resolve("new-file.txt")),
          "newFilePresent").isTrue();
        requireThat(Files.readString(isolatedCache.resolve("new-file.txt")),
          "newFileContent").isEqualTo("fresh content");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildCommand rejects a missing model.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*model.*")
  public void buildCommandRejectsMissingModel() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      runner.buildCommand("", "medium", "", "");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildCommand rejects a missing effort.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*effort.*")
  public void buildCommandRejectsMissingEffort() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      runner.buildCommand("haiku", "", "", "");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Claude resume sessions are passed through as native CLI resume flags.
   */
  @Test
  public void buildCommandIncludesResumeSession() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      List<String> command = runner.buildCommand("claude-sonnet-4-5", "high", "", "",
        "session-123");

      requireThat(command, "command").contains("--resume", "session-123");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that resumed Claude session ids cannot be interpreted as CLI flags.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*must not start with.*")
  public void buildCommandRejectsFlagLikeResumeId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      runner.buildCommand("claude-sonnet-4-5", "high", "", "", "--bad-session");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws IOException when the --prompt-file file does not exist.
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = "(?s).*--prompt-file file not found.*")
  public void runThrowsWhenPromptFileNotFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      PrintStream out = new PrintStream(new ByteArrayOutputStream(), false, UTF_8);
      ClaudeRunner.run(scope,
        new String[]{"--prompt-file", tempDir.resolve("nonexistent-prompt.txt").toString(),
          "--model", "haiku", "--effort", "medium"},
        out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() reads prompt content from the file specified by --prompt-file.
   * <p>
   * Confirms the file is read (no IOException) when it exists; the process launch itself
   * may fail if the Claude CLI binary is not available in the test environment,
   * but the file-read step succeeds.
   */
  @Test
  public void runReadsPromptFromFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path promptFile = tempDir.resolve("prompt.txt");
      Files.writeString(promptFile, "Hello from file", UTF_8);

      PrintStream out = new PrintStream(new ByteArrayOutputStream(), false, UTF_8);
      try
      {
        // The file exists and is readable — no IOException from file reading.
        // Process launch may fail if the Claude CLI binary is unavailable in the test environment.
        ClaudeRunner.run(scope, new String[]{"--prompt-file", promptFile.toString(),
          "--model", "haiku", "--effort", "medium"}, out);
      }
      catch (IOException e)
      {
        // Process launch failures are acceptable; file-read failures are not.
        // Reject any IOException that originates from the --prompt-file file-read step.
        requireThat(e.getMessage(), "errorMessage").doesNotContain("--prompt-file file not found");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that assistant priming messages are serialized as assistant stream-json messages.
   */
  @Test
  public void buildInputSupportsAssistantPriming() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String input = runner.buildInput(List.of(
        new PrimingMessage.UserMessage("Turn 1 question"),
        new PrimingMessage.AssistantMessage("Turn 1 answer")), List.of("Turn 2 question"),
        List.of());

      requireThat(input, "input").contains("\"type\":\"assistant\"");
      requireThat(input, "input").contains("\"text\":\"Turn 1 answer\"");
      requireThat(input, "input").contains("\"text\":\"Turn 2 question\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a completed Claude result leaves the CAT-managed session ready for another turn.
   */
  @Test
  public void resultLeavesSessionReady() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String output = """
        {"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"hello"}]}}
        {"type":"result","result":"done"}
        """;

      ClaudeRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
      requireThat(parsed.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.COMPLETED);
      requireThat(parsed.state().canSubmitTurn(), "canSubmitTurn").isTrue();
      requireThat(parsed.parsed().sessionId(), "sessionId").isEqualTo("session-123");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that one-shot Claude execution reports terminal completion after the process exits.
   */
  @Test
  public void executeProcessMarksCompleted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String streamOutput = """
        {"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"hello"}]}}
        {"type":"result","result":"done","session_id":"session-123"}
        """;
      Path script = tempDir.resolve("emit-claude.sh");
      Files.writeString(script, """
        #!/usr/bin/env bash
        cat >/dev/null
        cat <<'EOF'
        """ + streamOutput + """
        EOF
        """, UTF_8);
      requireThat(script.toFile().setExecutable(true), "scriptExecutable").isTrue();
      List<String> command = List.of("bash", script.toString());

      ClaudeRunner.ProcessResult result = runner.executeProcess(command, "{}", tempDir);

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
   * Verifies that Claude can preserve a resumable boundary for managed multi-turn callers.
   */
  @Test
  public void executeProcessCanPreserveResumableState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String streamOutput = """
        {"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"hello"}]}}
        {"type":"result","result":"done","session_id":"session-123"}
        """;
      Path script = tempDir.resolve("emit-claude.sh");
      Files.writeString(script, """
        #!/usr/bin/env bash
        cat >/dev/null
        cat <<'EOF'
        """ + streamOutput + """
        EOF
        """, UTF_8);
      requireThat(script.toFile().setExecutable(true), "scriptExecutable").isTrue();
      List<String> command = List.of("bash", script.toString());

      ClaudeRunner.ProcessResult result = runner.executeProcess(command, "{}", tempDir, true);

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
   * Verifies that a blocked callback does not prevent Claude timeout cleanup from returning.
   */
  @Test
  public void timeoutReturnsWhenCallbackBlocks() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope, Duration.ofMillis(200)))
    {
      List<String> command = List.of("bash", "-c", "printf '{\"type\":\"assistant\"," +
        "\"session_id\":\"session-123\",\"message\":{\"content\":[{\"type\":\"text\"," +
        "\"text\":\"hello\"}]}}\\n'; sleep 0.15");
      CountDownLatch callbackStarted = new CountDownLatch(1);
      CountDownLatch releaseCallback = new CountDownLatch(1);

      ClaudeRunner.ProcessResult result;
      try
      {
        result = runner.executeProcess(command, "{}", tempDir, true, event ->
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
   * Verifies that a failing Claude callback aborts promptly instead of waiting for process timeout.
   */
  @Test
  public void executeProcessReturnsWhenCallbackFails() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope, Duration.ofMillis(200)))
    {
      String assistantEvent = String.join("",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\",",
        "\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}}");
      List<String> command = List.of("bash", "-c", """
        printf '%%s\n' '%s'
        sleep 5
        """.formatted(assistantEvent));

      ClaudeRunner.ProcessResult result = runner.executeProcess(command, "{}", tempDir, true, _ ->
      {
        throw new IllegalStateException("listener boom");
      });

      requireThat(result.error(), "error").contains("listener boom");
      requireThat(result.elapsed().compareTo(Duration.ofMillis(320)), "elapsed").isLessThan(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Claude streams state updates through the public executeProcess callback path.
   */
  @Test
  public void executeProcessStreamsStateUpdates() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String assistantEvent = String.join("",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\",\"message\":{\"content\":[",
        "{\"type\":\"tool_use\",\"name\":\"Bash\",\"input\":{\"cmd\":\"pwd\"}}]}}");
      String toolEvent = String.join("",
        "{\"type\":\"tool\",\"content\":[",
        "{\"type\":\"tool_result\",\"tool_use_id\":\"tu1\",\"content\":\"/workspace\"}]}");
      String resultEvent = "{\"type\":\"result\",\"result\":\"done\",\"session_id\":\"session-123\"}";
      Path script = tempDir.resolve("emit-claude-events.sh");
      Files.writeString(script, """
        #!/usr/bin/env bash
        cat >/dev/null
        printf '%%s\n' \
          '%s' \
          '%s' \
          '%s'
        """.formatted(assistantEvent, toolEvent, resultEvent), UTF_8);
      requireThat(script.toFile().setExecutable(true), "scriptExecutable").isTrue();
      List<String> command = List.of("bash", script.toString());
      List<NestedRunnerEvent> events = new ArrayList<>();
      ClaudeRunner.ProcessResult result = runner.executeProcess(command, "{}", tempDir, false,
        events::add);

      requireThat(result.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.COMPLETED);
      requireThat(events.size(), "eventCount").isGreaterThanOrEqualTo(3);
      requireThat(events.getFirst().rawLine(), "firstRawLine").isEqualTo(assistantEvent);
      requireThat(events.getFirst().state().turnState(), "firstTurnState").
        isEqualTo(NestedRunnerTurnState.WAITING_FOR_TOOL_RESULT);
      requireThat(events.getFirst().state().engineSubstate(), "firstEngineSubstate").
        isEqualTo(NestedRunnerEngineSubstates.WAITING_FOR_TOOL_RESULT);
      requireThat(events.get(1).state().turnState(), "secondTurnState").
        isEqualTo(NestedRunnerTurnState.WORKING);
      requireThat(events.get(1).rawLine(), "secondRawLine").isEqualTo(toolEvent);
      requireThat(events.getLast().state().sessionState(), "lastSessionState").
        isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
      requireThat(events.getLast().rawLine(), "lastRawLine").isEqualTo(resultEvent);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Claude one-shot boundary validation accepts terminal completion without a session file.
   */
  @Test
  public void completedBoundaryAccepted() throws IOException
  {
    NestedRunnerState completed = new NestedRunnerState("session-123", "",
      "result", "", NestedRunnerTurnState.COMPLETED,
      NestedRunnerSessionState.COMPLETED, false, "", "");

    requireThat(ClaudeRunner.reachedExpectedBoundary(completed, false), "accepted").isTrue();
  }

  /**
   * Verifies that Claude CLI exit handling treats accepted one-shot boundaries as success even if
   * the nested process returned a non-zero exit code.
   */
  @Test
  public void cliExitCodeAcceptsCompletedBoundary() throws IOException
  {
    NestedRunnerState completed = new NestedRunnerState("session-123", "",
      "result", "", NestedRunnerTurnState.COMPLETED,
      NestedRunnerSessionState.COMPLETED, false, "", "");
    ClaudeRunner.ProcessResult result = new ClaudeRunner.ProcessResult(
      new ClaudeRunner.ParsedOutput(List.of("done"), List.of(), List.of(), List.of(), "session-123"),
      completed, Duration.ZERO, "", 7);

    requireThat(ClaudeRunner.resolveCliExitCode(result, false), "exitCode").isEqualTo(0);
  }

  /**
   * Verifies that Claude parsing does not depend on JSON key order for relevant events.
   */
  @Test
  public void parseOutputIgnoresJsonKeyOrder() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String output = """
        {"session_id":"session-123","message":{"content":[{"text":"hello","type":"text"}]},"type":"assistant"}
        {"session_id":"session-123","result":"done","type":"result"}
        """;

      ClaudeRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
      requireThat(parsed.parsed().texts(), "texts").contains("hello");
      requireThat(parsed.parsed().texts(), "texts").contains("done");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Claude ignores irrelevant non-JSON chatter and user echo lines while preserving
   * valid assistant/result events.
   */
  @Test
  public void parseOutputIgnoresIrrelevantChatter() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String output = """
        launching claude
        {"type":"user","message":{"content":[{"type":"text","text":"echoed user prompt"}]}}
        {"session_id":"session-123","message":{"content":[{"text":"hello","type":"text"}]},"type":"assistant"}
        {"session_id":"session-123","result":"done","type":"result"}
        trailing stderr chatter
        """;

      ClaudeRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
      requireThat(parsed.parsed().texts(), "texts").containsExactly(List.of("hello", "done"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that malformed streamed Claude JSON is returned as a controlled runner error.
   */
  @Test
  public void executeProcessReportsMalformedJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path script = tempDir.resolve("emit-bad-claude.sh");
      Files.writeString(script, """
        #!/usr/bin/env bash
        cat >/dev/null
        printf '%s\n' '{"type":"assistant"'
        """, UTF_8);
      requireThat(script.toFile().setExecutable(true), "scriptExecutable").isTrue();
      List<String> command = List.of("bash", script.toString());

      ClaudeRunner.ProcessResult result = runner.executeProcess(command, "{}", tempDir);

      requireThat(result.error(), "error").isNotBlank();
      requireThat(result.parsed().texts(), "texts").isEmpty();
      requireThat(result.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.UNKNOWN);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that malformed user chatter is ignored while Claude waits for a tool result.
   */
  @Test
  public void malformedUserEventIgnoredWhileWaiting() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String output = String.join("\n",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\"," +
          "\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Bash\"," +
          "\"input\":{\"cmd\":\"pwd\"}}]}}",
        "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"ignored\"}]",
        "{\"type\":\"tool\",\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"tu1\"," +
          "\"content\":\"/workspace\"}]}",
        "{\"type\":\"result\",\"result\":\"done\",\"session_id\":\"session-123\"}");

      ClaudeRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
      requireThat(parsed.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.COMPLETED);
      requireThat(parsed.parsed().texts(), "texts").containsExactly(List.of("done"));
      requireThat(parsed.parsed().toolUses(), "toolUses").containsExactly(List.of("Bash"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Claude preserves an accepted boundary even if the nested process exits non-zero.
   */
  @Test
  public void executeProcessAllowsNonZeroAcceptedExit() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String streamOutput = """
        {"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"hello"}]}}
        {"type":"result","result":"done","session_id":"session-123"}
        """;
      Path script = tempDir.resolve("emit-claude-nonzero.sh");
      Files.writeString(script, """
        #!/usr/bin/env bash
        cat >/dev/null
        cat <<'EOF'
        """ + streamOutput + """
        EOF
        exit 7
        """, UTF_8);
      requireThat(script.toFile().setExecutable(true), "scriptExecutable").isTrue();
      List<String> command = List.of("bash", script.toString());

      ClaudeRunner.ProcessResult result = runner.executeProcess(command, "{}", tempDir);

      requireThat(result.error(), "error").isEmpty();
      requireThat(result.exitCode(), "exitCode").isEqualTo(7);
      requireThat(result.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.COMPLETED);
      requireThat(ClaudeRunner.resolveCliExitCode(result, false), "cliExitCode").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Claude tool_use blocks expose the waiting-for-tool-result substate.
   */
  @Test
  public void toolUseLeavesSessionWaitingForToolResult() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String output = String.join("\n",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\"," +
          "\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Bash\"," +
          "\"input\":{\"cmd\":\"pwd\"}}]}}");

      ClaudeRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

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
   * Verifies that Claude clears the tool-wait substate once tool_result output arrives.
   */
  @Test
  public void toolResultClearsWaitingState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String output = String.join("\n",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\"," +
          "\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Bash\"," +
          "\"input\":{\"cmd\":\"pwd\"}}]}}",
        "{\"type\":\"tool\",\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"tu1\"," +
          "\"content\":\"/workspace\"}]}");

      ClaudeRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WORKING);
      requireThat(parsed.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.WORKING);
      requireThat(parsed.state().canSubmitTurn(), "canSubmitTurn").isFalse();
      requireThat(parsed.state().engineSubstate(), "engineSubstate").isEmpty();
      requireThat(parsed.parsed().toolUses(), "toolUses").containsExactly(List.of("Bash"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that user-wrapped tool_result blocks also clear the tool-wait substate.
   */
  @Test
  public void userToolResultClearsWaitingState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String output = String.join("\n",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\"," +
          "\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Bash\"," +
          "\"input\":{\"cmd\":\"pwd\"}}]}}",
        "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"tool_result\"," +
          "\"tool_use_id\":\"tu1\",\"content\":\"/workspace\"}]}}");

      ClaudeRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.WORKING);
      requireThat(parsed.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.WORKING);
      requireThat(parsed.state().canSubmitTurn(), "canSubmitTurn").isFalse();
      requireThat(parsed.state().engineSubstate(), "engineSubstate").isEmpty();
      requireThat(parsed.parsed().toolUses(), "toolUses").containsExactly(List.of("Bash"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Claude does not invent a waiting state before any stream evidence exists.
   */
  @Test
  public void emptyOutputStaysInUnknownState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      ClaudeRunner.ParsedSessionOutput parsed = runner.parseSessionOutput("");

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
   * Verifies that Claude error events surface as terminal error state instead of being skipped.
   */
  @Test
  public void errorEventSurfacesErrorState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String output = """
        {"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"hello"}]}}
        {"type":"error","session_id":"session-123","message":"boom"}
        """;

      ClaudeRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.ERROR);
      requireThat(parsed.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.ERROR);
      requireThat(parsed.state().canSubmitTurn(), "canSubmitTurn").isFalse();
      requireThat(parsed.state().error(), "error").isEqualTo("boom");
      requireThat(parsed.state().currentTurnId(), "currentTurnId").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Claude result events carrying failure metadata surface as terminal error state.
   */
  @Test
  public void failingResultSurfacesErrorState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String output = """
        {"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"hello"}]}}
        {"type":"result","session_id":"session-123","subtype":"error","is_error":true,"result":"boom"}
        """;

      ClaudeRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().sessionState(), "sessionState").
        isEqualTo(NestedRunnerSessionState.ERROR);
      requireThat(parsed.state().turnState(), "turnState").
        isEqualTo(NestedRunnerTurnState.ERROR);
      requireThat(parsed.state().canSubmitTurn(), "canSubmitTurn").isFalse();
      requireThat(parsed.state().error(), "error").isEqualTo("boom");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple assistant chunks in one turn do not fabricate extra turn ids.
   */
  @Test
  public void multiChunkAssistantStaysSingleTurn() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      String output = """
        {"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"first"}]}}
        {"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"second"}]}}
        {"type":"result","result":"done","session_id":"session-123"}
        """;

      ClaudeRunner.ParsedSessionOutput parsed = runner.parseSessionOutput(output);

      requireThat(parsed.state().currentTurnId(), "currentTurnId").isEmpty();
      requireThat(parsed.parsed().turns().size(), "turnCount").isEqualTo(1);
      requireThat(parsed.parsed().turns().getFirst().texts(), "turnTexts").
        isEqualTo(List.of("first", "second", "done"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that direct Claude runner sessions can append turns, persist state, reload, and close.
   */
  @Test
  public void managedSessionPersistsTurnByTurnState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = tempDir.resolve("claude-session.json");
      String firstTurnResult = String.join("",
        "{\"type\":\"result\",\"subtype\":\"success\",\"duration_ms\":1,",
        "\"duration_api_ms\":1,\"is_error\":false,\"num_turns\":1,",
        "\"result\":\"done\",\"session_id\":\"session-123\",\"total_cost_usd\":0}");
      String secondTurnToolUse = String.join("",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\",\"message\":{\"content\":[",
        "{\"type\":\"tool_use\",\"name\":\"Bash\",\"input\":{\"cmd\":\"pwd\"}}]}}");
      String secondTurnAssistantText = String.join("",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\",\"message\":{\"content\":[",
        "{\"type\":\"text\",\"text\":\"done again\"}]}}");
      String secondTurnResult = String.join("",
        "{\"type\":\"result\",\"subtype\":\"success\",\"duration_ms\":1,",
        "\"duration_api_ms\":1,\"is_error\":false,\"num_turns\":2,",
        "\"result\":\"done\",\"session_id\":\"session-123\",\"total_cost_usd\":0}");
      ClaudeRunner.ManagedSession session = runner.startSession(sessionFile,
        "claude-sonnet-4-5", "high", tempDir);
      try (session)
      {
        requireThat(session.latestState().canSubmitTurn(), "initialCanSubmitTurn").isTrue();
        requireThat(session.snapshot().turns(), "initialTurns").isEmpty();

        Path firstScript = tempDir.resolve("emit-claude-turn1.sh");
        Files.writeString(firstScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          printf '%%s\n' \
            '{"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"hello"}]}}' \
            '%s'
          """.formatted(firstTurnResult), UTF_8);
        requireThat(firstScript.toFile().setExecutable(true), "firstScriptExecutable").isTrue();
        session.submitTurn("turn one", List.of("bash", firstScript.toString()), "{}",
          _ -> {});

        requireThat(session.snapshot().sessionId(), "sessionId").isEqualTo("session-123");
        requireThat(session.latestState().sessionState(), "sessionState").
          isEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
        requireThat(session.latestState().canSubmitTurn(), "canSubmitTurn").isTrue();
        requireThat(session.snapshot().turns().size(), "turnCount").isEqualTo(1);
        requireThat(session.snapshot().turns().getFirst().prompt(), "firstPrompt").isEqualTo("turn one");

        Path secondScript = tempDir.resolve("emit-claude-turn2.sh");
        Files.writeString(secondScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          printf '%%s\n' \
            '%s' \
            '%s' \
            '%s'
          """.formatted(secondTurnToolUse, secondTurnAssistantText, secondTurnResult), UTF_8);
        requireThat(secondScript.toFile().setExecutable(true), "secondScriptExecutable").isTrue();
        session.submitTurn("turn two", List.of("bash", secondScript.toString()), "{}",
          _ -> {});

        requireThat(session.snapshot().turns().size(), "updatedTurnCount").isEqualTo(2);
        requireThat(session.latestState().canSubmitTurn(), "updatedCanSubmitTurn").isTrue();
        requireThat(session.snapshot().toParsedOutput().turns().size(), "parsedTurns").isEqualTo(2);

        ClaudeRunner.ClaudeSession reloaded = runner.loadSession(sessionFile, "claude-sonnet-4-5",
          "high", tempDir);
        requireThat(reloaded.turns().size(), "reloadedTurnCount").isEqualTo(2);
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
   * Verifies that a failed managed Claude turn invalidates the in-memory session handle.
   */
  @Test
  public void managedSessionRejectsReuseAfterFailure() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = tempDir.resolve("claude-session.json");
      String firstTurnResult = String.join("",
        "{\"type\":\"result\",\"subtype\":\"success\",\"duration_ms\":1,",
        "\"duration_api_ms\":1,\"is_error\":false,\"num_turns\":1,",
        "\"result\":\"done\",\"session_id\":\"session-123\",\"total_cost_usd\":0}");
      try (ClaudeRunner.ManagedSession session = runner.startSession(sessionFile,
        "claude-sonnet-4-5", "high", tempDir))
      {
        Path firstScript = tempDir.resolve("emit-claude-success.sh");
        Files.writeString(firstScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          printf '%%s\n' \
            '{"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"hello"}]}}' \
            '%s'
          """.formatted(firstTurnResult), UTF_8);
        requireThat(firstScript.toFile().setExecutable(true), "firstScriptExecutable").isTrue();
        session.submitTurn("turn one", List.of("bash", firstScript.toString()), "{}",
          _ -> {});

        Path failedScript = tempDir.resolve("emit-claude-failed.sh");
        Files.writeString(failedScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          printf '%%s\n' \
            '{"type":"assistant","session_id":"session-123","message":{"content":[' \
            '{"type":"text","text":"still working"}]}}'
          """, UTF_8);
        requireThat(failedScript.toFile().setExecutable(true), "failedScriptExecutable").isTrue();
        session.submitTurn("turn two", List.of("bash", failedScript.toString()), "{}",
          _ -> {});

        requireThat(session.latestState().sessionState(), "failedSessionState").
          isNotEqualTo(NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST);
        requireThat(session.latestState().canSubmitTurn(), "failedCanSubmitTurn").isFalse();
        requireThat(Files.exists(sessionFile), "sessionFileDeletedAfterFailure").isFalse();
        requireThat(session.snapshot().turns().size(), "persistedTurnCountAfterFailure").
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
   * Verifies that a timed-out managed Claude turn invalidates the in-memory session handle.
   */
  @Test
  public void managedSessionRejectsReuseAfterTimeout() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope, Duration.ofMillis(100)))
    {
      Path sessionFile = tempDir.resolve("claude-session.json");
      String firstTurnResult = String.join("",
        "{\"type\":\"result\",\"subtype\":\"success\",\"duration_ms\":1,",
        "\"duration_api_ms\":1,\"is_error\":false,\"num_turns\":1,",
        "\"result\":\"done\",\"session_id\":\"session-123\",\"total_cost_usd\":0}");
      String timeoutAssistant = String.join("",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\",",
        "\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"still working\"}]}}");
      try (ClaudeRunner.ManagedSession session = runner.startSession(sessionFile,
        "claude-sonnet-4-5", "high", tempDir))
      {
        Path firstScript = tempDir.resolve("emit-claude-success.sh");
        Files.writeString(firstScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          printf '%%s\n' \
            '{"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"hello"}]}}' \
            '%s'
          """.formatted(firstTurnResult), UTF_8);
        requireThat(firstScript.toFile().setExecutable(true), "firstScriptExecutable").isTrue();
        session.submitTurn("turn one", List.of("bash", firstScript.toString()), "{}",
          _ -> {});

        Path timeoutScript = tempDir.resolve("emit-claude-timeout.sh");
        Files.writeString(timeoutScript, """
          #!/usr/bin/env bash
          cat >/dev/null
          printf '%%s\n' \
            '%s'
          sleep 5
          """.formatted(timeoutAssistant), UTF_8);
        requireThat(timeoutScript.toFile().setExecutable(true), "timeoutScriptExecutable").isTrue();
        session.submitTurn("turn two", List.of("bash", timeoutScript.toString()), "{}",
          _ -> {});

        requireThat(session.latestState().sessionState(), "timedOutSessionState").
          isEqualTo(NestedRunnerSessionState.TIMEOUT);
        requireThat(session.latestState().turnState(), "timedOutTurnState").
          isEqualTo(NestedRunnerTurnState.TIMEOUT);
        requireThat(session.latestState().canSubmitTurn(), "timedOutCanSubmitTurn").isFalse();
        requireThat(Files.exists(sessionFile), "sessionFileDeletedAfterTimeout").isFalse();
        requireThat(session.snapshot().turns().size(), "persistedTurnCountAfterTimeout").
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
   * Verifies that a Claude save failure invalidates the session instead of outrunning disk state.
   */
  @Test
  public void saveFailInvalidatesManagedSession() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = tempDir.resolve("claude-session.json");
      String firstTurnResult = String.join("",
        "{\"type\":\"result\",\"subtype\":\"success\",\"duration_ms\":1,",
        "\"duration_api_ms\":1,\"is_error\":false,\"num_turns\":1,",
        "\"result\":\"done\",\"session_id\":\"session-123\",\"total_cost_usd\":0}");
      String secondTurnAssistant = String.join("",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\",",
        "\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"hello again\"}]}}");
      try (ClaudeRunner.ManagedSession session = runner.startSession(sessionFile,
        "claude-sonnet-4-5", "high", tempDir))
      {
        Path firstScript = createClaudeScript(tempDir.resolve("emit-claude-success.sh"), """
          #!/usr/bin/env bash
          cat >/dev/null
          printf '%%s\n' \
            '{"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"hello"}]}}' \
            '%s'
          """.formatted(firstTurnResult), "firstScriptExecutable");
        session.submitTurn("turn one", List.of("bash", firstScript.toString()), "{}",
          _ -> {});
        Files.deleteIfExists(sessionFile);
        Files.createDirectory(sessionFile);

        Path secondScript = createClaudeScript(tempDir.resolve("emit-claude-second-success.sh"), """
          #!/usr/bin/env bash
          cat >/dev/null
          printf '%%s\n' \
            '%s' \
            '%s'
          """.formatted(secondTurnAssistant,
          firstTurnResult.replace("\"num_turns\":1", "\"num_turns\":2")), "secondScriptExecutable");

        assertSaveFailure(session, secondScript);
        Files.deleteIfExists(sessionFile);
        assertSessionInvalidatedAfterSaveFailure(session);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that managed Claude sessions persist the native Claude session id for resume.
   */
  @Test
  public void managedSessionPersistsResumeId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = tempDir.resolve("claude-session.json");
      ClaudeRunner.ClaudeSession initial = runner.loadSession(sessionFile, "claude-sonnet-4-5",
        "high", tempDir);

      String firstTurnOutput = String.join("\n",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\"," +
          "\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Write\"," +
          "\"input\":{\"content\":\"patched\"}}]}}",
        "{\"type\":\"assistant\",\"session_id\":\"session-123\"," +
          "\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"wrote file\"}]}}",
        "{\"type\":\"result\",\"result\":\"done\",\"session_id\":\"session-123\"}");
      ClaudeRunner.ParsedSessionOutput firstTurn = runner.parseSessionOutput(firstTurnOutput);
      runner.saveSession(sessionFile, initial.appendTurn("turn one", firstTurn.parsed(),
        firstTurn.state()));

      ClaudeRunner.ClaudeSession reloaded = runner.loadSession(sessionFile, "claude-sonnet-4-5",
        "high", tempDir);
      List<String> command = runner.buildCommand("claude-sonnet-4-5", "high", "", "",
        reloaded.sessionId());

      requireThat(reloaded.sessionId(), "sessionId").isEqualTo("session-123");
      requireThat(command, "command").contains("--resume", "session-123");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Creates an executable shell script used by Claude runner tests.
   *
   * @param scriptPath the target script path
   * @param scriptContents the shell script contents
   * @param executableRequirement the requirement label for the executable assertion
   * @return the executable script path
   * @throws IOException if the script cannot be written
   */
  private Path createClaudeScript(Path scriptPath, String scriptContents,
    String executableRequirement) throws IOException
  {
    Files.writeString(scriptPath, scriptContents, UTF_8);
    requireThat(scriptPath.toFile().setExecutable(true), executableRequirement).isTrue();
    return scriptPath;
  }

  /**
   * Verifies that a failed managed-session save surfaces the expected I/O failure.
   *
   * @param session the managed session under test
   * @param scriptPath the script that triggers the second successful turn
   */
  private void assertSaveFailure(ClaudeRunner.ManagedSession session, Path scriptPath)
  {
    try
    {
      session.submitTurn("turn two", List.of("bash", scriptPath.toString()), "{}",
        _ -> {});
      throw new AssertionError("Expected submitTurn() to fail when session persistence fails");
    }
    catch (IOException e)
    {
      requireThat(e.getMessage(), "errorMessage").contains("Session file");
    }
  }

  /**
   * Verifies that a save failure marks the session as non-resumable and rejects future turns.
   *
   * @param session the managed session under test
   * @throws IOException if the session unexpectedly reports I/O failure during the rejection check
   */
  private void assertSessionInvalidatedAfterSaveFailure(ClaudeRunner.ManagedSession session)
    throws IOException
  {
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

  /**
   * Verifies that a session file cannot be reused from a different working directory.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*cwd does not match.*")
  public void loadSessionRejectsMismatchedCwd() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path firstCwd = Files.createDirectories(tempDir.resolve("first"));
      Path secondCwd = Files.createDirectories(tempDir.resolve("second"));
      Path sessionFile = secondCwd.resolve("claude-session.json");

      ClaudeRunner.ClaudeSession initial = runner.loadSession(sessionFile, "claude-sonnet-4-5",
        "high", secondCwd);
      ClaudeRunner.ParsedSessionOutput turn = runner.parseSessionOutput("""
        {"type":"assistant","session_id":"session-123","message":{"content":[{"type":"text","text":"hello"}]}}
        {"type":"result","result":"done","session_id":"session-123"}
        """);
      ClaudeRunner.ClaudeSession mismatchedCwd = initial.appendTurn("turn one", turn.parsed(),
        turn.state());
      mismatchedCwd = new ClaudeRunner.ClaudeSession(mismatchedCwd.sessionId(), mismatchedCwd.model(),
        mismatchedCwd.effort(), firstCwd.toRealPath().toString(), mismatchedCwd.appendSystemPrompt(),
        mismatchedCwd.agent(), mismatchedCwd.turns(), mismatchedCwd.latestState());
      Files.writeString(sessionFile, scope.getJsonMapper().writeValueAsString(mismatchedCwd), UTF_8);

      runner.loadSession(sessionFile, "claude-sonnet-4-5", "high", secondCwd);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a persisted Claude session cannot resume unless the last state is ready for another turn.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*not ready for another turn.*")
  public void loadSessionRejectsNonResumableState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = tempDir.resolve("claude-session.json");
      ClaudeRunner.ClaudeSession session = new ClaudeRunner.ClaudeSession("session-123",
        "claude-sonnet-4-5", "high", tempDir.toRealPath().toString(), "", "",
        List.of(new ClaudeRunner.ClaudeSessionTurn("prompt", List.of("answer"), List.of(), List.of())),
        new NestedRunnerState("session-123", "", "error", "", NestedRunnerTurnState.ERROR,
          NestedRunnerSessionState.ERROR, false, "", "boom"));
      runner.saveSession(sessionFile, session);

      runner.loadSession(sessionFile, "claude-sonnet-4-5", "high", tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a persisted Claude session with turns must retain the native session id.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*turns but no native session id.*")
  public void loadSessionRejectsTurnsWithoutSessionId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = tempDir.resolve("claude-session.json");
      ClaudeRunner.ClaudeSession session = new ClaudeRunner.ClaudeSession("",
        "claude-sonnet-4-5", "high", tempDir.toRealPath().toString(), "", "",
        List.of(new ClaudeRunner.ClaudeSessionTurn("prompt", List.of("answer"), List.of(), List.of())),
        new NestedRunnerState("", "", "result", "", NestedRunnerTurnState.COMPLETED,
          NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST, true, "", ""));
      runner.saveSession(sessionFile, session);

      runner.loadSession(sessionFile, "claude-sonnet-4-5", "high", tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a persisted native Claude session id is not accepted without matching CAT turns.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*native session id but no turns.*")
  public void loadSessionRejectsSessionIdWithoutTurns() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = tempDir.resolve("claude-session.json");
      ClaudeRunner.ClaudeSession session = new ClaudeRunner.ClaudeSession("session-123",
        "claude-sonnet-4-5", "high", tempDir.toRealPath().toString(), "", "", List.of(),
        new NestedRunnerState("session-123", "", "result", "", NestedRunnerTurnState.COMPLETED,
          NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST, true, "", ""));
      runner.saveSession(sessionFile, session);

      runner.loadSession(sessionFile, "claude-sonnet-4-5", "high", tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that loading a persisted Claude session fills in the latest state session id when omitted.
   */
  @Test
  public void loadSessionNormalizesBlankStateId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = tempDir.resolve("claude-session.json");
      ClaudeRunner.ClaudeSession session = new ClaudeRunner.ClaudeSession("session-123",
        "claude-sonnet-4-5", "high", tempDir.toRealPath().toString(), "", "",
        List.of(new ClaudeRunner.ClaudeSessionTurn("prompt", List.of("answer"), List.of(), List.of())),
        new NestedRunnerState("", "", "result", "", NestedRunnerTurnState.COMPLETED,
          NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST, true, "", ""));
      runner.saveSession(sessionFile, session);

      ClaudeRunner.ClaudeSession loaded = runner.loadSession(sessionFile, "claude-sonnet-4-5",
        "high", tempDir);

      requireThat(loaded.latestState().sessionId(), "latestState.sessionId").
        isEqualTo("session-123");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Claude managed sessions cannot be resumed with different prompt/agent semantics.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*prompt/agent settings do not match current request.*")
  public void loadSessionRejectsAgentMismatch() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = tempDir.resolve("claude-session.json");
      ClaudeRunner.ClaudeSession session = new ClaudeRunner.ClaudeSession("session-123",
        "claude-sonnet-4-5", "high", tempDir.toRealPath().toString(), "system", "grader",
        List.of(), new NestedRunnerState("session-123", "", "result", "",
        NestedRunnerTurnState.COMPLETED, NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST,
        true, "", ""));
      runner.saveSession(sessionFile, session);

      runner.loadSession(sessionFile, "claude-sonnet-4-5", "high", tempDir, "", "");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that managed Claude session files must stay under the requested cwd.
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
    try (TestClaudeTool scope = new TestClaudeTool(cwd, cwd);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = outside.resolve("claude-session.json");
      ClaudeRunner.ClaudeSession session = new ClaudeRunner.ClaudeSession("",
        "claude-sonnet-4-5", "high", cwd.toRealPath().toString(), "", "", List.of(),
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
   * Verifies that a symlinked ancestor cannot redirect a managed Claude session file outside cwd.
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
    try (TestClaudeTool scope = new TestClaudeTool(cwd, cwd);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = cwd.resolve("link/newdir/claude-session.json");
      ClaudeRunner.ClaudeSession session = new ClaudeRunner.ClaudeSession("",
        "claude-sonnet-4-5", "high", cwd.toRealPath().toString(), "", "", List.of(),
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
   * Verifies that relative Claude session paths are resolved under the requested cwd.
   */
  @Test
  public void saveSessionResolvesRelativePath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir);
      ClaudeRunner runner = new ClaudeRunner(scope))
    {
      Path sessionFile = Path.of(".cat/work/claude-session.json");
      ClaudeRunner.ClaudeSession session = new ClaudeRunner.ClaudeSession("",
        "claude-sonnet-4-5", "high", tempDir.toRealPath().toString(), "", "", List.of(),
        new NestedRunnerState("", "", "", "", NestedRunnerTurnState.UNKNOWN,
          NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST, true, "", ""));

      runner.saveSession(sessionFile, session);
      ClaudeRunner.ClaudeSession loaded = runner.loadSession(sessionFile, "claude-sonnet-4-5",
        "high", tempDir);

      requireThat(Files.exists(tempDir.resolve(".cat/work/claude-session.json")),
        "sessionExists").isTrue();
      requireThat(loaded.model(), "loaded.model").isEqualTo("claude-sonnet-4-5");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getIsolatedConfigDir returns empty string when no isolation is configured.
   */
  @Test
  public void noIsolationReturnsEmptyConfigDir() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        requireThat(runner.getIsolatedConfigDir(), "configDir").isEmpty();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProcessBuilder sets CLAUDE_CONFIG_DIR when isolation is active.
   */
  @Test
  public void buildProcessBuilderSetsConfigDirWhen() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sourceConfig = tempDir.resolve("source-config");
      Files.createDirectories(sourceConfig);
      Path pluginSource = tempDir.resolve("plugin");
      Files.createDirectories(pluginSource);
      Path jlinkBin = tempDir.resolve("jlink-bin");
      Files.createDirectories(jlinkBin);

      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        runner.createIsolatedConfig(sourceConfig, pluginSource, jlinkBin, "2.1");
        String expectedConfigDir = runner.getIsolatedConfigDir();

        ProcessBuilder pb = runner.buildProcessBuilder(List.of("claude"), tempDir);
        requireThat(pb.environment().get("CLAUDE_CONFIG_DIR"), "CLAUDE_CONFIG_DIR").
          isEqualTo(expectedConfigDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProcessBuilder does NOT override CLAUDE_CONFIG_DIR when no isolation is active.
   * <p>
   * The env is inherited from the parent process unchanged; no isolation-specific value is injected.
   */
  @Test
  public void buildProcessBuilderDoesNotSetConfigDir() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        String parentValue = System.getenv("CLAUDE_CONFIG_DIR");

        ProcessBuilder pb = runner.buildProcessBuilder(List.of("claude"), tempDir);

        // The ProcessBuilder inherits the parent env; without isolation no override is injected.
        requireThat(pb.environment().get("CLAUDE_CONFIG_DIR"), "CLAUDE_CONFIG_DIR").
          isEqualTo(parentValue);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProcessBuilder sets CLAUDE_PLUGIN_ROOT to the isolated plugin cache path
   * when isolation is active.
   */
  @Test
  public void buildProcessBuilderSetsPluginRootWhen() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sourceConfig = tempDir.resolve("source-config");
      Files.createDirectories(sourceConfig);
      Path pluginSource = tempDir.resolve("plugin");
      Files.createDirectories(pluginSource);
      Path jlinkBin = tempDir.resolve("jlink-bin");
      Files.createDirectories(jlinkBin);

      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        runner.createIsolatedConfig(sourceConfig, pluginSource, jlinkBin, "2.1");
        String isolatedConfigDir = runner.getIsolatedConfigDir();

        ProcessBuilder pb = runner.buildProcessBuilder(List.of("claude"), tempDir);

        String expectedPluginRoot = Path.of(isolatedConfigDir).resolve("plugins").resolve("cache").
          resolve("cat").resolve("cat").resolve("2.1").toString();
        requireThat(pb.environment().get("CLAUDE_PLUGIN_ROOT"), "CLAUDE_PLUGIN_ROOT").
          isEqualTo(expectedPluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProcessBuilder does NOT override CLAUDE_PLUGIN_ROOT when no isolation is active.
   * <p>
   * The env is inherited from the parent process unchanged; no isolation-specific value is injected.
   */
  @Test
  public void buildProcessBuilderDoesNotSetPluginRoot() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        String parentValue = System.getenv("CLAUDE_PLUGIN_ROOT");

        ProcessBuilder pb = runner.buildProcessBuilder(List.of("claude"), tempDir);

        // The ProcessBuilder inherits the parent env; without isolation no override is injected.
        requireThat(pb.environment().get("CLAUDE_PLUGIN_ROOT"), "CLAUDE_PLUGIN_ROOT").
          isEqualTo(parentValue);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProcessBuilder always sets CLAUDE_PROJECT_DIR to the absolute cwd path,
   * even when no isolation is active. This ensures the runner process and its subagents resolve
   * relative file paths against the runner worktree rather than the parent's project directory.
   */
  @Test
  public void buildProcessBuilderSetsProjectDirToCwd() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        Path cwd = tempDir.resolve("runner-worktree");
        Files.createDirectories(cwd);

        ProcessBuilder pb = runner.buildProcessBuilder(List.of("claude"), cwd);

        requireThat(pb.environment().get("CLAUDE_PROJECT_DIR"), "CLAUDE_PROJECT_DIR").
          isEqualTo(cwd.toAbsolutePath().toString());
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
