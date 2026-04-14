/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.tool.MainClaudeTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates and transforms grader JSON from the grader agent's temp format to the canonical schema.
 * <p>
 * The grader agent produces a temp JSON with only "assertion_results" array. This tool validates
 * the schema, adds metadata fields (test_case_id, stats), and writes the canonical output.
 */
public final class GradeJsonTransformer
{
  /**
   * Creates a new transformer.
   */
  public GradeJsonTransformer()
  {
  }

  /**
   * Validates and transforms grader JSON.
   *
   * @param inputPath path to the temp JSON file from the grader agent
   * @param runId the run ID (e.g., "tc1_run1")
   * @param outputPath path where the canonical JSON should be written
   * @param scope the scope providing JSON mapper
   * @throws IOException if validation fails or I/O error occurs
   * @throws NullPointerException if any parameter is null
   */
  public void transform(Path inputPath, String runId, Path outputPath, JvmScope scope) throws IOException
  {
    requireThat(inputPath, "inputPath").isNotNull();
    requireThat(runId, "runId").isNotBlank();
    requireThat(outputPath, "outputPath").isNotNull();
    requireThat(scope, "scope").isNotNull();

    JsonMapper mapper = scope.getJsonMapper();
    String inputJson = Files.readString(inputPath);
    JsonNode input = mapper.readTree(inputJson);

    // Validate top-level structure
    if (!input.has("assertion_results"))
      throw new IOException("Grade JSON missing required field: assertion_results");

    JsonNode assertionResults = input.path("assertion_results");
    if (!assertionResults.isArray())
      throw new IOException("assertion_results must be an array");

    ArrayNode results = (ArrayNode) assertionResults;

    // Validate each assertion result
    for (int i = 0; i < results.size(); ++i)
    {
      JsonNode result = results.get(i);
      validateAssertionResult(result, i);
    }

    // Build canonical output
    ObjectNode output = mapper.createObjectNode();
    output.put("test_case_id", runId);
    output.set("assertion_results", results);

    // Compute stats
    int passCount = 0;
    int failCount = 0;
    for (int i = 0; i < results.size(); ++i)
    {
      String verdict = results.get(i).path("verdict").asString();
      if ("PASS".equals(verdict))
        ++passCount;
      else if ("FAIL".equals(verdict))
        ++failCount;
    }

    ObjectNode stats = mapper.createObjectNode();
    stats.put("total", results.size());
    stats.put("pass", passCount);
    stats.put("fail", failCount);
    output.set("stats", stats);

    // Write canonical output
    String outputJson = mapper.writeValueAsString(output);
    Files.writeString(outputPath, outputJson);

    // Print output path to stdout (expected by caller)
    System.out.println(outputPath);
  }

  /**
   * Validates a single assertion result object.
   *
   * @param result the assertion result object to validate
   * @param index the array index (for error messages)
   * @throws IOException if validation fails
   */
  private void validateAssertionResult(JsonNode result, int index) throws IOException
  {
    if (!result.has("assertion"))
      throw new IOException("assertion_results[" + index + "] missing required field: assertion");

    if (!result.has("verdict"))
    {
      // Check if they used wrong field name "status" instead of "verdict"
      if (result.has("status"))
        throw new IOException("assertion_results[" + index + "] has wrong field name 'status'. " +
          "Must use 'verdict' instead.");
      throw new IOException("assertion_results[" + index + "] missing required field: verdict");
    }

    if (!result.has("evidence"))
      throw new IOException("assertion_results[" + index + "] missing required field: evidence");

    if (!result.has("explanation"))
      throw new IOException("assertion_results[" + index + "] missing required field: explanation");

    String verdict = result.path("verdict").asString();
    if (!"PASS".equals(verdict) && !"FAIL".equals(verdict))
      throw new IOException("assertion_results[" + index + "] verdict must be PASS or FAIL, got: " + verdict);
  }

  /**
   * CLI entry point.
   *
   * @param args command-line arguments: inputPath runId outputPath
   */
  public static void main(String[] args)
  {
    if (args.length != 3)
    {
      System.err.println("Usage: grade-json-transformer <inputPath> <runId> <outputPath>");
      System.exit(1);
    }

    Path inputPath = Path.of(args[0]);
    String runId = args[1];
    Path outputPath = Path.of(args[2]);

    try (ClaudeTool scope = new MainClaudeTool())
    {
      new GradeJsonTransformer().transform(inputPath, runId, outputPath, scope);
    }
    catch (IOException e)
    {
      Logger log = LoggerFactory.getLogger(GradeJsonTransformer.class);
      log.error("Failed to transform grade JSON", e);
      System.err.println("ERROR: " + e.getMessage());
      System.exit(1);
    }
  }
}
