/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.agent.ProcessRunner;
import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Handles SPRT result aggregation and persistence.
 */
final class SprtResultsManager
{
  private static final DateTimeFormatter ISO_UTC =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
  private final CliTool scope;

  /**
   * Creates a new result manager.
   *
   * @param scope the shared CLI scope
   */
  SprtResultsManager(CliTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Implements the {@code merge-results} command.
   *
   * @param args {@code [new_sprt_state_path, prior_instruction_test_json_path, carryforward_ids_json, model_id]}
   *
   * @return the merged result JSON
   *
   * @throws IOException if files cannot be read
   */
  String mergeResults(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 4)
    {
      throw new IllegalArgumentException(
        "SprtRunner merge-results: expected 4 arguments, got " + args.length + ".\n" +
          "Usage: skill-test-runner merge-results <new_sprt_state_path> " +
          "<prior_instruction_test_json_path> <carryforward_ids_json> <model_id>");
    }

    Path statePath = Path.of(args[0]);
    String priorInstructionTestPath = args[1];
    String carryforwardIdsJson = args[2];
    String modelId = args[3];
    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "SprtRunner merge-results: state file not found: " + statePath);

    boolean hasPrior = !priorInstructionTestPath.equals("none");
    if (hasPrior && Files.notExists(Path.of(priorInstructionTestPath)))
    {
      throw new IllegalArgumentException(
        "SprtRunner merge-results: prior instruction-test file not found: " +
          priorInstructionTestPath);
    }

    JsonMapper mapper = scope.getJsonMapper();
    JsonNode stateRoot = mapper.readTree(statePath.toFile());
    JsonNode sprtStateNode = stateRoot.path("sprt_state");
    String effort = stateRoot.path("effort").asString("");
    Set<String> carryforwardIds = parseCarryforwardIds(carryforwardIdsJson, mapper);
    Map<String, JsonNode> priorByTestCaseId = priorByTestCaseId(hasPrior,
      priorInstructionTestPath, modelId, effort, mapper);
    String overallDecision = "ACCEPT";
    ArrayNode testCasesArray = mapper.createArrayNode();

    if (sprtStateNode.isObject())
    {
      for (Map.Entry<String, JsonNode> entry : sprtStateNode.properties())
      {
        String testCaseId = entry.getKey();
        JsonNode testCase = mergeTestCase(entry.getValue(), testCaseId, carryforwardIds,
          priorByTestCaseId, mapper);
        String decision = testCase.path("decision").asString("INCONCLUSIVE");
        if (decision.equals("REJECT"))
          overallDecision = "REJECT";
        else if (decision.equals("INCONCLUSIVE") && !overallDecision.equals("REJECT"))
          overallDecision = "INCONCLUSIVE";
        testCasesArray.add(testCase);
      }
    }

    ObjectNode result = mapper.createObjectNode();
    result.put("model_id", modelId);
    result.put("timestamp", ISO_UTC.format(Instant.now()));
    result.put("overall_decision", overallDecision);
    result.put("incremental", true);
    result.set("test_cases", testCasesArray);
    return compactJson(result);
  }

  /**
   * Implements the {@code write-test-results} command.
   *
   * @param args {@code [worktree_path, sprt_state_path, test_dir_path]}
   *
   * @return {@code key=value} result lines
   *
   * @throws IOException if the state file cannot be read or the JSON cannot be written
   */
  String writeTestResults(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
    {
      throw new IllegalArgumentException(
        "SprtRunner write-test-results: expected 3 arguments " +
          "<worktree_path> <sprt_state_path> <test_dir_path>, got " + args.length + ".\n" +
          "Usage: sprt-runner write-test-results " +
          "<worktree_path> <sprt_state_path> <test_dir_path>");
    }

    Path worktreePath = Path.of(args[0]);
    Path sprtStatePath = Path.of(args[1]);
    Path testDirPath = Path.of(args[2]);
    if (Files.notExists(sprtStatePath))
    {
      throw new IllegalArgumentException(
        "SprtRunner write-test-results: state file not found: " + sprtStatePath);
    }

    JsonMapper mapper = scope.getJsonMapper();
    JsonNode stateRoot = mapper.readTree(sprtStatePath.toFile());
    JsonNode sprtStateNode = stateRoot.path("sprt_state");
    ObjectNode sprtNode = buildSprtSummary(sprtStateNode, mapper);
    String modelId = requiredString(stateRoot, "model_id",
      "SprtRunner write-test-results: sprt state is missing required field model_id");
    String effort = stateRoot.path("effort").asString("");
    JsonNode failedIdsNode = stateRoot.path("failed_test_ids");
    if (!failedIdsNode.isArray())
    {
      throw new IllegalStateException(
        "SprtRunner write-test-results: sprt state is missing required field failed_test_ids");
    }

    ObjectNode currentResult = mapper.createObjectNode();
    currentResult.put("model_id", modelId);
    currentResult.put("effort", effort);
    currentResult.set("failed_test_ids", failedIdsNode);
    currentResult.set("sprt", sprtNode);

    Path testResultsFile = testDirPath.resolve("test-results.json");
    ObjectNode output = loadExistingResults(testResultsFile, mapper);
    output.set(resultKey(modelId, effort), currentResult);
    output.put("model_id", modelId);
    output.put("effort", effort);
    output.set("failed_test_ids", failedIdsNode.deepCopy());
    output.set("sprt", sprtNode.deepCopy());

    Files.createDirectories(testDirPath);
    Files.writeString(testResultsFile, prettyJson(output), UTF_8);
    stageResultsFile(worktreePath, testResultsFile);
    return commitResults(worktreePath, testDirPath.getFileName().toString(),
      sprtNode.path("overall_decision").asString(""));
  }

  /**
   * Returns the failed-test array from a persisted test-results file for a specific model/effort.
   *
   * @param testResultsPath the test-results file
   *
   * @param modelId the model id
   *
   * @param effort the effort level
   *
   * @return the failed-test array, or a missing node if not found
   *
   * @throws IOException if the file cannot be read
   */
  JsonNode failedTestIds(Path testResultsPath, String modelId, String effort) throws IOException
  {
    JsonNode priorResults = scope.getJsonMapper().readTree(testResultsPath.toFile());
    return findResultForModelAndEffort(priorResults, modelId, effort).path("failed_test_ids");
  }

  /**
   * Converts an object to compact JSON (single line without indentation).
   *
   * @param value the object to serialize
   *
   * @return compact JSON representation
   */
  String compactJson(Object value)
  {
    try
    {
      return scope.getJsonMapper().writer().
        withoutFeatures(SerializationFeature.INDENT_OUTPUT).
        writeValueAsString(value);
    }
    catch (Exception e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Parses carry-forward ids.
   *
   * @param carryforwardIdsJson the carryforwardIdsJson
   *
   * @param mapper the mapper
   *
   * @return the result
   */
  private static Set<String> parseCarryforwardIds(String carryforwardIdsJson, JsonMapper mapper)
    throws IOException
  {
    Set<String> carryforwardIds = new HashSet<>();
    JsonNode carryforwardNode = mapper.readTree(carryforwardIdsJson);
    if (carryforwardNode.isArray())
    {
      for (JsonNode element : carryforwardNode)
      {
        if (element.isString())
          carryforwardIds.add(element.asString());
      }
    }
    return carryforwardIds;
  }

  /**
   * Builds a lookup map of prior test-case results.
   *
   * @param hasPrior the hasPrior
   *
   * @param priorInstructionTestPath the priorInstructionTestPath
   *
   * @param modelId the modelId
   *
   * @param effort the effort
   *
   * @param mapper the mapper
   *
   * @return the result
   */
  private Map<String, JsonNode> priorByTestCaseId(boolean hasPrior, String priorInstructionTestPath,
    String modelId, String effort, JsonMapper mapper) throws IOException
  {
    Map<String, JsonNode> priorByTestCaseId = new HashMap<>();
    if (!hasPrior)
      return priorByTestCaseId;
    JsonNode priorRoot = mapper.readTree(Path.of(priorInstructionTestPath).toFile());
    JsonNode priorTestCases = priorTestCases(priorRoot, modelId, effort);
    if (priorTestCases.isArray())
    {
      for (JsonNode testCase : priorTestCases)
      {
        String testCaseId = testCase.path("test_case_id").asString("");
        if (!testCaseId.isBlank())
          priorByTestCaseId.put(testCaseId, testCase);
      }
    }
    return priorByTestCaseId;
  }

  /**
   * Merges one test-case result.
   *
   * @param currentTestCase the currentTestCase
   *
   * @param testCaseId the testCaseId
   *
   * @param carryforwardIds the carryforwardIds
   *
   * @param priorByTestCaseId the priorByTestCaseId
   *
   * @param mapper the mapper
   *
   * @return the result
   */
  private static JsonNode mergeTestCase(JsonNode currentTestCase, String testCaseId,
    Set<String> carryforwardIds, Map<String, JsonNode> priorByTestCaseId, JsonMapper mapper)
  {
    boolean usePriorStats = carryforwardIds.contains(testCaseId) &&
      priorByTestCaseId.containsKey(testCaseId);
    JsonNode source = currentTestCase;
    boolean carriedForward = currentTestCase.path("carried_forward").asBoolean(false);
    if (usePriorStats)
    {
      source = priorByTestCaseId.get(testCaseId);
      carriedForward = true;
    }
    ObjectNode testCase = mapper.createObjectNode();
    testCase.put("test_case_id", testCaseId);
    testCase.put("log_ratio", source.path("log_ratio").asDouble(0.0));
    testCase.put("passes", source.path("passes").asInt(0));
    testCase.put("fails", source.path("fails").asInt(0));
    testCase.put("runs", source.path("runs").asInt(0));
    testCase.put("decision", source.path("decision").asString("INCONCLUSIVE"));
    testCase.put("carried_forward", carriedForward);
    return testCase;
  }

  /**
   * Builds the `sprt` node written to test-results.json.
   *
   * @param sprtStateNode the sprtStateNode
   *
   * @param mapper the mapper
   *
   * @return the result
   */
  private static ObjectNode buildSprtSummary(JsonNode sprtStateNode, JsonMapper mapper)
  {
    String overallDecision = "ACCEPT";
    ArrayNode testCasesArray = mapper.createArrayNode();
    if (sprtStateNode.isObject())
    {
      for (Map.Entry<String, JsonNode> entry : sprtStateNode.properties())
      {
        String testCaseId = entry.getKey();
        JsonNode testCaseNode = entry.getValue();
        String decision = testCaseNode.path("decision").asString("INCONCLUSIVE");
        if (decision.equals("REJECT"))
          overallDecision = "REJECT";
        else if (decision.equals("INCONCLUSIVE") && !overallDecision.equals("REJECT"))
          overallDecision = "INCONCLUSIVE";

        ObjectNode testCase = mapper.createObjectNode();
        testCase.put("test_case_id", testCaseId);
        testCase.put("decision", decision);
        testCase.put("log_ratio", testCaseNode.path("log_ratio").asDouble(0.0));
        testCase.put("pass_count", testCaseNode.path("passes").asInt(0));
        testCase.put("fail_count", testCaseNode.path("fails").asInt(0));
        testCase.put("total_runs", testCaseNode.path("runs").asInt(0));
        testCase.put("total_tokens", 0);
        testCase.put("total_duration_ms", 0);
        testCasesArray.add(testCase);
      }
    }
    ObjectNode sprtNode = mapper.createObjectNode();
    sprtNode.set("test_cases", testCasesArray);
    sprtNode.put("overall_decision", overallDecision);
    sprtNode.put("total_tokens", 0);
    sprtNode.put("total_duration_ms", 0);
    return sprtNode;
  }

  /**
   * Loads an existing test-results file when present.
   *
   * @param testResultsFile the testResultsFile
   *
   * @param mapper the mapper
   *
   * @return the result
   */
  private static ObjectNode loadExistingResults(Path testResultsFile, JsonMapper mapper)
    throws IOException
  {
    ObjectNode output = mapper.createObjectNode();
    if (Files.exists(testResultsFile))
    {
      JsonNode existing = mapper.readTree(testResultsFile.toFile());
      if (existing.isObject())
        output.setAll((ObjectNode) existing);
    }
    return output;
  }

  /**
   * Stages the test-results file.
   *
   * @param worktreePath the worktreePath
   *
   * @param testResultsFile the testResultsFile
   */
  private static void stageResultsFile(Path worktreePath, Path testResultsFile) throws IOException
  {
    ProcessRunner.Result addResult = ProcessRunner.run(worktreePath,
      "git", "add", "--", testResultsFile.toAbsolutePath().toString());
    if (addResult.exitCode() != 0)
    {
      throw new IOException(
        "SprtRunner write-test-results: git add failed with exit code " +
          addResult.exitCode() + ": " + addResult.output());
    }
  }

  /**
   * Commits the test-results file with retry.
   *
   * @param worktreePath the worktreePath
   *
   * @param testDirName the testDirName
   *
   * @param overallDecision the overallDecision
   *
   * @return the result
   */
  private static String commitResults(Path worktreePath, String testDirName, String overallDecision)
    throws IOException
  {
    String commitMessage = "test-results: update " + testDirName;
    Random random = new Random();
    boolean committed = false;
    for (int attempt = 1; attempt <= 3; ++attempt)
    {
      ProcessRunner.Result commitResult = ProcessRunner.run(worktreePath,
        "git", "commit", "-m", commitMessage);
      if (commitResult.exitCode() == 0)
      {
        committed = true;
        break;
      }
      if (attempt < 3)
      {
        long baseMilliseconds = (long) Math.pow(2, attempt) * 1000L;
        long jitterMilliseconds = (long) (random.nextDouble() * baseMilliseconds);
        try
        {
          Thread.sleep(baseMilliseconds + jitterMilliseconds);
        }
        catch (InterruptedException _)
        {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    if (!committed)
      return "status=error\nmessage=git commit failed after 3 attempts";

    ProcessRunner.Result shaResult = ProcessRunner.run(worktreePath, "git", "rev-parse", "HEAD");
    StringJoiner resultLines = new StringJoiner("\n");
    resultLines.add("status=ok");
    resultLines.add("overall_decision=" + overallDecision);
    String testSha = "";
    if (shaResult.exitCode() == 0)
      testSha = shaResult.output().trim();
    resultLines.add("test_sha=" + testSha);
    return resultLines.toString();
  }

  /**
   * Returns a required string field.
   *
   * @param node the node
   *
   * @param fieldName the fieldName
   *
   * @param errorMessage the errorMessage
   *
   * @return the result
   */
  private static String requiredString(JsonNode node, String fieldName, String errorMessage)
  {
    String value = node.path(fieldName).asString("");
    if (value.isBlank())
      throw new IllegalStateException(errorMessage);
    return value;
  }

  /**
   * Builds a stable key for a [model_id, effort] result entry.
   *
   * @param modelId the modelId
   *
   * @param effort the effort
   *
   * @return the result
   */
  private static String resultKey(String modelId, String effort)
  {
    if (effort.isBlank())
      return modelId + "|default";
    return modelId + "|" + effort;
  }

  /**
   * Returns the instruction-test test_cases array from either the legacy flat shape or a persisted
   * per-model result entry.
   *
   * @param root the root
   *
   * @param modelId the modelId
   *
   * @param effort the effort
   *
   * @return the result
   */
  private JsonNode priorTestCases(JsonNode root, String modelId, String effort)
  {
    JsonNode direct = root.path("test_cases");
    if (direct.isArray())
      return direct;
    return findResultForModelAndEffort(root, modelId, effort).path("sprt").path("test_cases");
  }

  /**
   * Finds the result entry matching the requested [model, effort] tuple.
   *
   * @param root the root
   *
   * @param modelId the modelId
   *
   * @param effort the effort
   *
   * @return the result
   */
  private JsonNode findResultForModelAndEffort(JsonNode root, String modelId, String effort)
  {
    JsonNode exact = root.path(resultKey(modelId, effort));
    if (exact.isObject())
      return exact;
    String rootModelId = root.path("model_id").asString("");
    String rootEffort = root.path("effort").asString("");
    if (rootModelId.equals(modelId) && rootEffort.equals(effort))
      return root;
    if (rootModelId.equals(modelId) && rootEffort.isBlank() && effort.isBlank())
      return root;
    return scope.getJsonMapper().missingNode();
  }

  /**
   * Serializes the given value to a pretty-printed JSON string.
   *
   * @param value the value
   *
   * @return the result
   */
  private String prettyJson(Object value)
  {
    try
    {
      return scope.getJsonMapper().writeValueAsString(value);
    }
    catch (Exception e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }
}
