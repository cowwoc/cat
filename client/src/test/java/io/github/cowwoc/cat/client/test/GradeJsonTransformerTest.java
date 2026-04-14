/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for the grade-json-transformer CLI binary.
 */
public final class GradeJsonTransformerTest
{
  /**
   * Verifies that the grade-json-transformer binary exists and is executable.
   */
  @Test
  public void binaryExists() throws IOException
  {
    Path binary = Path.of(System.getProperty("user.home")).
      resolve(".config/claude/plugins/cache/cat/cat/2.1/client/bin/grade-json-transformer");
    requireThat(Files.exists(binary), "binaryExists").isTrue();
    requireThat(Files.isExecutable(binary), "binaryIsExecutable").isTrue();
  }

  /**
   * Verifies that valid grader JSON with correct schema is accepted and transformed correctly.
   */
  @Test
  public void validGraderJsonIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path inputFile = tempDir.resolve("input.json");
      Path outputFile = tempDir.resolve("output.json");

      String inputJson = """
        {
          "assertion_results": [
            {
              "assertion": "must use rm -f",
              "verdict": "PASS",
              "evidence": "Agent executed: rm -f file.txt",
              "explanation": "The agent correctly used rm -f as required."
            }
          ]
        }""";

      Files.writeString(inputFile, inputJson);

      Path binary = Path.of(System.getProperty("user.home")).
        resolve(".config/claude/plugins/cache/cat/cat/2.1/client/bin/grade-json-transformer");

      ProcessBuilder pb = new ProcessBuilder(
        binary.toString(),
        inputFile.toString(),
        "tc1_run1",
        outputFile.toString());
      try (Process process = pb.start())
      {
        int exitCode = process.waitFor();

        requireThat(exitCode, "exitCode").isEqualTo(0);
        requireThat(Files.exists(outputFile), "outputFileExists").isTrue();

        String outputJson = Files.readString(outputFile);
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode output = mapper.readTree(outputJson);

        requireThat(output.has("test_case_id"), "hasTestCaseId").isTrue();
        requireThat(output.path("test_case_id").asString(), "testCaseId").isEqualTo("tc1_run1");
        requireThat(output.has("assertion_results"), "hasAssertionResults").isTrue();
        requireThat(output.path("assertion_results").isArray(), "assertionResultsIsArray").isTrue();
      }
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Process interrupted", e);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that grader JSON with wrong field name "status" instead of "verdict" is rejected.
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*verdict.*status.*")
  public void wrongFieldNameStatusIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path inputFile = tempDir.resolve("input.json");
      Path outputFile = tempDir.resolve("output.json");

      String inputJson = """
        {
          "assertion_results": [
            {
              "assertion": "must use rm -f",
              "status": "PASS",
              "evidence": "Agent executed: rm -f file.txt",
              "explanation": "The agent correctly used rm -f as required."
            }
          ]
        }""";

      Files.writeString(inputFile, inputJson);

      Path binary = Path.of(System.getProperty("user.home")).
        resolve(".config/claude/plugins/cache/cat/cat/2.1/client/bin/grade-json-transformer");

      ProcessBuilder pb = new ProcessBuilder(
        binary.toString(),
        inputFile.toString(),
        "tc1_run1",
        outputFile.toString());
      try (Process process = pb.start())
      {
        int exitCode = process.waitFor();

        if (exitCode != 0)
        {
          String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
          throw new IOException("Transformer rejected input: " + stderr);
        }
      }
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Process interrupted", e);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that grader JSON missing the "verdict" field is rejected.
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*(?i)missing.*verdict.*")
  public void missingVerdictFieldIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path inputFile = tempDir.resolve("input.json");
      Path outputFile = tempDir.resolve("output.json");

      String inputJson = """
        {
          "assertion_results": [
            {
              "assertion": "must use rm -f",
              "evidence": "Agent executed: rm -f file.txt",
              "explanation": "The agent correctly used rm -f as required."
            }
          ]
        }""";

      Files.writeString(inputFile, inputJson);

      Path binary = Path.of(System.getProperty("user.home")).
        resolve(".config/claude/plugins/cache/cat/cat/2.1/client/bin/grade-json-transformer");

      ProcessBuilder pb = new ProcessBuilder(
        binary.toString(),
        inputFile.toString(),
        "tc1_run1",
        outputFile.toString());
      try (Process process = pb.start())
      {
        int exitCode = process.waitFor();

        if (exitCode != 0)
        {
          String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
          throw new IOException("Transformer rejected input: " + stderr);
        }
      }
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Process interrupted", e);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
