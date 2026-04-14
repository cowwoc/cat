/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Transforms grader JSON output to canonical schema with validation.
 * <p>
 * Accepts multiple input formats (tolerant reader):
 * <ul>
 *   <li>"assertions" or "assertion_results" field (normalized to "assertion_results")</li>
 *   <li>"pass"/"PASS"/"Pass" verdict (normalized to "PASS")</li>
 *   <li>"fail"/"FAIL"/"Fail" verdict (normalized to "FAIL")</li>
 * </ul>
 */
public final class GradeJsonTransformer
{
  /**
   * Prevent construction.
   */
  private GradeJsonTransformer()
  {
  }

  /**
   * Transforms grader JSON to canonical schema.
   *
   * @param args [input_json_path, test_case_id, output_json_path]
   * @throws IOException if I/O fails
   */
  public static void main(String[] args) throws IOException
  {
    run(args);
  }

  private static void run(String[] args) throws IOException
  {
    if (args.length != 3)
      throw new IllegalArgumentException("Usage: input_json_path test_case_id output_json_path");
    requireThat(args, "args").isNotNull();

    Path inputPath = Path.of(args[0]);
    String testCaseId = args[1];
    Path outputPath = Path.of(args[2]);

    String inputJson = Files.readString(inputPath);
    JsonMapper mapper = JsonMapper.shared();
    JsonNode input = mapper.readTree(inputJson);

    ObjectNode root = mapper.createObjectNode();
    root.put("test_case_id", testCaseId);
    root.putNull("config");

    ArrayNode assertionResults = root.putArray("assertion_results");

    JsonNode resultsNode = input.get("assertion_results");
    if (resultsNode == null)
      resultsNode = input.get("assertions");
    if (resultsNode == null || !resultsNode.isArray())
    {
      throw new IOException(
        "Input JSON missing 'assertion_results' or 'assertions' array field");
    }

    int passCount = 0;
    int failCount = 0;
    for (JsonNode resultNode : resultsNode)
    {
      if (!resultNode.isObject())
        throw new IOException("Assertion result must be an object: " + resultNode);

      JsonNode assertionNode = resultNode.get("assertion");
      JsonNode verdictNode = resultNode.get("verdict");
      JsonNode evidenceNode = resultNode.get("evidence");
      JsonNode explanationNode = resultNode.get("explanation");

      if (assertionNode == null || !assertionNode.isString())
        throw new IOException("Missing or invalid 'assertion' field in: " + resultNode);
      if (verdictNode == null || !verdictNode.isString())
        throw new IOException("Missing or invalid 'verdict' field in: " + resultNode);
      if (evidenceNode == null || !evidenceNode.isString())
        throw new IOException("Missing or invalid 'evidence' field in: " + resultNode);
      if (explanationNode == null || !explanationNode.isString())
        throw new IOException("Missing or invalid 'explanation' field in: " + resultNode);

      String verdict = verdictNode.asString().toUpperCase(Locale.ROOT);
      if (!verdict.equals("PASS") && !verdict.equals("FAIL"))
        throw new IOException("Invalid verdict (must be PASS or FAIL): " + verdictNode.asString());

      ObjectNode result = assertionResults.addObject();
      result.put("assertion", assertionNode.asString());
      result.put("verdict", verdict);
      result.put("evidence", evidenceNode.asString());
      result.put("explanation", explanationNode.asString());

      if (verdict.equals("PASS"))
        ++passCount;
      else
        ++failCount;
    }

    int totalCount = passCount + failCount;
    root.put("pass_count", passCount);
    root.put("fail_count", failCount);
    root.put("total_count", totalCount);
    double passRate;
    if (totalCount > 0)
      passRate = Math.round((double) passCount / totalCount * 100.0) / 100.0;
    else
      passRate = 0.0;
    root.put("pass_rate", passRate);

    Files.writeString(outputPath, mapper.writeValueAsString(root));
    System.out.println(outputPath);
  }
}
