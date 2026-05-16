/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Validates canonical grader JSON schema and consistency.
 */
public final class GradeSchemaValidator
{
  private GradeSchemaValidator()
  {
  }

  /**
   * Validates a canonical grade JSON object.
   *
   * @param gradeNode the JSON object to validate
   * @param gradePath path used in validation errors
   * @return PASS if all assertions pass, otherwise FAIL
   * @throws IOException if validation fails
   */
  public static String validateAndExtractVerdict(JsonNode gradeNode, Path gradePath)
    throws IOException
  {
    if (!gradeNode.isObject())
      throw new IOException("Grade file root must be an object: " + gradePath);

    JsonNode testCaseId = gradeNode.path("test_case_id");
    if (!testCaseId.isString() || testCaseId.asString().isBlank())
      throw new IOException("Grade file missing required non-empty 'test_case_id' string: " +
        gradePath);

    JsonNode assertionResults = gradeNode.path("assertion_results");
    if (assertionResults.isMissingNode() || !assertionResults.isArray())
      throw new IOException("Grade file missing required 'assertion_results' array: " + gradePath);

    ArrayNode results = (ArrayNode) assertionResults;
    if (results.isEmpty())
      throw new IOException("Grade file has empty assertion_results: " + gradePath);

    int passCount = 0;
    int failCount = 0;
    for (JsonNode result : results)
    {
      if (!result.isObject())
      {
        throw new IOException("assertion_results must contain objects only. Grade file: " +
          gradePath);
      }
      validateRequiredTextField((ObjectNode) result, "assertion", gradePath);
      String verdict = validateVerdictField((ObjectNode) result, gradePath);
      validateRequiredTextField((ObjectNode) result, "evidence", gradePath);
      validateRequiredTextField((ObjectNode) result, "explanation", gradePath);
      if (verdict.equals("PASS"))
        ++passCount;
      else
        ++failCount;
    }

    int totalCount = results.size();
    validateRequiredCount(gradeNode, "pass_count", passCount, gradePath);
    validateRequiredCount(gradeNode, "fail_count", failCount, gradePath);
    validateRequiredCount(gradeNode, "total_count", totalCount, gradePath);
    validatePassRate(gradeNode, passCount, totalCount, gradePath);

    if (failCount == 0)
      return "PASS";
    return "FAIL";
  }

  private static void validateRequiredTextField(ObjectNode result, String fieldName,
    Path gradePath) throws IOException
  {
    JsonNode value = result.path(fieldName);
    if (!value.isString() || value.asString().isBlank())
    {
      throw new IOException("Grader output missing required non-empty '" + fieldName +
        "' string. Grade file: " + gradePath);
    }
  }

  private static String validateVerdictField(ObjectNode result, Path gradePath) throws IOException
  {
    JsonNode verdictNode = result.path("verdict");
    if (!verdictNode.isString() || verdictNode.asString().isBlank())
    {
      StringJoiner foundFields = new StringJoiner(", ");
      for (Map.Entry<String, JsonNode> entry : result.properties())
        foundFields.add(entry.getKey());
      throw new IOException("Grader output missing required 'verdict' field. " +
        "Found fields: [" + foundFields + "]. " +
        "Expected exactly {\"verdict\": \"PASS\"} or {\"verdict\": \"FAIL\"}. " +
        "Grade file: " + gradePath);
    }
    String verdict = verdictNode.asString();
    if (!verdict.equals("PASS") && !verdict.equals("FAIL"))
      throw new IOException("Invalid verdict value: '" + verdict + "'. " +
        "Must be exactly 'PASS' or 'FAIL'. Grade file: " + gradePath);
    return verdict;
  }

  private static void validateRequiredCount(JsonNode gradeNode, String fieldName,
    int expectedValue, Path gradePath) throws IOException
  {
    JsonNode countNode = gradeNode.path(fieldName);
    if (!countNode.isInt() || countNode.asInt() < 0)
    {
      throw new IOException("Grade file missing required non-negative integer '" + fieldName +
        "'. Grade file: " + gradePath);
    }
    int actualValue = countNode.asInt();
    if (actualValue != expectedValue)
    {
      throw new IOException("Grade file has inconsistent '" + fieldName + "'. Expected " +
        expectedValue + " but got " + actualValue + ". Grade file: " + gradePath);
    }
  }

  private static void validatePassRate(JsonNode gradeNode, int passCount, int totalCount,
    Path gradePath) throws IOException
  {
    JsonNode passRateNode = gradeNode.path("pass_rate");
    if (!passRateNode.isNumber())
      throw new IOException("Grade file missing required numeric 'pass_rate'. Grade file: " +
        gradePath);
    double actualPassRate = passRateNode.asDouble();
    double expectedPassRate;
    if (totalCount > 0)
      expectedPassRate = (double) passCount / totalCount;
    else
      expectedPassRate = 0.0;
    if (Math.abs(actualPassRate - expectedPassRate) > 1e-9)
    {
      throw new IOException("Grade file has inconsistent 'pass_rate'. Expected " +
        expectedPassRate + " but got " + actualPassRate + ". Grade file: " + gradePath);
    }
  }
}
