/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.util.UpdateBranch;
import io.github.cowwoc.cat.agent.ProcessRunner;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link UpdateBranch#run(String[], PrintStream, PrintStream, Path)} and
 * {@link UpdateBranch#main(String[])} argument-error behavior.
 */
public final class UpdateBranchMainTest
{
  private static final String UPDATE_BRANCH_CLASS =
    "io.github.cowwoc.cat.claude.hook.util.UpdateBranch";

  /**
   * Verifies that run(...) rejects invalid arguments with non-zero status and plain-text usage output.
   */
  @Test
  public void runInvalidArgsReturnNonZeroWithPlainTextUsage()
  {
    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);

    int exitCode = UpdateBranch.run(new String[]{"only-branch"}, out, err, Path.of("."));

    String stdout = outBuffer.toString(StandardCharsets.UTF_8).strip();
    String stderr = errBuffer.toString(StandardCharsets.UTF_8).strip();
    requireThat(exitCode, "exitCode").isNotEqualTo(0);
    requireThat(stderr, "stderr").contains("Usage:");
    requireThat(stdout, "stdout").isEmpty();
    TestUtils.assertPlainText(stdout, stderr);
  }

  /**
   * Verifies that run(...) rejects unknown flags with non-zero status and plain-text usage output.
   */
  @Test
  public void runUnknownFlagReturnNonZeroWithPlainTextUsage()
  {
    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);

    int exitCode = UpdateBranch.run(new String[]{"--bogus", "branch", "hash"}, out, err, Path.of("."));

    String stdout = outBuffer.toString(StandardCharsets.UTF_8).strip();
    String stderr = errBuffer.toString(StandardCharsets.UTF_8).strip();
    requireThat(exitCode, "exitCode").isNotEqualTo(0);
    requireThat(stderr, "stderr").contains("Usage:");
    TestUtils.assertPlainText(stdout, stderr);
  }

  /**
   * Verifies that run(...) rejects duplicate {@code --force} flags with plain-text usage output.
   */
  @Test
  public void runDuplicateForceReturnsNonZeroWithPlainTextUsage()
  {
    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);

    int exitCode = UpdateBranch.run(new String[]{"--force", "--force", "branch", "hash"},
      out, err, Path.of("."));

    String stdout = outBuffer.toString(StandardCharsets.UTF_8).strip();
    String stderr = errBuffer.toString(StandardCharsets.UTF_8).strip();
    requireThat(exitCode, "exitCode").isNotEqualTo(0);
    requireThat(stderr, "stderr").contains("Duplicate flag");
    requireThat(stderr, "stderr").contains("Usage:");
    TestUtils.assertPlainText(stdout, stderr);
  }

  /**
   * Verifies that run(...) rejects blank branch and target arguments with plain-text usage output.
   */
  @Test
  public void runBlankArgumentsReturnNonZeroWithPlainTextUsage()
  {
    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);

    int blankBranchExitCode = UpdateBranch.run(new String[]{" ", "HEAD"}, out, err, Path.of("."));
    int blankTargetExitCode = UpdateBranch.run(new String[]{"branch", " "}, out, err, Path.of("."));

    String stdout = outBuffer.toString(StandardCharsets.UTF_8).strip();
    String stderr = errBuffer.toString(StandardCharsets.UTF_8).strip();
    requireThat(blankBranchExitCode, "blankBranchExitCode").isNotEqualTo(0);
    requireThat(blankTargetExitCode, "blankTargetExitCode").isNotEqualTo(0);
    requireThat(stderr, "stderr").contains("Usage:");
    TestUtils.assertPlainText(stdout, stderr);
  }

  /**
   * Verifies that main(...) with invalid args exits non-zero and emits plain-text usage output.
   *
   * @throws IOException if process execution fails
   */
  @Test
  public void mainInvalidArgsEmitPlainTextUsageAndNonZeroExit() throws IOException
  {
    Path tempDir = Files.createTempDirectory("update-branch-main-test-");
    try
    {
      ProcessResult result = invokeMain(tempDir, "only-branch");

      requireThat(result.exitCode(), "exitCode").isNotEqualTo(0);
      requireThat(result.stderr(), "stderr").isNotBlank();
      requireThat(result.stderr() + "\n" + result.stdout(), "combinedOutput").contains("Usage:");
      TestUtils.assertPlainText(result.stdout(), result.stderr());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  private static ProcessResult invokeMain(Path directory, String... args) throws IOException
  {
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(UPDATE_BRANCH_CLASS);
    for (String arg : args)
      command.add(arg);

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(directory.toFile());
    processBuilder.redirectErrorStream(false);
    Process process = processBuilder.start();

    String stdout;
    try (BufferedReader stdoutReader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
    {
      stdout = ProcessRunner.readAllLines(stdoutReader);
    }

    String stderr;
    try (BufferedReader stderrReader = new BufferedReader(
      new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)))
    {
      stderr = ProcessRunner.readAllLines(stderrReader);
    }

    int exitCode;
    try
    {
      exitCode = process.waitFor();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for update-branch process", e);
    }
    return new ProcessResult(exitCode, stdout.strip(), stderr.strip());
  }

  private record ProcessResult(int exitCode, String stdout, String stderr)
  {
  }
}
