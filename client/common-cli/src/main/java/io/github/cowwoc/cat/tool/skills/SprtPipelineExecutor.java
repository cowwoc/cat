/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes per-batch SPRT trial pipelines.
 */
final class SprtPipelineExecutor
{
  private final SprtRunner runner;
  private final Logger log;
  private final SprtGrader sprtGrader;

  /**
   * Creates a new pipeline executor.
   *
   * @param runner the SPRT runner facade
   * @param log the workflow logger
   * @param sprtGrader the grader used for completed trials
   */
  SprtPipelineExecutor(SprtRunner runner, Logger log, SprtGrader sprtGrader)
  {
    this.runner = runner;
    this.log = log;
    this.sprtGrader = sprtGrader;
  }

  /**
   * Runs one batch of parallel pipelines.
   *
   * @param worktreePath the worktree root under test
   * @param sprtStateJson the SPRT state path
   * @param issueName the issue name
   * @param testDirRel the relative test directory
   * @param modelId the test model
   * @param testEffort the test effort
   * @param isolationResultJson the isolation mapping payload
   * @param outputDir the output directory
   * @param processedCount the count already processed this batch
   * @param sortedWorktrees the sorted worktree entries
   * @param currentParallelism the current parallelism
   * @param mapper the JSON mapper
   * @param sprtLock the shared SPRT lock
   * @param cumulativeFailures the cumulative failure counter
   * @param inconclusiveTestCases the batch-local inconclusive list
   * @return the batch outcome
   * @throws InterruptedException if interrupted while joining pipelines
   */
  BatchOutcome runParallelPipelines(String worktreePath, String sprtStateJson, String issueName,
    String testDirRel, String modelId, String testEffort, String isolationResultJson,
    String outputDir, int processedCount, List<JsonNode> sortedWorktrees, int currentParallelism,
    JsonMapper mapper, Object sprtLock, AtomicInteger cumulativeFailures,
    ArrayNode inconclusiveTestCases) throws InterruptedException
  {
    int batchSize = Math.min(currentParallelism, sortedWorktrees.size() - processedCount);
    List<Thread> pipelineThreads = new ArrayList<>();
    List<Boolean> pipelineFailures = java.util.Collections.synchronizedList(new ArrayList<>());
    List<Exception> pipelineErrors = java.util.Collections.synchronizedList(new ArrayList<>());
    AtomicInteger batchDecidedCount = new AtomicInteger(0);

    for (int i = 0; i < batchSize; ++i)
    {
      JsonNode worktreeNode = sortedWorktrees.get(processedCount + i);
      String testCaseId = worktreeNode.path("tc_id").asString();
      String runnerWorktree = worktreeNode.path("runner_worktree").asString();
      int trialNum = worktreeNode.path("trial_num").asInt();
      PipelineInputs inputs = preparePipelineInputs(worktreePath, issueName, testDirRel, testCaseId,
        runnerWorktree, outputDir, trialNum, mapper);
      Thread pipelineThread = Thread.ofVirtual().start(() ->
        runPipeline(inputs, worktreePath, testDirRel, sprtStateJson, isolationResultJson, modelId,
          testEffort, mapper, sprtLock, cumulativeFailures, batchDecidedCount,
          inconclusiveTestCases, pipelineFailures, pipelineErrors));
      pipelineThreads.add(pipelineThread);
    }
    for (Thread pipelineThread : pipelineThreads)
      pipelineThread.join();
    boolean anyFailed = pipelineFailures.stream().anyMatch(failed -> failed);
    return new BatchOutcome(batchDecidedCount.get(), batchSize, anyFailed, pipelineErrors);
  }

  /**
   * Prepares one pipeline's prompt/output inputs.
   *
   * @param worktreePath the worktree root under test
   * @param issueName the issue name
   * @param testDirRel the relative test directory
   * @param testCaseId the test-case identifier
   * @param runnerWorktree the runner worktree
   * @param outputDir the output directory
   * @param trialNum the trial number
   * @param mapper the JSON mapper
   * @return the prepared pipeline inputs
   */
  private PipelineInputs preparePipelineInputs(String worktreePath, String issueName,
    String testDirRel, String testCaseId, String runnerWorktree, String outputDir, int trialNum,
    JsonMapper mapper)
  {
    try
    {
      String prepareResult = runner.prepareTrial(new String[]{worktreePath, issueName + "-isolation",
        testDirRel, testCaseId, runnerWorktree, outputDir, String.valueOf(trialNum)});
      String promptFile = null;
      List<Path> promptFiles = new ArrayList<>();
      String outputJson = null;
      boolean hasFixture = false;
      for (String line : prepareResult.split("\n"))
      {
        if (line.startsWith("prompt_file="))
          promptFile = line.substring("prompt_file=".length());
        else if (line.startsWith("prompt_files_json="))
        {
          JsonNode promptFilesNode = mapper.readTree(line.substring("prompt_files_json=".length()));
          for (JsonNode pathNode : promptFilesNode)
          {
            String promptPathValue = pathNode.stringValue();
            if (promptPathValue != null)
              promptFiles.add(Path.of(promptPathValue));
          }
        }
        else if (line.startsWith("output_json="))
          outputJson = line.substring("output_json=".length());
        else if (line.startsWith("runner_fixture="))
          hasFixture = true;
      }
      if (promptFiles.isEmpty() && promptFile != null)
        promptFiles.add(Path.of(promptFile));
      if (outputJson == null || (!hasFixture && promptFiles.isEmpty()))
      {
        throw new IOException("prepare-trial did not return all required fields for " + testCaseId);
      }
      return new PipelineInputs(testCaseId, runnerWorktree, trialNum, List.copyOf(promptFiles),
        outputJson, hasFixture);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Runs one trial pipeline and captures any failure into the shared batch state.
   *
   * @param inputs the prepared trial inputs
   * @param worktreePath the worktree root under test
   * @param testDirRel the relative test directory
   * @param sprtStateJson the SPRT state path
   * @param isolationResultJson the isolation mapping payload
   * @param modelId the test model
   * @param testEffort the test effort
   * @param mapper the JSON mapper
   * @param sprtLock the shared SPRT lock
   * @param cumulativeFailures the cumulative failure counter
   * @param batchDecidedCount the per-batch decision counter
   * @param inconclusiveTestCases the batch-local inconclusive list
   * @param pipelineFailures the per-pipeline failure list
   * @param pipelineErrors the per-pipeline error list
   */
  private void runPipeline(PipelineInputs inputs, String worktreePath, String testDirRel,
    String sprtStateJson, String isolationResultJson, String modelId, String testEffort,
    JsonMapper mapper, Object sprtLock, AtomicInteger cumulativeFailures,
    AtomicInteger batchDecidedCount, ArrayNode inconclusiveTestCases,
    List<Boolean> pipelineFailures, List<Exception> pipelineErrors)
  {
    boolean failed = false;
    try
    {
      failed = executeTrialAndGrade(inputs, worktreePath, testDirRel, sprtStateJson,
        isolationResultJson, modelId, testEffort, mapper, sprtLock, cumulativeFailures,
        batchDecidedCount, inconclusiveTestCases);
    }
    catch (Exception e)
    {
      log.error("Pipeline for {} failed", inputs.testCaseId(), e);
      pipelineErrors.add(e);
      failed = true;
    }
    finally
    {
      pipelineFailures.add(failed);
    }
  }

  /**
   * Executes the runner, contamination check, grading, and SPRT update for one pipeline.
   *
   * @param inputs the trial inputs
   * @param worktreePath the worktree root under test
   * @param testDirRel the relative test directory
   * @param sprtStateJson the SPRT state path
   * @param isolationResultJson the isolation mapping payload
   * @param modelId the test model
   * @param testEffort the test effort
   * @param mapper the JSON mapper
   * @param sprtLock the shared SPRT lock
   * @param cumulativeFailures the cumulative failure counter
   * @param batchDecidedCount the count of decisions reached in this batch
   * @param inconclusiveTestCases the batch-local inconclusive list
   * @return {@code true} if the pipeline failed, otherwise {@code false}
   * @throws IOException if runner output, grading, or state updates fail
   */
  private boolean executeTrialAndGrade(PipelineInputs inputs, String worktreePath,
    String testDirRel, String sprtStateJson, String isolationResultJson, String modelId,
    String testEffort, JsonMapper mapper, Object sprtLock, AtomicInteger cumulativeFailures,
    AtomicInteger batchDecidedCount, ArrayNode inconclusiveTestCases) throws IOException
  {
    if (!inputs.hasFixture() && handleRunnerFailure(inputs, sprtStateJson, modelId, testEffort,
      mapper, sprtLock, cumulativeFailures, batchDecidedCount, inconclusiveTestCases))
    {
      return true;
    }

    String gradeFilePath = Path.of(Path.of(inputs.outputJson()).getParent().toString(),
      inputs.testCaseId() + "_run" + inputs.trialNum() + "_grade.json").toString();
    String verdict = sprtGrader.gradeTc(inputs.testCaseId(), inputs.trialNum(), inputs.outputJson(),
      modelId, testEffort, inputs.runnerWorktree(), inputs.runnerWorktree(),
      Path.of(worktreePath, testDirRel).toString(), gradeFilePath, isolationResultJson);
    boolean passed = verdict.equals("PASS");
    if (!passed)
      cumulativeFailures.incrementAndGet();
    updateBoundaryDecision(inputs, sprtStateJson, passed, mapper, sprtLock, batchDecidedCount,
      inconclusiveTestCases);
    return !passed;
  }

  /**
   * Runs the nested engine and contamination checks for one trial.
   *
   * @param inputs the trial inputs
   * @param sprtStateJson the SPRT state path
   * @param modelId the model under test
   * @param testEffort the effort under test
   * @param mapper the JSON mapper
   * @param sprtLock the shared SPRT state lock
   * @param cumulativeFailures the cumulative failure counter
   * @param batchDecidedCount the count of decisions reached in this batch
   * @param inconclusiveTestCases the batch-local inconclusive list
   * @return {@code true} if the trial failed before grading
   * @throws IOException if runner execution or SPRT updates fail
   */
  private boolean handleRunnerFailure(PipelineInputs inputs, String sprtStateJson,
    String modelId, String testEffort, JsonMapper mapper, Object sprtLock,
    AtomicInteger cumulativeFailures, AtomicInteger batchDecidedCount,
    ArrayNode inconclusiveTestCases) throws IOException
  {
    Path runnerLogPath = Path.of(inputs.outputJson()).getParent().
      resolve(inputs.testCaseId() + "_run" + inputs.trialNum() + "_runner.log");
    int exitCode = runNestedTrial(inputs, modelId, testEffort, runnerLogPath);
    if (exitCode != 0 || !Files.exists(Path.of(inputs.outputJson())))
    {
      log.warn("{}: runner failed (exit={}). Log: {}", inputs.testCaseId(), exitCode, runnerLogPath);
      cumulativeFailures.incrementAndGet();
      recordFailureDecision(sprtStateJson, inputs.testCaseId(), inputs.trialNum(), "runner-failure",
        mapper, sprtLock, batchDecidedCount, inconclusiveTestCases);
      return true;
    }
    String contamination = runner.checkRunContamination(new String[]{inputs.outputJson()});
    if (!contamination.contains("status=FAIL"))
      return false;
    log.warn("{}: contamination detected", inputs.testCaseId());
    cumulativeFailures.incrementAndGet();
    recordFailureDecision(sprtStateJson, inputs.testCaseId(), inputs.trialNum(), "contamination",
      mapper, sprtLock, batchDecidedCount, inconclusiveTestCases);
    return true;
  }

  /**
   * Runs one nested trial while teeing launcher output to the runner log file.
   *
   * @param inputs the trial inputs
   * @param modelId the model under test
   * @param testEffort the effort under test
   * @param runnerLogPath the destination for launcher logs
   * @return the nested runner exit code
   * @throws IOException if trial execution fails
   */
  private int runNestedTrial(PipelineInputs inputs, String modelId, String testEffort,
    Path runnerLogPath) throws IOException
  {
    try (OutputStream logOut = Files.newOutputStream(runnerLogPath);
      PrintStream logStream = new PrintStream(logOut, true, UTF_8))
    {
      return runner.runTrial(inputs.promptFiles(), modelId, testEffort, inputs.runnerWorktree(),
        inputs.outputJson(), logStream);
    }
  }

  /**
   * Updates the SPRT boundary after one graded trial result.
   *
   * @param inputs the trial inputs
   * @param sprtStateJson the SPRT state path
   * @param passed whether the trial passed
   * @param mapper the JSON mapper
   * @param sprtLock the shared SPRT state lock
   * @param batchDecidedCount the count of decisions reached in this batch
   * @param inconclusiveTestCases the batch-local inconclusive list
   * @throws IOException if SPRT update or boundary checks fail
   */
  private void updateBoundaryDecision(PipelineInputs inputs, String sprtStateJson, boolean passed,
    JsonMapper mapper, Object sprtLock, AtomicInteger batchDecidedCount,
    ArrayNode inconclusiveTestCases) throws IOException
  {
    synchronized (sprtLock)
    {
      runner.updateSprt(new String[]{sprtStateJson, inputs.testCaseId(), String.valueOf(passed)});
      String boundaryResult = runner.checkBoundary(new String[]{sprtStateJson, inputs.testCaseId()});
      JsonNode boundaryNode = mapper.readTree(boundaryResult);
      String decision = boundaryNode.path("decision").asString();
      log.info("{}: {} (trial={})", inputs.testCaseId(), decision, inputs.trialNum());
      if (decision.equals("ACCEPT") || decision.equals("REJECT"))
        batchDecidedCount.incrementAndGet();
      else
        inconclusiveTestCases.add(inputs.testCaseId());
    }
  }

  /**
   * Records a failed trial as a reject-capable SPRT update.
   *
   * @param sprtStateJson the SPRT state path
   * @param testCaseId the test-case identifier
   * @param trialNum the trial number
   * @param failureType the failure category
   * @param mapper the JSON mapper
   * @param sprtLock the shared SPRT state lock
   * @param batchDecidedCount the count of decisions reached in this batch
   * @param inconclusiveTestCases the batch-local inconclusive list
   * @throws IOException if SPRT update or boundary checks fail
   */
  private void recordFailureDecision(String sprtStateJson, String testCaseId, int trialNum,
    String failureType, JsonMapper mapper, Object sprtLock, AtomicInteger batchDecidedCount,
    ArrayNode inconclusiveTestCases) throws IOException
  {
    synchronized (sprtLock)
    {
      runner.updateSprt(new String[]{sprtStateJson, testCaseId, "false"});
      String boundaryResult = runner.checkBoundary(new String[]{sprtStateJson, testCaseId});
      JsonNode boundaryNode = mapper.readTree(boundaryResult);
      String decision = boundaryNode.path("decision").asString();
      log.info("{}: {} (trial={}, {})", testCaseId, decision, trialNum, failureType);
      if (decision.equals("ACCEPT") || decision.equals("REJECT"))
        batchDecidedCount.incrementAndGet();
      else
        inconclusiveTestCases.add(testCaseId);
    }
  }

  /**
   * Inputs for one test-case pipeline.
   *
   * @param testCaseId the test-case identifier
   * @param runnerWorktree the runner worktree
   * @param trialNum the trial number
   * @param promptFiles the prompt files for the trial
   * @param outputJson the output JSON path
   * @param hasFixture whether the runner used a prebuilt fixture
   */
  record PipelineInputs(String testCaseId, String runnerWorktree, int trialNum,
                        List<Path> promptFiles, String outputJson, boolean hasFixture)
  {
  }

  /**
   * Aggregated outcome for a batch of pipelines.
   *
   * @param decidedCount the number of test cases decided in the batch
   * @param batchSize the number of pipelines run in the batch
   * @param anyFailed whether any pipeline failed
   * @param errors the collected pipeline errors
   */
  record BatchOutcome(int decidedCount, int batchSize, boolean anyFailed, List<Exception> errors)
  {
  }
}
