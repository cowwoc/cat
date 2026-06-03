/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.codex;

import io.github.cowwoc.cat.client.test.TestCodexTool;
import io.github.cowwoc.cat.client.test.TestUtils;
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
 * Runtime tests for Codex SPRT runner behavior.
 */
public final class CodexSprtRunnerTest
{
  /**
   * Verifies runtime command delegation behavior through SprtRunner for a Codex scope.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if interrupted
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*unknown command: unknown-command.*")
  public void unknownCommandUsesSprtRunnerDispatch() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-runner-test-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
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
   * Verifies Codex handler registration resolution at runtime via script function execution.
   *
   * @throws IOException if process execution fails
   * @throws InterruptedException if interrupted
   */
  @Test
  public void codexHandlerResolutionRuntime() throws IOException, InterruptedException
  {
    String scriptPath = Path.of(System.getProperty("user.dir")).getParent().resolve(
      "distribution/scripts/build-jlink-images.sh").toString();
    String output;
    int exitCode;
    try (Process process = new ProcessBuilder("bash", "-lc",
      "source '" + scriptPath + "'; set_engine_handlers codex; printf '%s\n' \"${HANDLERS[@]}\"").
      redirectErrorStream(true).start())
    {
      output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      exitCode = process.waitFor();
    }
    requireThat(exitCode, "exitCode").isEqualTo(0);
    requireThat(output, "output").contains("codex-runner:io.github.cowwoc.cat.codex.engine.CodexRunner");
    requireThat(output, "output").contains("sprt-runner:io.github.cowwoc.cat.codex.engine.CodexSprtRunner");
    requireThat(output, "output").doesNotContain("sprt-runner:io.github.cowwoc.cat.common.cli/");
  }

  /**
   * Verifies that grading uses the instruction-grader-agent TOML model and effort, not the
   * model and effort used by the test run being graded.
   *
   * @throws Exception if setup or process execution fails
   */
  @Test
  public void graderUsesCodexAgentConfig() throws Exception
  {
    Path tempDir = Files.createTempDirectory("codex-grader-agent-config-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      Path agentFile = tempDir.resolve(
        "client/plugin/agents/codex/instruction-grader-agent.toml");
      Files.createDirectories(agentFile.getParent());
      Files.writeString(agentFile, """
        name = "cat-instruction-grader-agent"
        nickname_candidates = ["instruction-grader-agent"]
        model = "gpt-5.4-mini"
        model_reasoning_effort = "medium"
        """, StandardCharsets.UTF_8);

      Path capturedArgs = tempDir.resolve("captured-args.txt");
      Path launcher = tempDir.resolve(
        "client/distribution/target/jlink/codex/bin/codex-runner");
      writeFakeLauncher(launcher, capturedArgs, "--output");

      Path promptFile = tempDir.resolve("grader-prompt.txt");
      Files.writeString(promptFile, "grade this", StandardCharsets.UTF_8);

      int exitCode = SharedSecrets.runGrader(scope, "2.1.87", promptFile,
        "gpt-5.5", "xhigh", tempDir.toString(),
        tempDir.resolve("grade.json").toString(),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

      requireThat(exitCode, "exitCode").isEqualTo(0);
      String[] args = Files.readString(capturedArgs, StandardCharsets.UTF_8).strip().split("\n");
      requireThat(valueAfter(args, "--model"), "model").isEqualTo("gpt-5.4-mini");
      requireThat(valueAfter(args, "--effort"), "effort").isEqualTo("medium");
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
