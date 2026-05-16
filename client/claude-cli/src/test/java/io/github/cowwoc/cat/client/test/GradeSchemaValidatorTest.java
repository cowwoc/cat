/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.util.GradeSchemaValidator;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link GradeSchemaValidator}.
 */
public final class GradeSchemaValidatorTest
{
  /**
   * Verifies that canonical grade JSON passes schema validation.
   *
   * @throws IOException if JSON parsing fails unexpectedly
   */
  @Test
  public void acceptsCanonicalSchema() throws IOException
  {
    JsonMapper mapper = JsonMapper.shared();
    JsonNode gradeNode = mapper.readTree("""
      {
        "test_case_id": "tc1_run_1",
        "config": null,
        "assertion_results": [
          {
            "assertion": "a",
            "verdict": "PASS",
            "evidence": "e",
            "explanation": "x"
          },
          {
            "assertion": "b",
            "verdict": "FAIL",
            "evidence": "e2",
            "explanation": "x2"
          }
        ],
        "pass_count": 1,
        "fail_count": 1,
        "total_count": 2,
        "pass_rate": 0.5
      }
      """);
    String verdict = GradeSchemaValidator.validateAndExtractVerdict(gradeNode, Path.of("grade.json"));
    requireThat(verdict, "verdict").isEqualTo("FAIL");
  }

  /**
   * Verifies that missing test_case_id fails schema validation.
   *
   * @throws IOException if validation unexpectedly succeeds
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*missing required non-empty 'test_case_id' string.*")
  public void rejectsMissingTestCaseId() throws IOException
  {
    JsonMapper mapper = JsonMapper.shared();
    JsonNode gradeNode = mapper.readTree("""
      {
        "assertion_results": [
          {
            "assertion": "a",
            "verdict": "PASS",
            "evidence": "e",
            "explanation": "x"
          }
        ],
        "pass_count": 1,
        "fail_count": 0,
        "total_count": 1,
        "pass_rate": 1.0
      }
      """);
    GradeSchemaValidator.validateAndExtractVerdict(gradeNode, Path.of("grade.json"));
  }

  /**
   * Verifies that empty test_case_id fails schema validation.
   *
   * @throws IOException if validation unexpectedly succeeds
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*missing required non-empty 'test_case_id' string.*")
  public void rejectsEmptyTestCaseId() throws IOException
  {
    JsonMapper mapper = JsonMapper.shared();
    JsonNode gradeNode = mapper.readTree("""
      {
        "test_case_id": "   ",
        "assertion_results": [
          {
            "assertion": "a",
            "verdict": "PASS",
            "evidence": "e",
            "explanation": "x"
          }
        ],
        "pass_count": 1,
        "fail_count": 0,
        "total_count": 1,
        "pass_rate": 1.0
      }
      """);
    GradeSchemaValidator.validateAndExtractVerdict(gradeNode, Path.of("grade.json"));
  }

  /**
   * Verifies that inconsistent stats fail schema validation.
   *
   * @throws IOException if validation unexpectedly succeeds
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*inconsistent 'pass_count'.*")
  public void rejectsInconsistentCounts() throws IOException
  {
    JsonMapper mapper = JsonMapper.shared();
    JsonNode gradeNode = mapper.readTree("""
      {
        "test_case_id": "tc1_run_1",
        "config": null,
        "assertion_results": [
          {
            "assertion": "a",
            "verdict": "PASS",
            "evidence": "e",
            "explanation": "x"
          }
        ],
        "pass_count": 0,
        "fail_count": 0,
        "total_count": 1,
        "pass_rate": 1.0
      }
      """);
    GradeSchemaValidator.validateAndExtractVerdict(gradeNode, Path.of("grade.json"));
  }
}
