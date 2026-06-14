/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.tool.CliTool;
import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates multi-batch SPRT execution.
 */
final class SprtRunWorkflow
{
  private final SprtRunner runner;
  private final CliTool scope;
  private final Logger log;
  private final SprtPipelineExecutor pipelineExecutor;
  private final SprtResultsManager resultsManager;
  private final SprtRunStatusStore sprtRunStatusStore;
  private final int earlyFailThreshold;
  private final int earlyFailWindow;

  /**
   * Creates a new workflow runner.
   *
   * @param runner the runner
   *
   * @param scope the scope
   *
   * @param log the log
   *
   * @param sprtGrader the sprtGrader
   *
   * @param resultsManager the resultsManager
   *
   * @param sprtRunStatusStore persists whole-run status snapshots and event deltas
   *
   * @param earlyFailThreshold the earlyFailThreshold
   *
   * @param earlyFailWindow the earlyFailWindow
   */
  SprtRunWorkflow(SprtRunner runner, CliTool scope, Logger log, SprtGrader sprtGrader,
    SprtResultsManager resultsManager, SprtRunStatusStore sprtRunStatusStore,
    int earlyFailThreshold, int earlyFailWindow)
  {
    requireThat(runner, "runner").isNotNull();
    requireThat(scope, "scope").isNotNull();
    requireThat(log, "log").isNotNull();
    requireThat(sprtGrader, "sprtGrader").isNotNull();
    requireThat(resultsManager, "resultsManager").isNotNull();
    requireThat(sprtRunStatusStore, "sprtRunStatusStore").isNotNull();
    this.runner = runner;
    this.scope = scope;
    this.log = log;
    this.pipelineExecutor = new SprtPipelineExecutor(runner, log, sprtGrader);
    this.resultsManager = resultsManager;
    this.sprtRunStatusStore = sprtRunStatusStore;
    this.earlyFailThreshold = earlyFailThreshold;
    this.earlyFailWindow = earlyFailWindow;
  }

  /**
   * Executes the {@code run-sprt} workflow.
   *
   * @param args the args
   *
   * @param out the out
   */
  void runSprt(String[] args, PrintStream out) throws IOException, InterruptedException
  {
    SprtRunner.RunSprtArguments parsedArgs = SprtRunner.parseRunSprtArgs(args,
      scope.getSessionId());
    String worktreePath = parsedArgs.worktreePath();
    String testDir = parsedArgs.testDir();
    String testModel = parsedArgs.testModel();
    String testEffort = parsedArgs.testEffort();
    String sessionId = parsedArgs.sessionId();
    SprtProgressListener progressListener = sprtRunStatusStore.createListener(
      Path.of(worktreePath), sessionId, testDir, testModel, testEffort);
    runner.validateConfiguration(testModel, testEffort);

    JsonMapper mapper = scope.getJsonMapper();
    try
    {
      out.println("Step 1: Running prepare-run...");
      String prepareOutput = runner.prepareRun(new String[]{worktreePath, testDir});
      Map<String, String> prepareVars = parseKeyValue(prepareOutput);
      String testDirAbs = prepareVars.get("test_dir_abs");
      String issueName = prepareVars.get("issue_name");
      Path testDirRel = Path.of(prepareVars.get("test_dir_rel"));
      Path sprtStatePath = Path.of(prepareVars.get("sprt_state_path"));
      progressListener.onRunStarted(0, sprtStatePath);
      out.println("  TEST_DIR_ABS: " + testDirAbs);
      out.println("  ISSUE_NAME: " + issueName);
      out.println("  SPRT_STATE_PATH: " + sprtStatePath);
      out.println();

      out.println("Step 2: Cleaning up previous run...");
      progressListener.onPhaseChanged(SprtRunStatus.PHASE_CLEANUP_PREVIOUS,
        "Cleaning up previous runner worktrees");
      runner.removeIsolationBranch(new String[]{worktreePath, issueName + "-isolation"});
      runner.removeRunnerWorktrees(new String[]{worktreePath, issueName});
      out.println();

      out.println("Step 3: Creating isolation branch...");
      progressListener.onPhaseChanged(SprtRunStatus.PHASE_ISOLATION, "Creating isolation branch");
      String isolationResult = runner.createIsolationBranch(new String[]{worktreePath, testDirAbs, issueName});
      JsonNode isolationNode = mapper.readTree(isolationResult);
      String isolationBranch = isolationNode.path("isolation_branch").asString();
      ArrayNode testCaseIdsArray = (ArrayNode) isolationNode.path("tc_ids_json");
      out.println("  Isolation branch: " + isolationBranch);
      out.println("  Test cases: " + testCaseIdsArray.size());
      out.println();

      Set<String> failedTestIds = loadFailedTestIds(testModel, testEffort, testDirAbs, sprtStatePath, mapper);

      out.println("Step 4: Initializing SPRT state...");
      progressListener.onPhaseChanged(SprtRunStatus.PHASE_INIT_STATE, "Initializing SPRT state");
      runner.initSprt(new String[]{sprtStatePath.toString(), mapper.writeValueAsString(testCaseIdsArray),
        "none", testModel, sessionId, "--effort", testEffort});
      progressListener.onRunStarted(testCaseIdsArray.size(), sprtStatePath);
      out.println("  SPRT state initialized at: " + sprtStatePath);
      out.println();

      List<String> testCaseIds = new ArrayList<>();
      for (JsonNode testCaseIdNode : testCaseIdsArray)
        testCaseIds.add(testCaseIdNode.asString());
      if (!failedTestIds.isEmpty())
      {
        testCaseIds.sort((left, right) ->
        {
          boolean leftFailed = failedTestIds.contains(left);
          boolean rightFailed = failedTestIds.contains(right);
          if (leftFailed && !rightFailed)
            return -1;
          if (!leftFailed && rightFailed)
            return 1;
          return 0;
        });
        out.println("=== Test Prioritization ===");
        out.println("Prioritizing " + failedTestIds.size() + " previously-failed test(s)");
        out.println();
      }

      runSprtLoop(worktreePath, testDirAbs, testDirRel, issueName, sessionId, testModel, testEffort,
        isolationResult, isolationBranch, sprtStatePath, testCaseIds, out, mapper, progressListener);
    }
    catch (IOException | InterruptedException | RuntimeException e)
    {
      String errorMessage = e.getMessage();
      if (errorMessage == null)
        errorMessage = e.getClass().getSimpleName();
      progressListener.onFailed(errorMessage);
      throw e;
    }
  }

  /**
   * Runs one batch across all currently undecided test cases.
   *
   * @param worktreePathStr the worktreePathStr
   *
   * @param sprtStateJson the sprtStateJson
   *
   * @param issueName the issueName
   *
   * @param testDirRel the testDirRel
   *
   * @param sessionId the sessionId
   *
   * @param modelId the modelId
   *
   * @param testEffort the testEffort
   *
   * @param batchNum the batchNum
   *
   * @param isolationResultJson the isolationResultJson
   *
   * @param progressListener records whole-run status snapshots and event deltas
   *
   * @return the result
   */
  String runSprtBatch(String worktreePathStr, String sprtStateJson, String issueName,
    String testDirRel, String sessionId, String modelId, String testEffort, int batchNum,
    String isolationResultJson, SprtProgressListener progressListener)
    throws IOException, InterruptedException
  {
    requireThat(worktreePathStr, "worktreePathStr").isNotBlank();
    requireThat(sprtStateJson, "sprtStateJson").isNotBlank();
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(testDirRel, "testDirRel").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(testEffort, "testEffort").isNotBlank();
    requireThat(isolationResultJson, "isolationResultJson").isNotBlank();

    JsonMapper mapper = scope.getJsonMapper();
    Object sprtLock = new Object();
    String worktreesJson = runner.createRunnerWorktrees(new String[]{worktreePathStr, sprtStateJson,
      issueName, sessionId});
    JsonNode worktreesRoot = mapper.readTree(worktreesJson);
    String outputDir = worktreesRoot.path("output_dir").asString();
    ArrayNode worktreesArray = (ArrayNode) worktreesRoot.path("worktrees");
    JsonNode sprtState = mapper.readTree(Files.readString(Path.of(sprtStateJson), UTF_8));
    JsonNode sprtStateData = sprtState.path("sprt_state");
    List<JsonNode> sortedWorktrees = new ArrayList<>();
    for (JsonNode node : worktreesArray)
      sortedWorktrees.add(node);
    sortedWorktrees.sort((left, right) ->
    {
      String leftTestCaseId = left.path("tc_id").asString();
      String rightTestCaseId = right.path("tc_id").asString();
      int leftFails = sprtStateData.path(leftTestCaseId).path("fails").asInt(0);
      int rightFails = sprtStateData.path(rightTestCaseId).path("fails").asInt(0);
      return Integer.compare(rightFails, leftFails);
    });

    int decidedCount = 0;
    ArrayNode inconclusiveTestCases = mapper.createArrayNode();
    int maxParallelism = Runtime.getRuntime().availableProcessors();
    int currentParallelism = Math.min(2, maxParallelism);
    int processedCount = 0;
    int priorFailures = cumulativePriorFailures(batchNum, sortedWorktrees, sprtStateData);
    AtomicInteger cumulativeFailures = new AtomicInteger(priorFailures);

    while (processedCount < sortedWorktrees.size())
    {
      if (batchNum <= earlyFailWindow && cumulativeFailures.get() >= earlyFailThreshold)
        break;
      SprtPipelineExecutor.BatchOutcome outcome = pipelineExecutor.runParallelPipelines(
        worktreePathStr, sprtStateJson, issueName, testDirRel, modelId, testEffort,
        isolationResultJson, outputDir, processedCount, sortedWorktrees, currentParallelism,
        mapper, sprtLock, cumulativeFailures, inconclusiveTestCases, progressListener);
      if (!outcome.errors().isEmpty())
        throw aggregatePipelineErrors(outcome.errors());

      decidedCount += outcome.decidedCount();
      processedCount += outcome.batchSize();
      if (outcome.anyFailed())
      {
        currentParallelism = Math.max(1, currentParallelism / 2);
        log.info("Failures detected, reducing parallelism to {}", currentParallelism);
      }
      else if (outcome.batchSize() == currentParallelism)
      {
        currentParallelism = Math.min(maxParallelism, currentParallelism * 2);
        log.info("Batch succeeded, increasing parallelism to {}", currentParallelism);
      }
    }

    boolean earlyAbort = batchNum <= earlyFailWindow &&
      cumulativeFailures.get() >= earlyFailThreshold;
    if (earlyAbort)
    {
      log.info("Early failure detection: {} failures reached threshold, batch interrupted",
        cumulativeFailures.get());
    }
    runner.removeRunnerWorktrees(new String[]{worktreePathStr, issueName});
    ObjectNode result = mapper.createObjectNode();
    result.put("decided_count", decidedCount);
    result.put("early_abort", earlyAbort);
    result.put("cumulative_failures", cumulativeFailures.get());
    result.set("inconclusive_tcs", inconclusiveTestCases);
    return resultsManager.compactJson(result);
  }

  /**
   * Executes the full SPRT loop once setup is complete.
   *
   * @param worktreePath the worktreePath
   *
   * @param testDirAbs the testDirAbs
   *
   * @param testDirRel the testDirRel
   *
   * @param issueName the issueName
   *
   * @param sessionId the sessionId
   *
   * @param testModel the testModel
   *
   * @param testEffort the testEffort
   *
   * @param isolationResult the isolationResult
   *
   * @param isolationBranch the isolationBranch
   *
   * @param sprtStatePath the sprtStatePath
   *
   * @param testCaseIds the testCaseIds
   *
   * @param out the out
   *
   * @param mapper the mapper
   *
   * @param progressListener records whole-run status snapshots and event deltas
   */
  private void runSprtLoop(String worktreePath, String testDirAbs, Path testDirRel, String issueName,
    String sessionId, String testModel, String testEffort, String isolationResult,
    String isolationBranch, Path sprtStatePath, List<String> testCaseIds, PrintStream out,
    JsonMapper mapper, SprtProgressListener progressListener) throws IOException, InterruptedException
  {
    out.println("=== Starting SPRT Loop ===");
    out.println("Test cases: " + testCaseIds.size());
    out.println();

    int batchNum = 0;
    int trialsPerBatch = 1;
    List<String> undecided = new ArrayList<>(testCaseIds);
    Map<String, Integer> runCounts = initializeRunCounts(testCaseIds);
    Map<String, String> decisions = new HashMap<>();
    long loopStartMilliseconds = System.currentTimeMillis();
    List<Long> batchDurationsMilliseconds = new ArrayList<>();

    while (!undecided.isEmpty())
    {
      ++batchNum;
      progressListener.onBatchStarted(batchNum, trialsPerBatch, undecided.size(), Math.min(2,
        Runtime.getRuntime().availableProcessors()));
      out.printf("=== Batch %d (%d trial(s) per TC): %d test case(s) remaining ===%n",
        batchNum, trialsPerBatch, undecided.size());
      String preStateJson = Files.readString(sprtStatePath);
      int failsBefore = countFails(mapper.readTree(preStateJson).path("sprt_state"), undecided);

      BatchProgress progress = runBatchTrials(worktreePath, testDirRel, issueName, sessionId,
        testModel, testEffort, isolationResult, sprtStatePath, undecided, runCounts, decisions,
        batchNum, trialsPerBatch, batchDurationsMilliseconds, out, mapper, progressListener);
      undecided = progress.undecided();
      out.println();

      JsonNode sprtNode = printBatchSummary(batchNum, sprtStatePath, testCaseIds, decisions, runCounts,
        out, mapper);
      progressListener.onBatchSummary(batchNum, undecided.size(), decisions.size(),
        progress.batchResultNode().path("cumulative_failures").asInt(0));
      if (!undecided.isEmpty())
        printEta(sprtNode, undecided, loopStartMilliseconds, batchDurationsMilliseconds, out);
      trialsPerBatch = nextTrialsPerBatch(sprtStatePath, testCaseIds, mapper, failsBefore,
        progress.batchEarlyAbort(), progress.anyReject(), trialsPerBatch);

      if (progress.batchEarlyAbort())
      {
        progressListener.onEarlyAbort(batchNum, progress.batchResultNode().path("cumulative_failures").asInt(0));
        handleEarlyAbort(progress.batchResultNode(), sprtNode, batchNum, sprtStatePath,
          testCaseIds, undecided, decisions, runCounts, out, mapper);
        break;
      }
      if (progress.anyReject())
      {
        progressListener.onRejectAbort(undecided.size());
        handleRejectAbort(sprtStatePath, undecided, decisions, runCounts, out, mapper);
        break;
      }
    }

    finishRun(worktreePath, testDirAbs, issueName, isolationBranch, isolationResult, sprtStatePath,
      testCaseIds, decisions, runCounts, out, progressListener);
  }

  /**
   * Initializes run counters for each test case at zero.
   *
   * @param testCaseIds the ordered test-case identifiers
   * @return a mutable map from test-case id to run count
   */
  private Map<String, Integer> initializeRunCounts(List<String> testCaseIds)
  {
    Map<String, Integer> runCounts = new HashMap<>();
    for (String testCaseId : testCaseIds)
      runCounts.put(testCaseId, 0);
    return runCounts;
  }

  /**
   * Loads failed test ids from prior state/results when model/effort match.
   *
   * @param testModel the testModel
   *
   * @param testEffort the testEffort
   *
   * @param testDirAbs the testDirAbs
   *
   * @param sprtStatePath the sprtStatePath
   *
   * @param mapper the mapper
   *
   * @return the result
   */
  private Set<String> loadFailedTestIds(String testModel, String testEffort, String testDirAbs,
    Path sprtStatePath, JsonMapper mapper) throws IOException
  {
    Set<String> failedTestIds = new HashSet<>();
    JsonNode failedTestIdsSource = null;
    if (Files.exists(sprtStatePath))
    {
      JsonNode priorStateRoot = mapper.readTree(sprtStatePath.toFile());
      String priorModelId = priorStateRoot.path("model_id").asString("");
      String priorEffort = priorStateRoot.path("effort").asString("");
      if (testModel.equals(priorModelId) && testEffort.equals(priorEffort))
        failedTestIdsSource = priorStateRoot.path("failed_test_ids");
    }
    if (failedTestIdsSource == null || !failedTestIdsSource.isArray() || failedTestIdsSource.isEmpty())
    {
      Path testResultsPath = Path.of(testDirAbs).resolve("test-results.json");
      if (Files.exists(testResultsPath))
        failedTestIdsSource = resultsManager.failedTestIds(testResultsPath, testModel, testEffort);
    }
    if (failedTestIdsSource != null && failedTestIdsSource.isArray())
    {
      for (JsonNode idNode : failedTestIdsSource)
      {
        if (idNode.isString())
          failedTestIds.add(idNode.asString());
      }
    }
    return failedTestIds;
  }

  /**
   * Runs the per-trial subloop for a batch.
   *
   * @param worktreePath the worktreePath
   *
   * @param testDirRel the testDirRel
   *
   * @param issueName the issueName
   *
   * @param sessionId the sessionId
   *
   * @param testModel the testModel
   *
   * @param testEffort the testEffort
   *
   * @param isolationResult the isolationResult
   *
   * @param sprtStatePath the sprtStatePath
   *
   * @param undecided the undecided
   *
   * @param runCounts the runCounts
   *
   * @param decisions the decisions
   *
   * @param batchNum the batchNum
   *
   * @param trialsPerBatch the trialsPerBatch
   *
   * @param batchDurationsMilliseconds the batchDurationsMilliseconds
   *
   * @param out the out
   *
   * @param mapper the mapper
   *
   * @param progressListener records whole-run status snapshots and event deltas
   *
   * @return the result
   */
  private BatchProgress runBatchTrials(String worktreePath, Path testDirRel, String issueName,
    String sessionId, String testModel, String testEffort, String isolationResult,
    Path sprtStatePath, List<String> undecided, Map<String, Integer> runCounts,
    Map<String, String> decisions, int batchNum, int trialsPerBatch,
    List<Long> batchDurationsMilliseconds, PrintStream out, JsonMapper mapper,
    SprtProgressListener progressListener)
    throws IOException, InterruptedException
  {
    boolean batchEarlyAbort = false;
    JsonNode batchResultNode = null;
    boolean anyReject = false;
    List<String> currentUndecided = undecided;
    for (int trial = 0; trial < trialsPerBatch; ++trial)
    {
      batchResultNode = executeSingleBatchTrial(worktreePath, testDirRel, issueName, sessionId,
        testModel, testEffort, batchNum, isolationResult, sprtStatePath,
        batchDurationsMilliseconds, mapper, progressListener);
      batchEarlyAbort = batchResultNode.path("early_abort").asBoolean(false);
      TrialDecisionResult decisionResult = updateTrialDecisions(currentUndecided, sprtStatePath,
        runCounts, decisions, out, mapper);
      currentUndecided = decisionResult.stillUndecided();
      anyReject = anyReject || decisionResult.anyReject();
      if (batchEarlyAbort || anyReject || currentUndecided.isEmpty())
        break;
    }
    return new BatchProgress(currentUndecided, batchEarlyAbort, batchResultNode, anyReject);
  }

  /**
   * Executes one nested SPRT batch trial and records its duration.
   *
   * @param worktreePath the worktree root
   * @param testDirRel the relative test directory
   * @param issueName the issue name
   * @param sessionId the current CAT session identifier
   * @param testModel the test model
   * @param testEffort the effort level under test
   * @param batchNum the current batch number
   * @param isolationResult the isolation mapping payload
   * @param sprtStatePath the SPRT state file
   * @param batchDurationsMilliseconds the collected batch durations
   * @param mapper the JSON mapper
   * @param progressListener records whole-run status snapshots and event deltas
   * @return the parsed batch result payload
   * @throws IOException if batch execution or parsing fails
   * @throws InterruptedException if interrupted while running the nested batch
   */
  private JsonNode executeSingleBatchTrial(String worktreePath, Path testDirRel, String issueName,
    String sessionId, String testModel, String testEffort, int batchNum, String isolationResult,
    Path sprtStatePath, List<Long> batchDurationsMilliseconds, JsonMapper mapper,
    SprtProgressListener progressListener)
    throws IOException, InterruptedException
  {
    long batchStartMilliseconds = System.currentTimeMillis();
    String batchResult = runSprtBatch(worktreePath, sprtStatePath.toString(), issueName,
      testDirRel.toString(), sessionId, testModel, testEffort, batchNum, isolationResult,
      progressListener);
    batchDurationsMilliseconds.add(System.currentTimeMillis() - batchStartMilliseconds);
    return mapper.readTree(batchResult);
  }

  /**
   * Recomputes boundary decisions for the current undecided set after one trial.
   *
   * @param currentUndecided the test cases that were undecided before this trial
   * @param sprtStatePath the SPRT state file
   * @param runCounts the mutable run-count map
   * @param decisions the mutable decision map
   * @param out the user-facing output stream
   * @param mapper the JSON mapper
   * @return the updated undecided set and reject flag
   * @throws IOException if boundary lookup or parsing fails
   */
  private TrialDecisionResult updateTrialDecisions(List<String> currentUndecided, Path sprtStatePath,
    Map<String, Integer> runCounts, Map<String, String> decisions, PrintStream out,
    JsonMapper mapper) throws IOException
  {
    boolean anyReject = false;
    List<String> stillUndecided = new ArrayList<>();
    for (String testCaseId : currentUndecided)
    {
      JsonNode boundaryNode = mapper.readTree(
        runner.checkBoundary(new String[]{sprtStatePath.toString(), testCaseId}));
      String decision = boundaryNode.path("decision").asString();
      int runs = runCounts.get(testCaseId) + 1;
      runCounts.put(testCaseId, runs);
      if (decision.equals("ACCEPT") || decision.equals("REJECT"))
      {
        decisions.put(testCaseId, decision);
        out.println("  ✓ " + testCaseId + ": " + decision + " (" + runs + " runs)");
        anyReject = anyReject || decision.equals("REJECT");
      }
      else if (runs >= 50)
      {
        decisions.put(testCaseId, "REJECT");
        out.println("  ✗ " + testCaseId + ": REJECT (truncated at 50 runs)");
        anyReject = true;
      }
      else
      {
        stillUndecided.add(testCaseId);
      }
    }
    return new TrialDecisionResult(stillUndecided, anyReject);
  }

  /**
   * Prints one batch summary and returns the parsed SPRT node.
   *
   * @param sprtStatePath the sprtStatePath
   *
   * @param testCaseIds the testCaseIds
   *
   * @param decisions the decisions
   *
   * @param runCounts the runCounts
   *
   * @param out the out
   *
   * @param mapper the mapper
   *
   * @return the result
   *
   * @param batchNum the batchNum
   */
  private static JsonNode printBatchSummary(int batchNum, Path sprtStatePath, List<String> testCaseIds,
    Map<String, String> decisions, Map<String, Integer> runCounts, PrintStream out,
    JsonMapper mapper) throws IOException
  {
    out.println("=== Batch " + batchNum + " Summary ===");
    out.println();
    JsonNode sprtNode = mapper.readTree(Files.readString(sprtStatePath)).path("sprt_state");
    out.printf("%-10s %-7s %-7s %-12s %-6s %-20s%n",
      "TC", "Passes", "Fails", "Decision", "Runs", "Runs to Convergence");
    out.println("-".repeat(72));
    for (String testCaseId : testCaseIds)
    {
      JsonNode testCaseNode = sprtNode.path(testCaseId);
      int passes = testCaseNode.path("passes").asInt(0);
      int fails = testCaseNode.path("fails").asInt(0);
      double logRatio = testCaseNode.path("log_ratio").asDouble(0.0);
      String decision = decisions.getOrDefault(testCaseId, "INCONCLUSIVE");
      int runs = runCounts.get(testCaseId);
      String convergence = "-";
      if (decision.equals("INCONCLUSIVE"))
        convergence = "~" + (int) Math.ceil((2.944 - logRatio) / 0.1112) + " more";
      out.printf("%-10s %-7d %-7d %-12s %-6d %-20s%n",
        testCaseId, passes, fails, decision, runs, convergence);
    }
    out.println();
    return sprtNode;
  }

  /**
   * Prints ETA information for remaining undecided tests.
   *
   * @param sprtNode the sprtNode
   *
   * @param undecided the undecided
   *
   * @param loopStartMilliseconds the loopStartMilliseconds
   *
   * @param batchDurationsMilliseconds the batchDurationsMilliseconds
   *
   * @param out the out
   */
  private static void printEta(JsonNode sprtNode, List<String> undecided, long loopStartMilliseconds,
    List<Long> batchDurationsMilliseconds, PrintStream out)
  {
    long averageBatchMilliseconds = batchDurationsMilliseconds.stream().
      mapToLong(Long::longValue).sum() / batchDurationsMilliseconds.size();
    int maxRunsToAccept = 0;
    for (String testCaseId : undecided)
    {
      double logRatio = sprtNode.path(testCaseId).path("log_ratio").asDouble(0.0);
      int runsToAccept = (int) Math.ceil((2.944 - logRatio) / 0.1112);
      if (runsToAccept > maxRunsToAccept)
        maxRunsToAccept = runsToAccept;
    }
    long elapsedMilliseconds = System.currentTimeMillis() - loopStartMilliseconds;
    long etaMilliseconds = maxRunsToAccept * averageBatchMilliseconds;
    out.printf("Elapsed: %s | Avg batch: %s | ETA to ACCEPT: ~%s (%d batch(es) @ %s each)%n",
      formatDuration(elapsedMilliseconds), formatDuration(averageBatchMilliseconds),
      formatDuration(etaMilliseconds), maxRunsToAccept, formatDuration(averageBatchMilliseconds));
    out.println();
  }

  /**
   * Computes the next adaptive trials-per-batch value.
   *
   * @param sprtStatePath the sprtStatePath
   *
   * @param testCaseIds the testCaseIds
   *
   * @param mapper the mapper
   *
   * @param failsBefore the failsBefore
   *
   * @param batchEarlyAbort the batchEarlyAbort
   *
   * @param anyReject the anyReject
   *
   * @param currentTrialsPerBatch the currentTrialsPerBatch
   *
   * @return the result
   */
  private static int nextTrialsPerBatch(Path sprtStatePath, List<String> testCaseIds, JsonMapper mapper,
    int failsBefore, boolean batchEarlyAbort, boolean anyReject, int currentTrialsPerBatch)
    throws IOException
  {
    JsonNode postSprtNode = mapper.readTree(Files.readString(sprtStatePath)).path("sprt_state");
    int failsAfter = countFails(postSprtNode, testCaseIds);
    if (!batchEarlyAbort && !anyReject && failsAfter == failsBefore)
      return Math.min(currentTrialsPerBatch * 2, 4);
    return 1;
  }

  /**
   * Counts failures for the specified test cases.
   *
   * @param sprtNode the sprtNode
   *
   * @param testCaseIds the testCaseIds
   *
   * @return the result
   */
  private static int countFails(JsonNode sprtNode, List<String> testCaseIds)
  {
    int fails = 0;
    for (String testCaseId : testCaseIds)
      fails += sprtNode.path(testCaseId).path("fails").asInt(0);
    return fails;
  }

  /**
   * Handles an early-abort batch result.
   *
   * @param batchResultNode the batchResultNode
   *
   * @param sprtNode the sprtNode
   *
   * @param batchNum the batchNum
   *
   * @param sprtStatePath the sprtStatePath
   *
   * @param testCaseIds the testCaseIds
   *
   * @param undecided the undecided
   *
   * @param decisions the decisions
   *
   * @param runCounts the runCounts
   *
   * @param out the out
   *
   * @param mapper the mapper
   */
  private void handleEarlyAbort(JsonNode batchResultNode, JsonNode sprtNode, int batchNum,
    Path sprtStatePath, List<String> testCaseIds, List<String> undecided,
    Map<String, String> decisions, Map<String, Integer> runCounts, PrintStream out,
    JsonMapper mapper) throws IOException
  {
    int totalFailures = batchResultNode.path("cumulative_failures").asInt(0);
    int failedCount = 0;
    for (String testCaseId : testCaseIds)
    {
      if (sprtNode.path(testCaseId).path("fails").asInt(0) > 0)
        ++failedCount;
    }
    out.println("=== Early Failure Detection (Batch " + batchNum + ") ===");
    out.println("Detected " + totalFailures + " total failures across " +
      failedCount + " test case(s). Batch interrupted mid-execution.");
    out.println("Stopping early to provide fast feedback.");
    out.println();
    updateFailedTestIds(sprtStatePath, mapper);
    for (String testCaseId : undecided)
    {
      decisions.put(testCaseId, "INCONCLUSIVE");
      out.println("  " + testCaseId + ": INCONCLUSIVE (early stop after " +
        runCounts.get(testCaseId) + " runs)");
    }
    out.println();
  }

  /**
   * Handles abort after a reject decision.
   *
   * @param sprtStatePath the sprtStatePath
   *
   * @param undecided the undecided
   *
   * @param decisions the decisions
   *
   * @param runCounts the runCounts
   *
   * @param out the out
   *
   * @param mapper the mapper
   */
  private void handleRejectAbort(Path sprtStatePath, List<String> undecided,
    Map<String, String> decisions, Map<String, Integer> runCounts, PrintStream out,
    JsonMapper mapper) throws IOException
  {
    updateFailedTestIds(sprtStatePath, mapper);
    out.println("=== SPRT Aborted: At least one test case REJECT detected ===");
    out.println("Remaining test cases (" + undecided.size() + ") will be marked INCONCLUSIVE.");
    for (String testCaseId : undecided)
    {
      decisions.put(testCaseId, "INCONCLUSIVE");
      out.println("  " + testCaseId + ": INCONCLUSIVE (aborted after " +
        runCounts.get(testCaseId) + " runs)");
    }
    out.println();
  }

  /**
   * Performs final result writing, cleanup, and reporting.
   *
   * @param worktreePath the worktreePath
   *
   * @param testDirAbs the testDirAbs
   *
   * @param issueName the issueName
   *
   * @param isolationBranch the isolationBranch
   *
   * @param isolationResult the isolationResult
   *
   * @param sprtStatePath the sprtStatePath
   *
   * @param testCaseIds the testCaseIds
   *
   * @param decisions the decisions
   *
   * @param runCounts the runCounts
   *
   * @param out the out
   *
   * @param progressListener records whole-run status snapshots and event deltas
   */
  private void finishRun(String worktreePath, String testDirAbs, String issueName,
    String isolationBranch, String isolationResult, Path sprtStatePath, List<String> testCaseIds,
    Map<String, String> decisions, Map<String, Integer> runCounts, PrintStream out,
    SprtProgressListener progressListener)
    throws IOException
  {
    out.println("=== SPRT Loop Complete ===");
    out.println();
    TestResultsSummary summary = writeTestResults(worktreePath, testDirAbs, sprtStatePath, out,
      progressListener);
    String overallDecision = summary.overallDecision();
    String testSha = summary.testSha();

    cleanupRun(worktreePath, issueName, isolationBranch, out, progressListener);
    progressListener.onCompleted(overallDecision, testSha, Path.of(testDirAbs).resolve("test-results.json"));
    printFinalResults(isolationResult, testCaseIds, decisions, runCounts, out, overallDecision,
      testSha);
  }

  /**
   * Writes final SPRT result artifacts and returns the required summary values.
   *
   * @param worktreePath the worktree root
   * @param testDirAbs the absolute test directory
   * @param sprtStatePath the SPRT state file
   * @param out the user-facing output stream
   * @param progressListener records whole-run status snapshots and event deltas
   * @return the final summary values emitted by write-test-results
   * @throws IOException if writing test results fails
   */
  private TestResultsSummary writeTestResults(String worktreePath, String testDirAbs,
    Path sprtStatePath, PrintStream out, SprtProgressListener progressListener) throws IOException
  {
    out.println("Step 6: Writing test results...");
    progressListener.onWritingResults(Path.of(testDirAbs).resolve("test-results.json"));
    Map<String, String> writeVars = parseKeyValue(resultsManager.writeTestResults(
      new String[]{worktreePath, sprtStatePath.toString(), testDirAbs}));
    String writeStatus = writeVars.get("status");
    if (!"ok".equals(writeStatus))
    {
      String message = writeVars.getOrDefault("message",
        "write-test-results returned status='" + writeStatus + "'");
      throw new IOException("SprtRunner write-test-results failed: " + message);
    }
    String overallDecision = requiredOutput(writeVars, "overall_decision");
    String testSha = requiredOutput(writeVars, "test_sha");
    out.println("  Overall decision: " + overallDecision);
    out.println("  Test SHA: " + testSha);
    out.println();
    return new TestResultsSummary(overallDecision, testSha);
  }

  /**
   * Removes the temporary isolation branch and runner worktrees for this run.
   *
   * @param worktreePath the worktree root
   * @param issueName the issue name
   * @param isolationBranch the temporary isolation branch
   * @param out the user-facing output stream
   * @param progressListener records whole-run status snapshots and event deltas
   * @throws IOException if cleanup commands fail
   */
  private void cleanupRun(String worktreePath, String issueName, String isolationBranch,
    PrintStream out, SprtProgressListener progressListener) throws IOException
  {
    out.println("Step 7: Cleanup...");
    progressListener.onCleanupStarted();
    runner.removeIsolationBranch(new String[]{worktreePath, isolationBranch});
    runner.removeRunnerWorktrees(new String[]{worktreePath, issueName});
    out.println();
  }

  /**
   * Prints the final per-test and overall SPRT results.
   *
   * @param isolationResult the isolation mapping payload
   * @param testCaseIds the ordered test-case identifiers
   * @param decisions the final decisions by test case
   * @param runCounts the final run counts by test case
   * @param out the user-facing output stream
   * @param overallDecision the overall SPRT decision
   * @param testSha the test SHA recorded in results
   * @throws IOException if a test-case name lookup fails
   */
  private void printFinalResults(String isolationResult, List<String> testCaseIds,
    Map<String, String> decisions, Map<String, Integer> runCounts, PrintStream out,
    String overallDecision, String testSha) throws IOException
  {
    out.println("=== SPRT Results ===");
    out.println();
    out.println("Overall Decision: " + overallDecision);
    out.println("Test SHA: " + testSha);
    out.println();
    for (String testCaseId : testCaseIds)
    {
      String originalStem = runner.getTcName(new String[]{isolationResult, testCaseId});
      out.println(testCaseId + ": " + decisions.get(testCaseId) + " (" + runCounts.get(testCaseId) +
        " runs) - " + originalStem + ".md");
    }
    out.println();
    out.println("COMPLETE: overall_decision=" + overallDecision);
  }

  /**
   * Computes cumulative failures already present before this batch.
   *
   * @param batchNum the batchNum
   *
   * @param sortedWorktrees the sortedWorktrees
   *
   * @param sprtStateData the sprtStateData
   *
   * @return the result
   */
  private int cumulativePriorFailures(int batchNum, List<JsonNode> sortedWorktrees, JsonNode sprtStateData)
  {
    if (batchNum > earlyFailWindow)
      return 0;
    int priorFailures = 0;
    for (JsonNode worktree : sortedWorktrees)
    {
      String testCaseId = worktree.path("tc_id").asString();
      priorFailures += sprtStateData.path(testCaseId).path("fails").asInt(0);
    }
    return priorFailures;
  }

  /**
   * Recomputes failed_test_ids from the current SPRT state file contents.
   *
   * @param sprtStatePath the sprtStatePath
   *
   * @param mapper the mapper
   */
  private void updateFailedTestIds(Path sprtStatePath, JsonMapper mapper) throws IOException
  {
    String freshStateJson = Files.readString(sprtStatePath);
    ObjectNode mutableStateRoot = (ObjectNode) mapper.readTree(freshStateJson);
    ArrayNode failedIdsArray = mapper.createArrayNode();
    JsonNode sprtState = mutableStateRoot.path("sprt_state");
    if (sprtState.isObject())
    {
      for (Map.Entry<String, JsonNode> entry : sprtState.properties())
      {
        if (entry.getValue().path("fails").asInt(0) > 0)
          failedIdsArray.add(entry.getKey());
      }
    }
    mutableStateRoot.set("failed_test_ids", failedIdsArray);
    Files.writeString(sprtStatePath, mapper.writeValueAsString(mutableStateRoot), UTF_8);
  }

  /**
   * Parses key=value output into a map.
   *
   * @param output the output
   *
   * @return the result
   */
  private static Map<String, String> parseKeyValue(String output)
  {
    Map<String, String> result = new HashMap<>();
    for (String line : output.split("\n"))
    {
      int equalsIndex = line.indexOf('=');
      if (equalsIndex > 0)
        result.put(line.substring(0, equalsIndex), line.substring(equalsIndex + 1));
    }
    return result;
  }

  /**
   * Formats elapsed milliseconds for status output.
   *
   * @param milliseconds the milliseconds
   *
   * @return the result
   */
  private static String formatDuration(long milliseconds)
  {
    long seconds = milliseconds / 1_000;
    if (seconds < 60)
      return seconds + "s";
    long minutes = seconds / 60;
    long remainingSeconds = seconds % 60;
    if (minutes < 60)
      return minutes + "m " + remainingSeconds + "s";
    long hours = minutes / 60;
    long remainingMinutes = minutes % 60;
    return hours + "h " + remainingMinutes + "m";
  }

  /**
   * Returns a required key from write-test-results output.
   *
   * @param writeVars the writeVars
   *
   * @param key the key
   *
   * @return the result
   */
  private static String requiredOutput(Map<String, String> writeVars, String key) throws IOException
  {
    String value = writeVars.get(key);
    if (value == null || value.isBlank())
      throw new IOException("SprtRunner write-test-results did not return " + key);
    return value;
  }

  /**
   * Aggregates pipeline errors.
   *
   * @param pipelineErrors the pipelineErrors
   *
   * @return the result
   */
  private static IOException aggregatePipelineErrors(List<Exception> pipelineErrors)
  {
    Exception firstError = pipelineErrors.getFirst();
    IOException aggregate;
    if (firstError instanceof IOException ioException)
      aggregate = ioException;
    else
      aggregate = new IOException("Pipeline failed", firstError);
    for (int i = 1; i < pipelineErrors.size(); ++i)
      aggregate.addSuppressed(pipelineErrors.get(i));
    return aggregate;
  }

  /**
   * Per-batch loop progress.
   *
   * @param undecided the undecided
   *
   * @param batchEarlyAbort the batchEarlyAbort
   *
   * @param batchResultNode the batchResultNode
   *
   * @param anyReject the anyReject
   */
  private record BatchProgress(List<String> undecided, boolean batchEarlyAbort,
    JsonNode batchResultNode, boolean anyReject)
  {
  }

  /**
   * Captures the remaining undecided cases and reject status after one trial.
   *
   * @param stillUndecided the test cases still lacking a terminal decision
   * @param anyReject whether any test case reached or was forced into reject
   */
  private record TrialDecisionResult(List<String> stillUndecided, boolean anyReject)
  {
  }

  /**
   * Carries the overall decision and test SHA returned by write-test-results.
   *
   * @param overallDecision the overall SPRT decision
   * @param testSha the test SHA written to the results file
   */
  private record TestResultsSummary(String overallDecision, String testSha)
  {
  }
}
