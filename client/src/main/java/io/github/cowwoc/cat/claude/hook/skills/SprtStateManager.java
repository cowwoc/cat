/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

import io.github.cowwoc.cat.claude.hook.ClaudePluginScope;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Manages SPRT (Sequential Probability Ratio Test) state JSON files.
 * <p>
 * Handles initialization, update, boundary checking, and smoke-test status operations
 * on SPRT state files persisted as JSON.
 */
final class SprtStateManager
{
  /**
   * SPRT log-likelihood increment for a passing observation.
   * <p>
   * Equals {@code ln(p0/p1) = ln(0.95/0.85)}.
   */
  private static final double SPRT_LOG_PASS = 0.1112;
  /**
   * SPRT log-likelihood increment for a failing observation.
   * <p>
   * Equals {@code ln((1-p0)/(1-p1)) = ln(0.05/0.15)}.
   */
  private static final double SPRT_LOG_FAIL = -1.0986;
  /**
   * SPRT upper acceptance boundary.
   * <p>
   * Equals {@code ln((1-beta)/alpha) = ln(19)}.
   */
  private static final double SPRT_ACCEPT = 2.944;
  /**
   * SPRT lower rejection boundary.
   * <p>
   * Equals {@code ln(beta/(1-alpha)) = ln(0.0526)}.
   */
  private static final double SPRT_REJECT = -2.944;
  /**
   * Number of smoke-test runs before escalating to full SPRT.
   */
  private static final int SMOKE_RUNS = 3;
  /**
   * Prior compliance boost applied when {@code --prior-boost} is enabled.
   * <p>
   * Equivalent to approximately 10 prior PASS observations.
   */
  private static final double PRIOR_BOOST = 1.112;

  private final Logger log = LoggerFactory.getLogger(SprtStateManager.class);
  private final ClaudePluginScope scope;

  /**
   * Creates a new SprtStateManager.
   *
   * @param scope the Claude plugin scope providing JSON mapper and other services
   * @throws NullPointerException if {@code scope} is null
   */
  SprtStateManager(ClaudePluginScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Implements the {@code init-sprt} command.
   * <p>
   * Creates or replaces the SPRT state file at {@code sprt_state_path}, carrying forward prior results
   * when the same model is used and merging with fresh-run entries for all IDs in {@code rerun_tc_ids_json}.
   *
   * @param args {@code [sprt_state_path, rerun_tc_ids_json, prior_instruction_test_json_path,
   *             next_model_id, session_id, [--prior-boost] [--effort <level>]]}
   * @return compact JSON {@code {"ok":true}}
   * @throws IllegalArgumentException if arguments are invalid or the prior instruction-test is not found
   * @throws IOException              if the state file cannot be written
   */
  String initSprt(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length < 5)
      throw new IllegalArgumentException(
        "InstructionTestRunner init-sprt: expected at least 5 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner init-sprt <sprt_state_path> <rerun_tc_ids_json> " +
        "<prior_instruction_test_json_path> <next_model_id> <session_id> [--prior-boost]");

    Path sprtStatePath = Path.of(args[0]);
    String nextModelId = args[3];
    if (nextModelId.isBlank())
      throw new IllegalArgumentException(
        "InstructionTestRunner init-sprt: next_model_id must not be blank");
    String sessionId = args[4];
    if (sessionId.isBlank())
      throw new IllegalArgumentException(
        "InstructionTestRunner init-sprt: session_id must not be blank");
    String rerunJson = args[1];
    String priorPath = args[2];
    boolean usePriorBoost = false;
    String effort = "";
    for (int i = 5; i < args.length; ++i)
    {
      if (args[i].equals("--prior-boost"))
        usePriorBoost = true;
      else if (args[i].equals("--effort") && i + 1 < args.length)
      {
        effort = args[i + 1];
        ++i;
      }
    }

    boolean hasPrior = !priorPath.equals("none");
    if (hasPrior && Files.notExists(Path.of(priorPath)))
      throw new IllegalArgumentException(
        "InstructionTestRunner init-sprt: prior instruction-test file not found: " + priorPath);

    JsonMapper mapper = scope.getJsonMapper();

    // Parse rerun IDs
    JsonNode rerunNode = mapper.readTree(rerunJson);
    Set<String> rerunIds = new HashSet<>();
    if (rerunNode.isArray())
    {
      for (JsonNode element : rerunNode)
      {
        if (element.isString())
          rerunIds.add(element.asString());
      }
    }

    // Read prior instruction-test (if any), building a lookup map in a single pass over test_cases
    // so priorIds can be derived from the map's key set without a second traversal.
    // When the prior model_id differs from the current model, invalidate all cached results.
    Map<String, JsonNode> priorByTestCaseId = new HashMap<>();
    if (hasPrior)
    {
      JsonNode priorRoot = mapper.readTree(Path.of(priorPath).toFile());
      String priorModelId = priorRoot.path("model_id").asString("");
      // Only carry forward prior results when model_id matches the next model
      boolean modelMatches = !priorModelId.isBlank() && priorModelId.equals(nextModelId);
      if (!modelMatches && !priorModelId.isBlank())
      {
        log.warn("Model changed from {} to {}, invalidating cached SPRT results",
          priorModelId, nextModelId);
      }
      else if (priorModelId.isBlank())
        log.warn("Prior instruction-test has no model_id, invalidating cached SPRT results");

      // When cached results are invalidated, delete old test outputs to prevent schema conflicts
      if (!modelMatches)
      {
        Path worktreePath = sprtStatePath.getParent().getParent();
        Path testRunsDir = worktreePath.resolve(".cat/work/test-runs").resolve(sessionId);
        if (Files.exists(testRunsDir))
        {
          log.debug("Deleting stale test outputs at: {}", testRunsDir);
          try (Stream<Path> walk = Files.walk(testRunsDir))
          {
            walk.sorted(Comparator.reverseOrder()).forEach(path ->
            {
              try
              {
                Files.delete(path);
              }
              catch (IOException e)
              {
                log.warn("Failed to delete: {}", path, e);
              }
            });
          }
        }
      }

      if (modelMatches)
      {
        JsonNode priorTestCases = priorRoot.path("test_cases");
        if (priorTestCases.isArray())
        {
          for (JsonNode tc : priorTestCases)
          {
            String tcId = tc.path("test_case_id").asString("");
            if (!tcId.isBlank())
              priorByTestCaseId.put(tcId, tc);
          }
        }
      }
    }

    // Build union of prior IDs and rerun IDs (preserving insertion order, O(1) deduplication).
    // Prior IDs come first so carry-forward cases precede fresh re-runs in iteration order.
    SequencedSet<String> allIds = new LinkedHashSet<>(priorByTestCaseId.keySet());
    allIds.addAll(rerunIds);

    ObjectNode sprtState = mapper.createObjectNode();

    for (String testCaseId : allIds)
    {
      ObjectNode entry = mapper.createObjectNode();
      if (rerunIds.contains(testCaseId))
      {
        // Fresh SPRT state for re-run cases
        double initialLogRatio = 0.0;
        if (usePriorBoost)
        {
          JsonNode priorTc = priorByTestCaseId.get(testCaseId);
          if (priorTc != null && priorTc.path("decision").asString("").equals("ACCEPT"))
            initialLogRatio = PRIOR_BOOST;
        }
        entry.put("log_ratio", initialLogRatio);
        entry.put("passes", 0);
        entry.put("fails", 0);
        entry.put("runs", 0);
        entry.put("decision", "INCONCLUSIVE");
        entry.put("carried_forward", false);
        entry.put("smoke_runs_done", 0);
      }
      else
      {
        // Carry-forward state from prior instruction-test
        double logRatio = 0.0;
        int passes = 0;
        int fails = 0;
        int runs = 0;
        String decision = "INCONCLUSIVE";
        int smokeRunsDone = 0;
        JsonNode priorTc = priorByTestCaseId.get(testCaseId);
        if (priorTc != null)
        {
          JsonNode logRatioNode = priorTc.path("log_ratio");
          if (logRatioNode.isNumber())
            logRatio = logRatioNode.asDouble();
          JsonNode passesNode = priorTc.path("passes");
          if (passesNode.isNumber())
            passes = passesNode.asInt();
          JsonNode failsNode = priorTc.path("fails");
          if (failsNode.isNumber())
            fails = failsNode.asInt();
          JsonNode runsNode = priorTc.path("runs");
          if (runsNode.isNumber())
            runs = runsNode.asInt();
          String priorDecision = priorTc.path("decision").asString("");
          if (!priorDecision.isBlank())
            decision = priorDecision;
          JsonNode smokeNode = priorTc.path("smoke_runs_done");
          if (smokeNode.isNumber())
            smokeRunsDone = smokeNode.asInt();
        }
        entry.put("log_ratio", logRatio);
        entry.put("passes", passes);
        entry.put("fails", fails);
        entry.put("runs", runs);
        entry.put("decision", decision);
        entry.put("carried_forward", true);
        entry.put("smoke_runs_done", smokeRunsDone);
      }
      sprtState.set(testCaseId, entry);
    }

    ObjectNode stateDoc = mapper.createObjectNode();
    stateDoc.put("model_id", nextModelId);
    stateDoc.put("effort", effort);
    stateDoc.set("sprt_state", sprtState);
    stateDoc.set("failed_test_ids", mapper.createArrayNode());
    Files.createDirectories(sprtStatePath.getParent());
    Files.writeString(sprtStatePath, compactJson(stateDoc), UTF_8);

    ObjectNode result = mapper.createObjectNode();
    result.put("ok", true);
    return compactJson(result);
  }

  /**
   * Implements the {@code update-sprt} command.
   * <p>
   * Applies one PASS or FAIL observation to the SPRT log-ratio for a single test case and
   * re-evaluates boundary conditions.
   *
   * @param args {@code [sprt_state_path, tc_id, passed]}
   * @throws IllegalArgumentException if arguments are invalid or the state file is not found
   * @throws IOException              if the state file cannot be read or written
   */
  void updateSprt(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
      throw new IllegalArgumentException(
        "InstructionTestRunner update-sprt: expected 3 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner update-sprt <sprt_state_path> <tc_id> <passed>");

    Path statePath = Path.of(args[0]);
    String testCaseId = args[1];
    String passedStr = args[2];

    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "InstructionTestRunner update-sprt: state file not found: " + statePath);
    if (!passedStr.equals("true") && !passedStr.equals("false"))
      throw new IllegalArgumentException(
        "InstructionTestRunner update-sprt: <passed> must be 'true' or 'false', got: " + passedStr);

    boolean passed = passedStr.equals("true");
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode stateRoot = mapper.readTree(statePath.toFile());
    JsonNode sprtStateNode = stateRoot.path("sprt_state");

    // Read current values
    JsonNode tcNode = sprtStateNode.path(testCaseId);
    double currentLogRatio = tcNode.path("log_ratio").asDouble(0.0);
    int currentPasses = tcNode.path("passes").asInt(0);
    int currentFails = tcNode.path("fails").asInt(0);
    int currentRuns = tcNode.path("runs").asInt(0);
    int currentSmoke = tcNode.path("smoke_runs_done").asInt(0);
    boolean carriedForward = tcNode.path("carried_forward").asBoolean(false);

    // Apply observation
    double increment;
    if (passed)
      increment = SPRT_LOG_PASS;
    else
      increment = SPRT_LOG_FAIL;
    double newLogRatio = currentLogRatio + increment;
    int newRuns = currentRuns + 1;
    int newSmoke;
    if (currentSmoke < SMOKE_RUNS)
      newSmoke = currentSmoke + 1;
    else
      newSmoke = currentSmoke;
    int newPasses;
    int newFails;
    if (passed)
    {
      newPasses = currentPasses + 1;
      newFails = currentFails;
    }
    else
    {
      newPasses = currentPasses;
      newFails = currentFails + 1;
    }

    String newDecision;
    if (newLogRatio >= SPRT_ACCEPT)
      newDecision = "ACCEPT";
    else if (newLogRatio <= SPRT_REJECT)
      newDecision = "REJECT";
    else
      newDecision = "INCONCLUSIVE";

    // Build updated state
    ObjectNode newStateRoot = mapper.createObjectNode();
    ObjectNode newSprtState = mapper.createObjectNode();

    // Copy all existing entries, replacing the updated one
    if (sprtStateNode.isObject())
    {
      for (Map.Entry<String, JsonNode> entry : sprtStateNode.properties())
      {
        if (entry.getKey().equals(testCaseId))
        {
          ObjectNode updatedEntry = mapper.createObjectNode();
          updatedEntry.put("log_ratio", newLogRatio);
          updatedEntry.put("passes", newPasses);
          updatedEntry.put("fails", newFails);
          updatedEntry.put("runs", newRuns);
          updatedEntry.put("decision", newDecision);
          updatedEntry.put("carried_forward", carriedForward);
          updatedEntry.put("smoke_runs_done", newSmoke);
          newSprtState.set(entry.getKey(), updatedEntry);
        }
        else
        {
          newSprtState.set(entry.getKey(), entry.getValue());
        }
      }
    }

    // Copy all top-level fields from the original state (e.g., model_id) before overwriting
    // sprt_state. Without this, a round-trip through update-sprt would strip fields written by
    // init-sprt, breaking downstream commands that rely on model_id.
    if (stateRoot.isObject())
    {
      for (Map.Entry<String, JsonNode> entry : stateRoot.properties())
      {
        if (!entry.getKey().equals("sprt_state"))
          newStateRoot.set(entry.getKey(), entry.getValue());
      }
    }
    newStateRoot.set("sprt_state", newSprtState);
    // Atomic temp-file swap: write to .tmp then rename to avoid truncating the input file
    Path tempPath = statePath.resolveSibling(statePath.getFileName() + ".tmp");
    Files.writeString(tempPath, compactJson(newStateRoot), UTF_8);
    Files.move(tempPath, statePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Implements the {@code check-boundary} command.
   * <p>
   * Returns the current SPRT boundary decision for a single test case.
   *
   * @param args {@code [sprt_state_path, tc_id]}
   * @return a JSON object with decision, log_ratio, runs, smoke_runs_done, and carried_forward
   * @throws IllegalArgumentException if arguments are invalid or the state file is not found
   * @throws IOException              if the state file cannot be read
   */
  String checkBoundary(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner check-boundary: expected 2 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner check-boundary <sprt_state_path> <tc_id>");

    Path statePath = Path.of(args[0]);
    String testCaseId = args[1];

    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "InstructionTestRunner check-boundary: state file not found: " + statePath);

    JsonMapper mapper = scope.getJsonMapper();
    JsonNode stateRoot = mapper.readTree(statePath.toFile());
    JsonNode tcNode = stateRoot.path("sprt_state").path(testCaseId);

    double logRatio = tcNode.path("log_ratio").asDouble(0.0);
    int runs = tcNode.path("runs").asInt(0);
    int smoke = tcNode.path("smoke_runs_done").asInt(0);
    boolean carriedForward = tcNode.path("carried_forward").asBoolean(false);
    String decision = tcNode.path("decision").asString("INCONCLUSIVE");

    ObjectNode result = mapper.createObjectNode();
    result.put("test_case_id", testCaseId);
    result.put("decision", decision);
    result.put("log_ratio", logRatio);
    result.put("runs", runs);
    result.put("smoke_runs_done", smoke);
    result.put("carried_forward", carriedForward);
    return compactJson(result);
  }

  /**
   * Implements the {@code smoke-status} command.
   * <p>
   * Determines whether a test case is in the smoke-test phase or should escalate to full SPRT.
   *
   * @param args {@code [sprt_state_path, tc_id]}
   * @return a JSON object describing smoke-test phase status
   * @throws IllegalArgumentException if arguments are invalid or the state file is not found
   * @throws IOException              if the state file cannot be read
   */
  String smokeStatus(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner smoke-status: expected 2 arguments, got " + args.length + ".\n" +
        "Usage: skill-test-runner smoke-status <sprt_state_path> <tc_id>");

    Path statePath = Path.of(args[0]);
    String testCaseId = args[1];

    if (Files.notExists(statePath))
      throw new IllegalArgumentException(
        "InstructionTestRunner smoke-status: state file not found: " + statePath);

    JsonMapper mapper = scope.getJsonMapper();
    JsonNode stateRoot = mapper.readTree(statePath.toFile());
    JsonNode tcNode = stateRoot.path("sprt_state").path(testCaseId);

    int smoke = tcNode.path("smoke_runs_done").asInt(0);
    boolean carriedForward = tcNode.path("carried_forward").asBoolean(false);
    String decision = tcNode.path("decision").asString("INCONCLUSIVE");

    int remaining = Math.max(0, SMOKE_RUNS - smoke);
    boolean inSmoke = !carriedForward && smoke < SMOKE_RUNS;
    boolean escalate = !carriedForward && smoke >= SMOKE_RUNS && decision.equals("INCONCLUSIVE");

    ObjectNode result = mapper.createObjectNode();
    result.put("test_case_id", testCaseId);
    result.put("in_smoke_phase", inSmoke);
    result.put("smoke_runs_done", smoke);
    result.put("smoke_runs_remaining", remaining);
    result.put("escalate_to_full_sprt", escalate);
    return compactJson(result);
  }

  /**
   * Converts an object to compact JSON (single line without indentation).
   *
   * @param value the object to serialize
   * @return compact JSON representation
   */
  private String compactJson(Object value)
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
}
