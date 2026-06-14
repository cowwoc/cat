/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import java.nio.file.Path;

/**
 * Receives operational whole-run SPRT progress callbacks.
 */
interface SprtProgressListener
{
  SprtProgressListener NO_OP = new SprtProgressListener()
  {
  };

  /**
   * Announces the start of a whole-run SPRT execution.
   *
   * @param totalTestCases the total number of test cases scheduled for the run
   * @param sprtStatePath the path to the current SPRT state file
   */
  default void onRunStarted(int totalTestCases, Path sprtStatePath)
  {
  }

  /**
   * Announces a whole-run phase transition.
   *
   * @param phase the new phase identifier
   * @param message the user-facing description of the phase change
   */
  default void onPhaseChanged(String phase, String message)
  {
  }

  /**
   * Announces the start of one batch of undecided test cases.
   *
   * @param batch the 1-based batch number
   * @param trialsPerBatch the number of trials scheduled per undecided test case
   * @param undecidedCount the number of undecided test cases entering the batch
   * @param currentParallelism the parallelism selected for the batch
   */
  default void onBatchStarted(int batch, int trialsPerBatch, int undecidedCount, int currentParallelism)
  {
  }

  /**
   * Announces the outcome of one trial.
   *
   * @param testCaseId the test-case identifier
   * @param runNumber the 1-based run number for the test case
   * @param decision the boundary decision observed after the trial
   * @param undecidedCount the current number of undecided test cases
   * @param decidedCount the current number of decided test cases
   * @param cumulativeFailures the current cumulative failure count
   */
  default void onTrialResult(String testCaseId, int runNumber, String decision, int undecidedCount,
    int decidedCount, int cumulativeFailures)
  {
  }

  /**
   * Announces the end-of-batch summary.
   *
   * @param batch the completed batch number
   * @param undecidedCount the remaining undecided test-case count
   * @param decidedCount the decided test-case count
   * @param cumulativeFailures the current cumulative failure count
   */
  default void onBatchSummary(int batch, int undecidedCount, int decidedCount, int cumulativeFailures)
  {
  }

  /**
   * Announces early abort due to the configured cumulative failure threshold.
   *
   * @param batch the batch after which the abort triggered
   * @param cumulativeFailures the failure count that triggered the abort
   */
  default void onEarlyAbort(int batch, int cumulativeFailures)
  {
  }

  /**
   * Announces abort after an observed REJECT decision.
   *
   * @param undecidedCount the remaining undecided test-case count at abort time
   */
  default void onRejectAbort(int undecidedCount)
  {
  }

  /**
   * Announces that final test results are being written.
   *
   * @param testResultsPath the path to the test-results artifact being written
   */
  default void onWritingResults(Path testResultsPath)
  {
  }

  /**
   * Announces cleanup of runner worktrees and related temporary artifacts.
   */
  default void onCleanupStarted()
  {
  }

  /**
   * Announces successful completion of the whole run.
   *
   * @param overallDecision the overall SPRT decision
   * @param testSha the final test SHA recorded in results
   * @param testResultsPath the path to the written test-results artifact
   */
  default void onCompleted(String overallDecision, String testSha, Path testResultsPath)
  {
  }

  /**
   * Announces terminal run failure.
   *
   * @param error the failure summary message
   */
  default void onFailed(String error)
  {
  }
}
