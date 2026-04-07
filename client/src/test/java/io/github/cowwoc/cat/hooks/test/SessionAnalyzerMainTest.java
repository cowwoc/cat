/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.util.SessionAnalyzer;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;


/**
 * Tests for SessionAnalyzer.run() CLI error path handling.
 */
public class SessionAnalyzerMainTest
{
  /**
   * Verifies that invoking run() with no arguments throws IllegalArgumentException with usage information.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*Usage.*SessionAnalyzer.*")
  public void noArgsThrowsExceptionWithUsage() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-analyzer-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      SessionAnalyzer.run(scope, new String[]{}, out);
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
    Path tempDir = Files.createTempDirectory("session-analyzer-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SessionAnalyzer.run(scope, null,
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
    Path tempDir = Files.createTempDirectory("session-analyzer-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SessionAnalyzer.run(scope, new String[]{"dummy"}, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException when scope is null.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void nullScopeThrowsException() throws IOException
  {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    SessionAnalyzer.run(null, new String[]{"dummy"}, out);
  }

  /**
   * Verifies that the analyze subcommand with no session ID throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Usage.*analyze.*")
  public void analyzeMissingSessionIdThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-analyzer-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      SessionAnalyzer.run(scope, new String[]{"analyze"}, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the search subcommand with only a session ID (missing pattern) throws
   * IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Usage.*search.*")
  public void searchMissingPatternThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-analyzer-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      SessionAnalyzer.run(scope, new String[]{"search", "some-session-id"}, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the errors subcommand with no session ID throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Usage.*errors.*")
  public void errorsMissingSessionIdThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-analyzer-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      SessionAnalyzer.run(scope, new String[]{"errors"}, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the file-history subcommand with only a session ID (missing path pattern) throws
   * IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Usage.*file-history.*")
  public void fileHistoryMissingPathPatternThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-analyzer-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      SessionAnalyzer.run(scope, new String[]{"file-history", "some-session-id"}, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the analyze subcommand produces non-blank JSON output for a valid session file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void analyzeSubcommandProducesOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-analyzer-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sessionsPath = scope.getClaudeSessionsPath();
      Files.createDirectories(sessionsPath);
      Files.writeString(sessionsPath.resolve("test-session.jsonl"),
        "{\"type\":\"assistant\",\"message\":{\"id\":\"msg1\",\"content\":[]}}\n");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      SessionAnalyzer.run(scope, new String[]{"analyze", "test-session"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the errors subcommand produces non-blank JSON output for a valid session file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void errorsSubcommandProducesOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-analyzer-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sessionsPath = scope.getClaudeSessionsPath();
      Files.createDirectories(sessionsPath);
      Files.writeString(sessionsPath.resolve("test-session.jsonl"),
        "{\"type\":\"assistant\",\"message\":{\"id\":\"msg1\",\"content\":[]}}\n");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      SessionAnalyzer.run(scope, new String[]{"errors", "test-session"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the search subcommand produces non-blank JSON output for a valid session file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void searchSubcommandProducesOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-analyzer-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sessionsPath = scope.getClaudeSessionsPath();
      Files.createDirectories(sessionsPath);
      Files.writeString(sessionsPath.resolve("test-session.jsonl"),
        "{\"type\":\"assistant\",\"message\":{\"id\":\"msg1\",\"content\":[]}}\n");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      SessionAnalyzer.run(scope, new String[]{"search", "test-session", "msg1"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
