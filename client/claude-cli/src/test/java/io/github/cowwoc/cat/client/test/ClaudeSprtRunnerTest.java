/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.skills.SharedSecrets;
import io.github.cowwoc.cat.tool.skills.SprtRunner;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Runtime tests for Claude SPRT runner behavior.
 */
public final class ClaudeSprtRunnerTest
{
  /**
   * Verifies runtime command delegation behavior through SprtRunner for a Claude scope.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if interrupted
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*unknown command: unknown-command.*")
  public void unknownCommandUsesSprtRunnerDispatch() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("claude-sprt-runner-test-");
    try (CliTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner.run(scope, new String[]{"unknown-command"},
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies Claude handler registration resolution at runtime via script function execution.
   *
   * @throws IOException if process execution fails
   * @throws InterruptedException if interrupted
   */
  @Test
  public void claudeHandlerResolutionRuntime() throws IOException, InterruptedException
  {
    String scriptPath = Path.of(System.getProperty("user.dir")).getParent().resolve(
      "distribution/scripts/build-jlink-images.sh").toString();
    String output;
    int exitCode;
    try (Process process = new ProcessBuilder("bash", "-lc",
      "source '" + scriptPath + "'; set_engine_handlers claude; printf '%s\n' \"${HANDLERS[@]}\"").
      redirectErrorStream(true).start())
    {
      output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      exitCode = process.waitFor();
    }
    requireThat(exitCode, "exitCode").isEqualTo(0);
    requireThat(output, "output").contains("claude-runner:io.github.cowwoc.cat.claude.engine.ClaudeRunner");
    requireThat(output, "output").contains("sprt-runner:io.github.cowwoc.cat.claude.engine.ClaudeSprtRunner");
    requireThat(output, "output").doesNotContain("sprt-runner:io.github.cowwoc.cat.common.cli/");
  }

  /**
   * Verifies that grading uses the instruction-grader-agent frontmatter model and effort, not the
   * model and effort used by the test run being graded.
   *
   * @throws Exception if setup or process execution fails
   */
  @Test
  public void graderUsesClaudeAgentConfig() throws Exception
  {
    Path tempDir = Files.createTempDirectory("claude-grader-agent-config-");
    try (CliTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path agentFile = tempDir.resolve(
        "client/plugin/agents/claude/instruction-grader-agent.md");
      Files.createDirectories(agentFile.getParent());
      Files.writeString(agentFile, """
        ---
        name: instruction-grader-agent
        model: claude-haiku-4-5
        effort: low
        ---
        # Grader
        """, StandardCharsets.UTF_8);

      Path capturedArgs = tempDir.resolve("captured-args.txt");
      Path launcher = tempDir.resolve(
        "client/distribution/target/jlink/claude/bin/claude-runner");
      writeFakeLauncher(launcher, capturedArgs,
        "--plugin-source --jlink-bin --agent --output");

      Path promptFile = tempDir.resolve("grader-prompt.txt");
      Files.writeString(promptFile, "grade this", StandardCharsets.UTF_8);

      int exitCode = SharedSecrets.runGrader(scope, "2.1.87", promptFile,
        "claude-opus-4-5", "high", tempDir.toString(),
        tempDir.resolve("grade.json").toString(),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

      requireThat(exitCode, "exitCode").isEqualTo(0);
      String[] args = Files.readString(capturedArgs, StandardCharsets.UTF_8).strip().split("\n");
      requireThat(valueAfter(args, "--model"), "model").isEqualTo("claude-haiku-4-5");
      requireThat(valueAfter(args, "--effort"), "effort").isEqualTo("low");
      requireThat(valueAfter(args, "--agent"), "agent").isEqualTo("instruction-grader-agent");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  private static void writeFakeLauncher(Path launcher, Path capturedArgs, String help)
    throws IOException
  {
    Files.createDirectories(launcher.getParent());
    Files.writeString(launcher, """
      #!/usr/bin/env bash
      if [ "$1" = "--help" ]; then
        printf '%%s\\n' '%s'
        exit 0
      fi
      printf '%%s\\n' "$@" > '%s'
      exit 0
      """.formatted(help, capturedArgs), StandardCharsets.UTF_8);
    if (!launcher.toFile().setExecutable(true))
      throw new IOException("Unable to make launcher executable: " + launcher);
  }

  private static String valueAfter(String[] args, String flag)
  {
    for (int index = 0; index + 1 < args.length; ++index)
    {
      if (args[index].equals(flag))
        return args[index + 1];
    }
    throw new AssertionError("Missing flag: " + flag);
  }
}
