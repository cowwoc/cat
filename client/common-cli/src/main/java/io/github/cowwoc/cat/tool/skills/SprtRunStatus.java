/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Immutable whole-run SPRT status snapshot.
 *
 * @param schemaVersion schema version for status and event compatibility
 * @param runId unique identifier for the current run instance
 * @param sessionId CAT session identifier
 * @param worktreePath worktree under test
 * @param testDir test directory under test
 * @param modelId model under test
 * @param effort effort under test
 * @param status operational lifecycle state
 * @param phase current workflow phase
 * @param batch current batch number, if any
 * @param trialsPerBatch current trials-per-batch setting, if any
 * @param totalTestCases total test cases in the run
 * @param undecidedCount remaining undecided test cases
 * @param decidedCount test cases that reached ACCEPT or REJECT
 * @param cumulativeFailures cumulative failing trial count
 * @param currentParallelism current parallelism level, if known
 * @param lastEventSeq last emitted event sequence
 * @param lastEventAt timestamp of last event
 * @param sprtStatePath SPRT state file path
 * @param outputDir trial-output directory, if known
 * @param testResultsPath final test-results artifact path, if known
 * @param overallDecision overall formal decision, if known
 * @param testSha test-results SHA, if known
 * @param invocationFingerprint fingerprint of the inputs for this run
 * @param error operational error summary, if any
 */
record SprtRunStatus(
  int schemaVersion,
  String runId,
  String sessionId,
  String worktreePath,
  String testDir,
  String modelId,
  String effort,
  String status,
  String phase,
  Integer batch,
  Integer trialsPerBatch,
  Integer totalTestCases,
  Integer undecidedCount,
  Integer decidedCount,
  Integer cumulativeFailures,
  Integer currentParallelism,
  long lastEventSeq,
  String lastEventAt,
  String sprtStatePath,
  String outputDir,
  String testResultsPath,
  String overallDecision,
  String testSha,
  String invocationFingerprint,
  String error)
{
  static final int SCHEMA_VERSION = 1;

  static final String STATUS_NOT_STARTED = "NOT_STARTED";
  static final String STATUS_RUNNING = "RUNNING";
  static final String STATUS_COMPLETED = "COMPLETED";
  static final String STATUS_FAILED = "FAILED";
  static final String STATUS_ABORTED = "ABORTED";
  static final String STATUS_STALE = "STALE";
  static final String STATUS_UNKNOWN = "UNKNOWN";

  static final String PHASE_PREPARE = "PREPARE";
  static final String PHASE_CLEANUP_PREVIOUS = "CLEANUP_PREVIOUS";
  static final String PHASE_ISOLATION = "ISOLATION";
  static final String PHASE_INIT_STATE = "INIT_STATE";
  static final String PHASE_BATCH_RUNNING = "BATCH_RUNNING";
  static final String PHASE_BATCH_SUMMARY = "BATCH_SUMMARY";
  static final String PHASE_WRITING_RESULTS = "WRITING_RESULTS";
  static final String PHASE_CLEANUP = "CLEANUP";
  static final String PHASE_COMPLETE = "COMPLETE";
  static final String PHASE_ERROR = "ERROR";

  SprtRunStatus
  {
    requireThat(runId, "runId").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(worktreePath, "worktreePath").isNotBlank();
    requireThat(testDir, "testDir").isNotBlank();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(status, "status").isNotBlank();
    requireThat(phase, "phase").isNotBlank();
    requireThat(lastEventAt, "lastEventAt").isNotBlank();
    requireThat(invocationFingerprint, "invocationFingerprint").isNotBlank();
  }

  ObjectNode toObjectNode(JsonMapper mapper)
  {
    ObjectNode result = mapper.createObjectNode();
    result.put("schema_version", schemaVersion);
    result.put("run_id", runId);
    result.put("session_id", sessionId);
    result.put("worktree_path", worktreePath);
    result.put("test_dir", testDir);
    result.put("model_id", modelId);
    result.put("effort", effort);
    result.put("status", status);
    result.put("phase", phase);
    putNullable(result, "batch", batch);
    putNullable(result, "trials_per_batch", trialsPerBatch);
    putNullable(result, "total_test_cases", totalTestCases);
    putNullable(result, "undecided_count", undecidedCount);
    putNullable(result, "decided_count", decidedCount);
    putNullable(result, "cumulative_failures", cumulativeFailures);
    putNullable(result, "current_parallelism", currentParallelism);
    result.put("last_event_seq", lastEventSeq);
    result.put("last_event_at", lastEventAt);
    putNullable(result, "sprt_state_path", sprtStatePath);
    putNullable(result, "output_dir", outputDir);
    putNullable(result, "test_results_path", testResultsPath);
    putNullable(result, "overall_decision", overallDecision);
    putNullable(result, "test_sha", testSha);
    result.put("invocation_fingerprint", invocationFingerprint);
    putNullable(result, "error", error);
    return result;
  }

  static SprtRunStatus fromJson(JsonNode node)
  {
    requireThat(node, "node").isNotNull();
    return new SprtRunStatus(
      node.path("schema_version").asInt(SCHEMA_VERSION),
      requiredText(node, "run_id"),
      requiredText(node, "session_id"),
      requiredText(node, "worktree_path"),
      requiredText(node, "test_dir"),
      requiredText(node, "model_id"),
      requiredText(node, "effort"),
      requiredText(node, "status"),
      requiredText(node, "phase"),
      optionalInt(node, "batch"),
      optionalInt(node, "trials_per_batch"),
      optionalInt(node, "total_test_cases"),
      optionalInt(node, "undecided_count"),
      optionalInt(node, "decided_count"),
      optionalInt(node, "cumulative_failures"),
      optionalInt(node, "current_parallelism"),
      node.path("last_event_seq").asLong(0),
      requiredText(node, "last_event_at"),
      optionalText(node, "sprt_state_path"),
      optionalText(node, "output_dir"),
      optionalText(node, "test_results_path"),
      optionalText(node, "overall_decision"),
      optionalText(node, "test_sha"),
      requiredText(node, "invocation_fingerprint"),
      optionalText(node, "error"));
  }

  private static void putNullable(ObjectNode node, String field, Integer value)
  {
    if (value != null)
      node.put(field, value);
    else
      node.putNull(field);
  }

  private static void putNullable(ObjectNode node, String field, String value)
  {
    if (value != null)
      node.put(field, value);
    else
      node.putNull(field);
  }

  private static Integer optionalInt(JsonNode node, String field)
  {
    JsonNode child = node.get(field);
    if (child == null || child.isNull())
      return null;
    return child.asInt();
  }

  private static String optionalText(JsonNode node, String field)
  {
    JsonNode child = node.get(field);
    if (child == null || child.isNull())
      return null;
    return child.stringValue();
  }

  private static String requiredText(JsonNode node, String field)
  {
    JsonNode child = node.get(field);
    if (child == null || child.isNull())
      throw new IllegalArgumentException("Missing required status field: " + field);
    return child.stringValue();
  }
}
