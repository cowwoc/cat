/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.skills.GradeJsonTransformer;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link GradeJsonTransformer}.
 */
public final class GradeJsonTransformerTest
{
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

      try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
      {
        new GradeJsonTransformer().transform(inputFile, "tc1_run1", outputFile, scope);

        requireThat(Files.exists(outputFile), "outputFileExists").isTrue();

        String outputJson = Files.readString(outputFile);
        JsonNode output = scope.getJsonMapper().readTree(outputJson);

        requireThat(output.has("test_case_id"), "hasTestCaseId").isTrue();
        requireThat(output.path("test_case_id").asString(), "testCaseId").isEqualTo("tc1_run1");
        requireThat(output.has("assertion_results"), "hasAssertionResults").isTrue();
        requireThat(output.path("assertion_results").isArray(), "assertionResultsIsArray").isTrue();
        requireThat(output.has("stats"), "hasStats").isTrue();
      }
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
    expectedExceptionsMessageRegExp = ".*status.*verdict.*")
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
              "status": "pass",
              "evidence": "Agent executed: rm -f file.txt",
              "explanation": "The agent correctly used rm -f as required."
            }
          ]
        }""";

      Files.writeString(inputFile, inputJson);

      try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
      {
        new GradeJsonTransformer().transform(inputFile, "tc1_run1", outputFile, scope);
      }
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

      try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
      {
        new GradeJsonTransformer().transform(inputFile, "tc1_run1", outputFile, scope);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
