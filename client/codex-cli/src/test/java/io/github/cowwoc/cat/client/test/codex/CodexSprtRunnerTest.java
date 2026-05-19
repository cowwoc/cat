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
}
