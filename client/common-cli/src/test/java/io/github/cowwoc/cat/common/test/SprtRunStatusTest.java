/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.common.test;

import io.github.cowwoc.cat.tool.AbstractCliTool;
import io.github.cowwoc.cat.tool.skills.SprtRunner;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for the whole-run SPRT status surface.
 */
public final class SprtRunStatusTest
{
  /**
   * Verifies that run-status reports NOT_STARTED before any status artifacts exist.
   *
   * @throws IOException if fixture setup fails
   */
  @Test
  public void runStatusNotStartedWhenArtifactsAbsent() throws IOException
  {
    Path worktree = Files.createTempDirectory("sprt-run-status-empty-");
    try (TestCliTool scope = new TestCliTool(worktree))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      JsonNode root = scope.getJsonMapper().readTree(runner.runStatus(new String[]{worktree.toString()}));

      requireThat(root.path("status").path("status").asString(), "status").isEqualTo("NOT_STARTED");
      requireThat(root.path("status").path("phase").asString(), "phase").isEqualTo("PREPARE");
      requireThat(root.path("events").size(), "eventsSize").isEqualTo(0);
    }
    finally
    {
      deleteRecursively(worktree);
    }
  }

  /**
   * Verifies that run-status returns only event deltas newer than the requested sequence.
   *
   * @throws IOException if fixture setup fails
   */
  @Test
  public void runStatusFiltersEventsBySequence() throws IOException
  {
    Path worktree = Files.createTempDirectory("sprt-run-status-events-");
    try (TestCliTool scope = new TestCliTool(worktree))
    {
      Path statusDir = Files.createDirectories(worktree.resolve(".cat/work"));
      Path resultsPath = worktree.resolve("tests/test-results.json");
      String now = Instant.now().toString();
      Files.createDirectories(resultsPath.getParent());
      Files.writeString(resultsPath, "{\"ok\":true}", StandardCharsets.UTF_8);
      writeRunStatusSnapshot(statusDir.resolve("sprt-run-status.json"), """
        {
          "schema_version":1,
          "run_id":"run-1",
          "session_id":"sess-1",
          "worktree_path":"%s",
          "test_dir":"plugin/tests/sample",
          "model_id":"gpt-5.4",
          "effort":"high",
          "status":"RUNNING",
          "phase":"BATCH_RUNNING",
          "batch":2,
          "trials_per_batch":1,
          "total_test_cases":3,
          "undecided_count":1,
          "decided_count":2,
          "cumulative_failures":1,
          "current_parallelism":2,
          "last_event_seq":2,
          "last_event_at":"%s",
          "sprt_state_path":"%s",
          "output_dir":"%s",
          "test_results_path":"%s",
          "overall_decision":null,
          "test_sha":null,
          "invocation_fingerprint":"fingerprint-1",
          "error":null
        }
        """.formatted(escapeJson(worktree), now, escapeJson(worktree.resolve("sprt-state.json")),
        escapeJson(worktree.resolve("outputs")), escapeJson(resultsPath)));
      Files.writeString(statusDir.resolve("sprt-run-events.jsonl"),
        "{\"seq\":1,\"timestamp\":\"2026-06-14T11:59:00Z\",\"type\":\"phase_changed\"," +
          "\"phase\":\"INIT_STATE\",\"message\":\"Initializing\"}\n" +
          "{\"seq\":2,\"timestamp\":\"2026-06-14T12:00:00Z\",\"type\":\"batch_started\"," +
          "\"phase\":\"BATCH_RUNNING\",\"message\":\"Starting batch 2\"}\n",
        StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      JsonNode root = scope.getJsonMapper().readTree(
        runner.runStatus(new String[]{worktree.toString(), "--since-seq", "1"}));

      requireThat(root.path("status").path("status").asString(), "status").isEqualTo("RUNNING");
      requireThat(root.path("events").size(), "eventsSize").isEqualTo(1);
      requireThat(root.path("events").get(0).path("seq").asInt(), "seq").isEqualTo(2);
      requireThat(root.path("events").get(0).path("message").asString(), "message").
        isEqualTo("Starting batch 2");
    }
    finally
    {
      deleteRecursively(worktree);
    }
  }

  /**
   * Verifies that run-status treats a completed snapshot without test-results.json as UNKNOWN.
   *
   * @throws IOException if fixture setup fails
   */
  @Test
  public void runStatusSummaryHandlesMissingResults() throws IOException
  {
    Path worktree = Files.createTempDirectory("sprt-run-status-missing-results-");
    try (TestCliTool scope = new TestCliTool(worktree))
    {
      Path statusDir = Files.createDirectories(worktree.resolve(".cat/work"));
      writeRunStatusSnapshot(statusDir.resolve("sprt-run-status.json"), """
        {
          "schema_version":1,
          "run_id":"run-2",
          "session_id":"sess-2",
          "worktree_path":"%s",
          "test_dir":"plugin/tests/sample",
          "model_id":"gpt-5.4",
          "effort":"high",
          "status":"COMPLETED",
          "phase":"COMPLETE",
          "batch":3,
          "trials_per_batch":2,
          "total_test_cases":4,
          "undecided_count":0,
          "decided_count":4,
          "cumulative_failures":1,
          "current_parallelism":1,
          "last_event_seq":1,
          "last_event_at":"2026-06-14T12:00:00Z",
          "sprt_state_path":"%s",
          "output_dir":"%s",
          "test_results_path":"%s",
          "overall_decision":"ACCEPT",
          "test_sha":"abc123",
          "invocation_fingerprint":"fingerprint-2",
          "error":null
        }
        """.formatted(escapeJson(worktree), escapeJson(worktree.resolve("sprt-state.json")),
        escapeJson(worktree.resolve("outputs")),
        escapeJson(worktree.resolve("tests/test-results.json"))));
      Files.writeString(statusDir.resolve("sprt-run-events.jsonl"),
        "{\"seq\":1,\"timestamp\":\"2026-06-14T12:00:00Z\",\"type\":\"completed\",\"phase\":\"COMPLETE\"," +
          "\"message\":\"SPRT run completed\"}\n", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String summary = runner.runStatus(new String[]{worktree.toString(), "--summary"});

      requireThat(summary, "summary").contains("UNKNOWN ERROR");
      requireThat(summary, "summary").contains("missing test-results artifact");
      requireThat(summary, "summary").contains("SPRT run completed");
    }
    finally
    {
      deleteRecursively(worktree);
    }
  }

  /**
   * Writes a run-status snapshot fixture to disk.
   *
   * @param path the snapshot path
   * @param json the snapshot JSON content
   * @throws IOException if writing fails
   */
  private static void writeRunStatusSnapshot(Path path, String json) throws IOException
  {
    Files.writeString(path, json.strip() + System.lineSeparator(), StandardCharsets.UTF_8);
  }

  /**
   * Escapes a path for embedding inside JSON fixtures.
   *
   * @param path the path to escape
   * @return the escaped string value
   */
  private static String escapeJson(Path path)
  {
    return path.toAbsolutePath().normalize().toString().replace("\\", "\\\\");
  }

  /**
   * Deletes a temporary directory tree.
   *
   * @param root the root to delete
   * @throws IOException if deletion fails
   */
  private static void deleteRecursively(Path root) throws IOException
  {
    if (Files.notExists(root))
      return;
    try (java.util.stream.Stream<Path> stream = Files.walk(root))
    {
      for (Path path : stream.sorted(Comparator.reverseOrder()).toList())
        Files.deleteIfExists(path);
    }
  }

  /**
   * Minimal CLI test scope for engine-neutral common-cli tests.
   */
  private static final class TestCliTool extends AbstractCliTool
  {
    /**
     * Creates a Codex-shaped CLI scope rooted at a temporary worktree.
     *
     * @param worktree the temporary worktree and plugin root
     */
    private TestCliTool(Path worktree)
    {
      super("test-session", worktree, worktree, worktree.resolve(".codex"),
        worktree.resolve(".codex"), Path.of(".codex-plugin/plugin.json"),
        List.of(), Path.of(".codex-plugin/plugin.json"), worktree, "UTC", "");
    }
  }
}
