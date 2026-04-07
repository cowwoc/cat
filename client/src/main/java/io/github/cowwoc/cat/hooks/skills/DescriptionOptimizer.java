/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.util.SkillOutput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Prepares a description optimization request by reading the skill file, splitting the eval set
 * into a 60/40 train/test partition, and formatting a structured optimization prompt for the
 * description-optimizer subagent.
 * <p>
 * Accepts four arguments:
 * <ol>
 *   <li>Path to the skill's SKILL.md file</li>
 *   <li>Eval set JSON — a JSON array of objects with {@code query} (string) and
 *       {@code should_trigger} (boolean) fields</li>
 *   <li>Model ID string (e.g., {@code "claude-sonnet-4-5"})</li>
 *   <li>Max iterations — a positive integer string</li>
 * </ol>
 * <p>
 * Outputs a structured optimization request containing:
 * <ul>
 *   <li>The current skill description</li>
 *   <li>Train and test partitions of the eval set (60/40 deterministic split)</li>
 *   <li>Split sizes ({@code train_size} and {@code test_size})</li>
 *   <li>Instructions for the subagent to iterate up to {@code max_iterations} times</li>
 *   <li>Instructions to return {@code best_description} selected by test score</li>
 * </ul>
 * <p>
 * The train/test split is deterministic: the first 60% of entries (rounded down) go to train,
 * and the remainder go to test. With fewer than 2 items the split still assigns at least 1 item
 * to train and the rest to test.
 */
public final class DescriptionOptimizer implements SkillOutput
{
  /**
   * Minimum eval set size accepted by this handler.
   */
  private static final int MIN_EVAL_SET_SIZE = 2;

  private final ClaudeTool scope;

  /**
   * Creates a DescriptionOptimizer instance.
   *
   * @param scope the ClaudeTool for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public DescriptionOptimizer(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Generates a structured description optimization prompt.
   * <p>
   * Reads the skill file to extract the current description, parses the eval set, computes the
   * 60/40 train/test split, and formats the optimization request for the subagent.
   *
   * @param args four arguments: [skill-path, eval-set-json, model-id, max-iterations]
   * @return a formatted optimization prompt as JSON-embedded text
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if the wrong number of arguments is provided, the eval set
   *                                  is empty or too small, or max_iterations is not a positive
   *                                  integer
   * @throws IOException              if the skill file cannot be read
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 4)
      throw new IllegalArgumentException(
        "DescriptionOptimizer requires 4 arguments: " +
        "[skill-path, eval-set-json, model-id, max-iterations]. " +
        "Got " + args.length + " argument(s).");

    String skillPath = args[0];
    String evalSetJson = args[1];
    String modelId = args[2];
    String maxIterationsStr = args[3];

    // Validate and parse max_iterations
    int maxIterations;
    try
    {
      maxIterations = Integer.parseInt(maxIterationsStr.strip());
    }
    catch (NumberFormatException _)
    {
      throw new IllegalArgumentException(
        "DescriptionOptimizer: max_iterations must be a positive integer. " +
        "Got: '" + maxIterationsStr + "'.");
    }
    if (maxIterations <= 0)
      throw new IllegalArgumentException(
        "DescriptionOptimizer: max_iterations must be a positive integer. " +
        "Got: " + maxIterations + ".");

    // Read skill file
    Path skillFile = Path.of(skillPath);
    if (!Files.exists(skillFile))
      throw new IOException("Skill file not found: " + skillPath +
        ". Provide an absolute or relative path to a SKILL.md file.");

    String skillContent = Files.readString(skillFile);
    String currentDescription = SkillFrontmatter.extractDescription(skillContent, skillPath);

    // Parse eval set
    JsonNode evalSetNode = scope.getJsonMapper().readTree(evalSetJson);

    if (!evalSetNode.isArray())
      throw new IllegalArgumentException(
        "DescriptionOptimizer: eval-set-json must be a JSON array. " +
        "Got: " + evalSetNode.getNodeType());

    int totalSize = evalSetNode.size();
    if (totalSize < MIN_EVAL_SET_SIZE)
      throw new IllegalArgumentException(
        "DescriptionOptimizer: eval_set must contain at least " + MIN_EVAL_SET_SIZE +
        " entries for a meaningful 60/40 train/test split. " +
        "Got " + totalSize + " entry/entries.");

    // Validate each eval item has required fields
    int itemIndex = 0;
    for (JsonNode item : evalSetNode)
    {
      if (item.path("query").isMissingNode() || item.path("query").asString("").isBlank())
        throw new IllegalArgumentException(
          "DescriptionOptimizer: eval_set item at index " + itemIndex +
          " is missing a non-empty 'query' field. Each eval item must have 'query' (string) " +
          "and 'should_trigger' (boolean) fields. Found: " + item);
      if (item.path("should_trigger").isMissingNode())
        throw new IllegalArgumentException(
          "DescriptionOptimizer: eval_set item at index " + itemIndex +
          " is missing a 'should_trigger' field. Each eval item must have 'query' (string) " +
          "and 'should_trigger' (boolean) fields. Found: " + item);
      itemIndex += 1;
    }

    // Compute 60/40 split: first 60% → train, remainder → test
    int trainSize = Math.max(1, (int) Math.floor(totalSize * 0.6));
    int testSize = totalSize - trainSize;

    // Build train and test arrays
    ArrayNode trainArray = scope.getJsonMapper().createArrayNode();
    ArrayNode testArray = scope.getJsonMapper().createArrayNode();
    int idx = 0;
    for (JsonNode item : evalSetNode)
    {
      if (idx < trainSize)
        trainArray.add(item);
      else
        testArray.add(item);
      idx += 1;
    }

    // Build the structured output JSON
    ObjectNode outputNode = scope.getJsonMapper().createObjectNode();
    outputNode.put("current_description", currentDescription);
    outputNode.put("skill_path", skillPath);
    outputNode.put("model_id", modelId);
    outputNode.put("max_iterations", maxIterations);
    outputNode.put("train_size", trainSize);
    outputNode.put("test_size", testSize);
    outputNode.set("train_set", trainArray);
    outputNode.set("test_set", testArray);

    String splitJson = scope.getJsonMapper().writer().
      withFeatures(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT).
      writeValueAsString(outputNode);

    return formatOptimizationPrompt(currentDescription, skillPath, modelId, maxIterations,
      trainSize, testSize, splitJson);
  }

  /**
   * Formats the optimization prompt from the parsed inputs and split data.
   *
   * @param currentDescription the current skill description
   * @param skillPath          the path to the skill file
   * @param modelId            the model ID to use for evaluation
   * @param maxIterations      the maximum number of optimization iterations
   * @param trainSize          the number of train set items
   * @param testSize           the number of test set items
   * @param splitJson          the full split JSON (train_set, test_set, sizes, etc.)
   * @return the formatted optimization prompt
   */
  private String formatOptimizationPrompt(String currentDescription, String skillPath,
    String modelId, int maxIterations, int trainSize, int testSize, String splitJson)
  {
    return """
      DESCRIPTION OPTIMIZATION REQUEST
      =================================

      Skill: %s
      Model: %s
      Max iterations: %d
      Train set size: %d (60%%)
      Test set size: %d (40%%)

      Current description:
      %s

      Optimization loop instructions:
      1. For each iteration (up to %d):
         a. Score the current description against the train set.
            A query "passes" if the description would correctly predict should_trigger.
         b. If train score = 100%% or no improvement over previous iteration, stop early.
         c. Propose an improved description and score it against the train set.
         d. Keep the description with the higher train score.
      2. After completing iterations, score the best description against the test set.
      3. Return a JSON object with:
         - "best_description": the description with the highest test score
         - "iterations": an array of per-iteration objects, each containing:
           - "iteration": iteration number (1-based)
           - "description": the description tested
           - "train_score": fraction of train items predicted correctly (0.0-1.0)
           - "test_score": fraction of test items predicted correctly (0.0-1.0, only in final)

      Evaluation criteria for "passes":
      - A query passes if the description clearly covers the user's intent (should_trigger=true)
        OR clearly does NOT cover it (should_trigger=false)
      - Use the description text to determine trigger intent, not the query content alone

      Split data (use exactly these train/test partitions):

      %s
      """.formatted(skillPath, modelId, maxIterations, trainSize, testSize,
      currentDescription, maxIterations, splitJson);
  }
}
