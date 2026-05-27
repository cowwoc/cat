/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.util.UpdateBranch;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link UpdateBranch#run(String[], PrintStream, PrintStream, Path)} argument-error behavior.
 */
public final class UpdateBranchMainTest
{
  /**
   * Verifies that run(...) rejects invalid arguments with non-zero status and plain-text usage output.
   */
  @Test
  public void runInvalidArgsReturnNonZeroWithPlainText()
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
  public void runUnknownFlagReturnNonZeroWithPlainText()
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
  public void runDuplicateForceReturnsNonZeroWithPlain()
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
  public void runBlankArgumentsReturnNonZeroWithPlain()
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
}
