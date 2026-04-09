/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.EnforceStatusOutput;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for EnforceStatusOutput.run() CLI error path handling.
 */
public class EnforceStatusOutputMainTest
{
  /**
   * Verifies that run() produces a valid hook response when given a transcript with a status box.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void transcriptWithStatusBoxProducesResponse() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-main-test-");
    try
    {
      Path transcript = tempDir.resolve("transcript.jsonl");
      Files.writeString(transcript, """
        {"type":"text","content":"╭─ CAT Status"}
        """);
      String payloadJson = """
        {
          "session_id": "00000000-0000-0000-0000-000000000001",
          "transcript_path": "%s",
          "stop_hook_active": false
        }""".formatted(transcript.toString().replace("\\", "\\\\"));
      try (ClaudeHook scope = new TestClaudeHook(payloadJson, tempDir, tempDir, tempDir))
      {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        EnforceStatusOutput.run(scope, new String[]{},
          new ByteArrayInputStream(new byte[0]), out);

        String output = buffer.toString(StandardCharsets.UTF_8).strip();
        requireThat(output, "output").isNotBlank();

        // Verify the output is valid JSON
        JsonMapper mapper = scope.getJsonMapper();
        JsonNode json = mapper.readTree(output);
        requireThat(json.isObject(), "isJsonObject").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null args.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*args.*")
  public void nullArgsThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-main-test-");
    try (ClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      EnforceStatusOutput.run(scope, null,
        new ByteArrayInputStream(new byte[0]),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null input stream.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*in.*")
  public void nullInThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-main-test-");
    try (ClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      EnforceStatusOutput.run(scope, new String[]{}, null,
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null output stream.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*out.*")
  public void nullOutThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-main-test-");
    try (ClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      EnforceStatusOutput.run(scope, new String[]{},
        new ByteArrayInputStream(new byte[0]), null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that unexpected arguments produce an IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unexpected arguments.*")
  public void unexpectedArgsProducesError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-status-output-main-test-");
    try (ClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      EnforceStatusOutput.run(scope, new String[]{"--bogus"},
        new ByteArrayInputStream(new byte[0]), out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
