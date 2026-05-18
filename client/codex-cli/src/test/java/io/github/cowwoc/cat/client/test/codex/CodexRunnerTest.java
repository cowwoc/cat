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
import io.github.cowwoc.cat.codex.engine.CodexRunner.ParsedOutput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
   * Verifies that normal Codex executions keep the default sandbox policy.
   */
  @Test
  public void buildCommandUsesDefaultSandboxOutsideCodexEngine() throws IOException
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
   * Verifies that Codex JSONL output is parsed into text, tool use, and write content fields.
   */
  @Test
  public void parseOutputExtractsTextToolUsesAndWriteContent() throws IOException
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
}
