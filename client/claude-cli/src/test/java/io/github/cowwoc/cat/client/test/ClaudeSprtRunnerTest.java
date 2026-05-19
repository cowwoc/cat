/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.CliTool;
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
}
