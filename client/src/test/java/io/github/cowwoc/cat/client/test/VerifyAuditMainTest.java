/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.hook.skills.VerifyAudit;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link VerifyAudit#run(ClaudeTool, String[], InputStream, PrintStream)} CLI error path handling.
 */
public class VerifyAuditMainTest
{
  /**
   * Verifies that invoking run() with --help produces usage output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void helpFlagProducesUsageOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      InputStream in = new ByteArrayInputStream(new byte[0]);
      VerifyAudit.run(scope, new String[]{"--help"}, in, out);
      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").contains("Usage");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null args.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*args.*")
  public void nullArgsThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      InputStream in = new ByteArrayInputStream(new byte[0]);
      VerifyAudit.run(scope, null, in,
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null output stream.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*out.*")
  public void nullOutThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      InputStream in = new ByteArrayInputStream(new byte[0]);
      VerifyAudit.run(scope, new String[]{}, in, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that -h produces the same usage output as --help.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void shortHelpFlagProducesUsageOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      InputStream in = new ByteArrayInputStream(new byte[0]);
      VerifyAudit.run(scope, new String[]{"-h"}, in, out);
      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").contains("Usage");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an unknown subcommand throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unknown subcommand.*bogus.*")
  public void unknownSubcommandThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      InputStream in = new ByteArrayInputStream(new byte[0]);
      VerifyAudit.run(scope, new String[]{"bogus"}, in, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
