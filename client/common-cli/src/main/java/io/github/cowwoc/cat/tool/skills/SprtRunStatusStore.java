/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Persists whole-run SPRT status snapshots and append-only event deltas.
 */
final class SprtRunStatusStore
{
  private static final String SNAPSHOT_FILE = "sprt-run-status.json";
  private static final String EVENTS_FILE = "sprt-run-events.jsonl";
  private static final Duration EVENT_WAIT_POLL = Duration.ofMillis(250);
  private static final Duration STALE_THRESHOLD = Duration.ofMinutes(10);
  private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();

  private final JsonMapper mapper;
  private final Logger log;

  SprtRunStatusStore(JsonMapper mapper, Logger log)
  {
    requireThat(mapper, "mapper").isNotNull();
    requireThat(log, "log").isNotNull();
    this.mapper = mapper;
    this.log = log;
  }

  SprtProgressListener createListener(Path worktreePath, String sessionId, String testDir, String modelId,
    String effort)
    throws IOException
  {
    requireThat(worktreePath, "worktreePath").isNotNull();
    String normalizedWorktree = worktreePath.toAbsolutePath().normalize().toString();
    String fingerprint = fingerprint(normalizedWorktree, testDir, modelId, effort, sessionId);
    String now = Instant.now().toString();
    SprtRunStatus initial = new SprtRunStatus(SprtRunStatus.SCHEMA_VERSION,
      UUID.randomUUID().toString(), sessionId, normalizedWorktree, testDir, modelId, effort,
      SprtRunStatus.STATUS_RUNNING, SprtRunStatus.PHASE_PREPARE, null, null, null, null, null,
      0, null, 0, now, null, null, null, null, null, fingerprint, null);
    Path snapshotPath = snapshotPath(worktreePath);
    Path eventsPath = eventsPath(worktreePath);
    synchronized (lockFor(worktreePath))
    {
      Files.createDirectories(snapshotPath.getParent());
      Files.deleteIfExists(eventsPath);
      writeSnapshot(snapshotPath, initial);
    }
    return new StatusListener(worktreePath, initial);
  }

  StatusReadResult readStatus(Path worktreePath, long sinceSeq, Duration waitDuration) throws IOException
  {
    requireThat(worktreePath, "worktreePath").isNotNull();
    if (waitDuration == null)
      waitDuration = Duration.ZERO;
    Instant deadline = Instant.now().plus(waitDuration);
    while (true)
    {
      synchronized (lockFor(worktreePath))
      {
        StatusReadResult result = readStatusOnce(worktreePath, sinceSeq);
        if (!result.events().isEmpty() || isTerminal(result.status()) || waitDuration.isZero() ||
          waitDuration.isNegative())
        {
          return result;
        }
      }
      if (Instant.now().isAfter(deadline))
        return readStatusOnce(worktreePath, sinceSeq);
      try
      {
        Thread.sleep(EVENT_WAIT_POLL);
      }
      catch (InterruptedException _)
      {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while waiting for SPRT status events");
      }
    }
  }

  private StatusReadResult readStatusOnce(Path worktreePath, long sinceSeq) throws IOException
  {
    Path snapshotPath = snapshotPath(worktreePath);
    Path eventsPath = eventsPath(worktreePath);
    if (!Files.exists(snapshotPath))
      return new StatusReadResult(notStarted(worktreePath), List.of());
    try
    {
      SprtRunStatus status = SprtRunStatus.fromJson(mapper.readTree(Files.readString(snapshotPath, UTF_8)));
      status = validateTerminalArtifacts(status);
      if (SprtRunStatus.STATUS_RUNNING.equals(status.status()) && isStale(status))
      {
        status = copyStatus(status, SprtRunStatus.STATUS_STALE, status.phase(), status.batch(),
          status.trialsPerBatch(), status.totalTestCases(), status.undecidedCount(),
          status.decidedCount(), status.cumulativeFailures(), status.currentParallelism(),
          status.lastEventSeq(), status.lastEventAt(), status.sprtStatePath(), status.outputDir(),
          status.testResultsPath(), status.overallDecision(), status.testSha(), status.error());
      }
      return new StatusReadResult(status, readEventsSince(eventsPath, sinceSeq));
    }
    catch (Exception e)
    {
      log.warn("Unable to read SPRT status snapshot for {}", worktreePath, e);
      return new StatusReadResult(unknown(worktreePath, "Malformed status snapshot: " + e.getMessage()),
        safeReadEvents(eventsPath, sinceSeq));
    }
  }

  private List<JsonNode> safeReadEvents(Path eventsPath, long sinceSeq)
  {
    try
    {
      return readEventsSince(eventsPath, sinceSeq);
    }
    catch (IOException _)
    {
      return List.of();
    }
  }

  private List<JsonNode> readEventsSince(Path eventsPath, long sinceSeq) throws IOException
  {
    if (!Files.exists(eventsPath))
      return List.of();
    List<JsonNode> events = new ArrayList<>();
    for (String line : Files.readAllLines(eventsPath, UTF_8))
    {
      if (line.isBlank())
        continue;
      JsonNode event = mapper.readTree(line);
      if (event.path("seq").asLong() > sinceSeq)
        events.add(event);
    }
    return List.copyOf(events);
  }

  private SprtRunStatus validateTerminalArtifacts(SprtRunStatus status)
  {
    if (!SprtRunStatus.STATUS_COMPLETED.equals(status.status()))
      return status;
    if (status.testResultsPath() == null)
      return copyStatus(status, SprtRunStatus.STATUS_UNKNOWN, SprtRunStatus.PHASE_ERROR,
        status.batch(), status.trialsPerBatch(), status.totalTestCases(), status.undecidedCount(),
        status.decidedCount(), status.cumulativeFailures(), status.currentParallelism(),
        status.lastEventSeq(), status.lastEventAt(), status.sprtStatePath(), status.outputDir(),
        status.testResultsPath(), status.overallDecision(), status.testSha(),
        "COMPLETED snapshot missing test-results path");
    Path testResults = Path.of(status.testResultsPath());
    if (!Files.exists(testResults))
    {
      return copyStatus(status, SprtRunStatus.STATUS_UNKNOWN, SprtRunStatus.PHASE_ERROR,
        status.batch(), status.trialsPerBatch(), status.totalTestCases(), status.undecidedCount(),
        status.decidedCount(), status.cumulativeFailures(), status.currentParallelism(),
        status.lastEventSeq(), status.lastEventAt(), status.sprtStatePath(), status.outputDir(),
        status.testResultsPath(), status.overallDecision(), status.testSha(),
        "COMPLETED snapshot missing test-results artifact");
    }
    return status;
  }

  private boolean isStale(SprtRunStatus status)
  {
    Instant lastEvent = Instant.parse(status.lastEventAt());
    return Duration.between(lastEvent, Instant.now()).compareTo(STALE_THRESHOLD) > 0;
  }

  private Object lockFor(Path worktreePath)
  {
    return LOCKS.computeIfAbsent(worktreePath.toAbsolutePath().normalize(), ignored -> new Object());
  }

  private Path snapshotPath(Path worktreePath)
  {
    return worktreePath.toAbsolutePath().normalize().resolve(".cat/work").resolve(SNAPSHOT_FILE);
  }

  private Path eventsPath(Path worktreePath)
  {
    return worktreePath.toAbsolutePath().normalize().resolve(".cat/work").resolve(EVENTS_FILE);
  }

  private void appendEvent(Path eventsPath, ObjectNode event) throws IOException
  {
    java.nio.file.StandardOpenOption openOption = java.nio.file.StandardOpenOption.CREATE;
    if (Files.exists(eventsPath))
      openOption = java.nio.file.StandardOpenOption.APPEND;
    Files.writeString(eventsPath, mapper.writeValueAsString(event) + System.lineSeparator(), UTF_8,
      openOption);
  }

  private void writeSnapshot(Path snapshotPath, SprtRunStatus status) throws IOException
  {
    Path tmp = snapshotPath.resolveSibling(snapshotPath.getFileName() + ".tmp");
    Files.writeString(tmp, mapper.writeValueAsString(status.toObjectNode(mapper)), UTF_8);
    try
    {
      Files.move(tmp, snapshotPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    catch (AtomicMoveNotSupportedException _)
    {
      Files.move(tmp, snapshotPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static SprtRunStatus notStarted(Path worktreePath)
  {
    String now = Instant.now().toString();
    return new SprtRunStatus(SprtRunStatus.SCHEMA_VERSION, "not-started", "unknown",
      worktreePath.toAbsolutePath().normalize().toString(), "unknown", "unknown", "unknown",
      SprtRunStatus.STATUS_NOT_STARTED, SprtRunStatus.PHASE_PREPARE, null, null, null, null,
      null, null, null, 0, now, null, null, null, null, null, "not-started", null);
  }

  private static SprtRunStatus unknown(Path worktreePath, String error)
  {
    String now = Instant.now().toString();
    return new SprtRunStatus(SprtRunStatus.SCHEMA_VERSION, "unknown", "unknown",
      worktreePath.toAbsolutePath().normalize().toString(), "unknown", "unknown", "unknown",
      SprtRunStatus.STATUS_UNKNOWN, SprtRunStatus.PHASE_ERROR, null, null, null, null,
      null, null, null, 0, now, null, null, null, null, null, "unknown", error);
  }

  private static boolean isTerminal(SprtRunStatus status)
  {
    return switch (status.status())
    {
      case SprtRunStatus.STATUS_COMPLETED, SprtRunStatus.STATUS_FAILED,
        SprtRunStatus.STATUS_ABORTED, SprtRunStatus.STATUS_UNKNOWN -> true;
      default -> false;
    };
  }

  private static String fingerprint(String worktreePath, String testDir, String modelId, String effort,
    String sessionId)
  {
    try
    {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = (worktreePath + "\n" + testDir + "\n" + modelId + "\n" + effort + "\n" +
        sessionId).getBytes(UTF_8);
      return HexFormat.of().formatHex(digest.digest(bytes));
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new AssertionError("SHA-256 algorithm not available", e);
    }
  }

  private static SprtRunStatus copyStatus(SprtRunStatus previous, String status, String phase,
    Integer batch, Integer trialsPerBatch, Integer totalTestCases, Integer undecidedCount,
    Integer decidedCount, Integer cumulativeFailures, Integer currentParallelism, long lastEventSeq,
    String lastEventAt, String sprtStatePath, String outputDir, String testResultsPath,
    String overallDecision, String testSha, String error)
  {
    return new SprtRunStatus(previous.schemaVersion(), previous.runId(), previous.sessionId(),
      previous.worktreePath(), previous.testDir(), previous.modelId(), previous.effort(), status,
      phase, batch, trialsPerBatch, totalTestCases, undecidedCount, decidedCount,
      cumulativeFailures, currentParallelism, lastEventSeq, lastEventAt, sprtStatePath, outputDir,
      testResultsPath, overallDecision, testSha, previous.invocationFingerprint(), error);
  }

  /**
   * Snapshot and event deltas returned by {@link #readStatus(Path, long, Duration)}.
   *
   * @param status the current whole-run snapshot
   * @param events append-only events newer than the requested sequence
   */
  record StatusReadResult(SprtRunStatus status, List<JsonNode> events)
  {
  }

  private final class StatusListener implements SprtProgressListener
  {
    private final Path worktreePath;
    private SprtRunStatus current;

    private StatusListener(Path worktreePath, SprtRunStatus initial)
    {
      this.worktreePath = worktreePath.toAbsolutePath().normalize();
      this.current = initial;
    }

    @Override
    public void onRunStarted(int totalTestCases, Path sprtStatePath)
    {
      record("run_started", SprtRunStatus.PHASE_PREPARE, "SPRT run started", snapshot ->
      {
        snapshot.put("total_test_cases", totalTestCases);
        snapshot.put("sprt_state_path", sprtStatePath.toString());
      });
    }

    @Override
    public void onPhaseChanged(String phase, String message)
    {
      record("phase_changed", phase, message, ignored -> {});
    }

    @Override
    public void onBatchStarted(int batch, int trialsPerBatch, int undecidedCount, int currentParallelism)
    {
      record("batch_started", SprtRunStatus.PHASE_BATCH_RUNNING,
        "Starting batch " + batch + " with " + undecidedCount + " undecided test case(s)", snapshot ->
        {
          snapshot.put("batch", batch);
          snapshot.put("trials_per_batch", trialsPerBatch);
          snapshot.put("undecided_count", undecidedCount);
          snapshot.put("current_parallelism", currentParallelism);
        });
    }

    @Override
    public void onTrialResult(String testCaseId, int runNumber, String decision, int undecidedCount,
      int decidedCount, int cumulativeFailures)
    {
      record("trial_result", SprtRunStatus.PHASE_BATCH_RUNNING,
        testCaseId + " -> " + decision + " after run " + runNumber, snapshot ->
        {
          putNullable(snapshot, "undecided_count", undecidedCount);
          putNullable(snapshot, "decided_count", decidedCount);
          putNullable(snapshot, "cumulative_failures", cumulativeFailures);
          snapshot.put("test_case_id", testCaseId);
          snapshot.put("run_number", runNumber);
          snapshot.put("decision", decision);
        });
    }

    @Override
    public void onBatchSummary(int batch, int undecidedCount, int decidedCount, int cumulativeFailures)
    {
      record("batch_summary", SprtRunStatus.PHASE_BATCH_SUMMARY,
        "Finished batch " + batch + " with " + undecidedCount + " undecided test case(s)", snapshot ->
        {
          snapshot.put("batch", batch);
          putNullable(snapshot, "undecided_count", undecidedCount);
          putNullable(snapshot, "decided_count", decidedCount);
          putNullable(snapshot, "cumulative_failures", cumulativeFailures);
        });
    }

    @Override
    public void onEarlyAbort(int batch, int cumulativeFailures)
    {
      record("early_abort", SprtRunStatus.PHASE_BATCH_SUMMARY,
        "Aborted early after batch " + batch, snapshot ->
        {
          snapshot.put("status", SprtRunStatus.STATUS_ABORTED);
          snapshot.put("batch", batch);
          putNullable(snapshot, "cumulative_failures", cumulativeFailures);
        });
    }

    @Override
    public void onRejectAbort(int undecidedCount)
    {
      record("reject_abort", SprtRunStatus.PHASE_BATCH_SUMMARY,
        "Aborted after REJECT decision", snapshot ->
        {
          snapshot.put("status", SprtRunStatus.STATUS_ABORTED);
          putNullable(snapshot, "undecided_count", undecidedCount);
        });
    }

    @Override
    public void onWritingResults(Path testResultsPath)
    {
      record("writing_results", SprtRunStatus.PHASE_WRITING_RESULTS,
        "Writing test-results.json", snapshot -> snapshot.put("test_results_path",
          testResultsPath.toString()));
    }

    @Override
    public void onCleanupStarted()
    {
      record("cleanup_started", SprtRunStatus.PHASE_CLEANUP, "Cleaning up runner worktrees",
        ignored -> {});
    }

    @Override
    public void onCompleted(String overallDecision, String testSha, Path testResultsPath)
    {
      record("completed", SprtRunStatus.PHASE_COMPLETE, "SPRT run completed", snapshot ->
      {
        snapshot.put("status", SprtRunStatus.STATUS_COMPLETED);
        snapshot.put("overall_decision", overallDecision);
        snapshot.put("test_sha", testSha);
        snapshot.put("test_results_path", testResultsPath.toString());
      });
    }

    @Override
    public void onFailed(String error)
    {
      record("failed", SprtRunStatus.PHASE_ERROR, error, snapshot ->
      {
        snapshot.put("status", SprtRunStatus.STATUS_FAILED);
        snapshot.put("error", error);
      });
    }

    /**
     * Appends one event line and refreshes the current snapshot atomically under the worktree lock.
     *
     * @param type the event type identifier
     * @param phase the workflow phase associated with the event
     * @param message the human-readable event message
     * @param extraFields callback that writes additional fields into both event and snapshot nodes
     */
    private void record(String type, String phase, String message,
      java.util.function.Consumer<ObjectNode> extraFields)
    {
      try
      {
        synchronized (lockFor(worktreePath))
        {
          Path snapshotPath = snapshotPath(worktreePath);
          Path eventsPath = eventsPath(worktreePath);
          String now = Instant.now().toString();
          long seq = current.lastEventSeq() + 1;
          ObjectNode event = mapper.createObjectNode();
          event.put("seq", seq);
          event.put("timestamp", now);
          event.put("type", type);
          event.put("phase", phase);
          event.put("message", message);
          extraFields.accept(event);
          appendEvent(eventsPath, event);

          ObjectNode snapshot = current.toObjectNode(mapper);
          JsonNode statusNode = snapshot.get("status");
          String currentStatus;
          if (statusNode == null)
            currentStatus = null;
          else
            currentStatus = statusNode.stringValue();
          if (currentStatus == null)
            currentStatus = SprtRunStatus.STATUS_RUNNING;
          snapshot.put("status", currentStatus);
          snapshot.put("phase", phase);
          snapshot.put("last_event_seq", seq);
          snapshot.put("last_event_at", now);
          extraFields.accept(snapshot);
          current = SprtRunStatus.fromJson(snapshot);
          writeSnapshot(snapshotPath, current);
        }
      }
      catch (Exception e)
      {
        log.warn("Unable to persist SPRT status update for {}", worktreePath, e);
      }
    }

    /**
     * Writes an integer field or explicit JSON null into a mutable snapshot/event node.
     *
     * @param node the destination node
     * @param field the field name
     * @param value the nullable integer value
     */
    private void putNullable(ObjectNode node, String field, Integer value)
    {
      if (value != null)
        node.put(field, value);
      else
        node.putNull(field);
    }
  }
}
