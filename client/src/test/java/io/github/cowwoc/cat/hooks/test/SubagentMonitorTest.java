/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.SubagentMonitor;
import io.github.cowwoc.cat.hooks.util.SubagentMonitor.MonitorResult;
import io.github.cowwoc.cat.hooks.util.SubagentMonitor.SubagentInfo;
import io.github.cowwoc.cat.hooks.util.SubagentMonitor.SubagentStatus;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for SubagentMonitor.
 * <p>
 * Tests verify monitoring of subagent worktrees, token counting, and JSON output.
 */
public class SubagentMonitorTest
{
  /**
   * Verifies that monitor returns empty result when no subagent worktrees exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void monitorReturnsEmptyWhenNoSubagents() throws IOException
  {
    Path sessionBase = Files.createTempDirectory("session-test");
    try
    {
      MonitorResult result = SubagentMonitor.monitor(sessionBase.toString());

      requireThat(result.summary().total(), "total").isEqualTo(0);
      requireThat(result.summary().running(), "running").isEqualTo(0);
      requireThat(result.summary().complete(), "complete").isEqualTo(0);
      requireThat(result.summary().warning(), "warning").isEqualTo(0);
      requireThat(result.subagents(), "subagents").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(sessionBase);
    }
  }

  /**
   * Verifies that toJson produces valid JSON for empty result.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toJsonProducesValidJsonForEmptyResult() throws IOException
  {
    Path sessionBase = Files.createTempDirectory("session-test");
    try
    {
      MonitorResult result = SubagentMonitor.monitor(sessionBase.toString());
      String json = result.toJson();

      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode root = mapper.readTree(json);
      requireThat(root.has("subagents"), "hasSubagents").isTrue();
      requireThat(root.get("subagents").isArray(), "subagentsIsArray").isTrue();
      requireThat(root.get("subagents").size(), "subagentsSize").isEqualTo(0);
      requireThat(root.has("summary"), "hasSummary").isTrue();
      requireThat(root.get("summary").get("total").asInt(), "total").isEqualTo(0);
      requireThat(root.get("summary").get("running").asInt(), "running").isEqualTo(0);
      requireThat(root.get("summary").get("complete").asInt(), "complete").isEqualTo(0);
      requireThat(root.get("summary").get("warning").asInt(), "warning").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(sessionBase);
    }
  }

  /**
   * Verifies that SubagentInfo validates required fields.
   */
  @Test
  public void subagentInfoValidatesRequiredFields()
  {
    try
    {
      new SubagentInfo(null, "task", SubagentStatus.RUNNING, 0, 0, "/path");
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("id");
    }

    try
    {
      new SubagentInfo("abc123", null, SubagentStatus.RUNNING, 0, 0, "/path");
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("task");
    }

    try
    {
      new SubagentInfo("abc123", "task", null, 0, 0, "/path");
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("status");
    }

    try
    {
      new SubagentInfo("abc123", "task", SubagentStatus.RUNNING, 0, 0, null);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("worktree");
    }
  }

  /**
   * Verifies that SubagentInfo validates non-negative token count.
   */
  @Test
  public void subagentInfoValidatesNonNegativeTokens()
  {
    try
    {
      new SubagentInfo("abc123", "task", SubagentStatus.RUNNING, -1, 0, "/path");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("tokens");
    }
  }

  /**
   * Verifies that SubagentInfo validates non-negative compaction count.
   */
  @Test
  public void subagentInfoValidatesNonNegativeCompactions()
  {
    try
    {
      new SubagentInfo("abc123", "task", SubagentStatus.RUNNING, 0, -1, "/path");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("compactions");
    }
  }

  /**
   * Verifies that Summary validates non-negative counts.
   */
  @Test
  public void summaryValidatesNonNegativeCounts()
  {
    try
    {
      new SubagentMonitor.Summary(-1, 0, 0, 0);
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("total");
    }

    try
    {
      new SubagentMonitor.Summary(0, -1, 0, 0);
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("running");
    }

    try
    {
      new SubagentMonitor.Summary(0, 0, -1, 0);
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("complete");
    }

    try
    {
      new SubagentMonitor.Summary(0, 0, 0, -1);
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("warning");
    }
  }

  /**
   * Verifies that toJson includes all subagent fields.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toJsonIncludesAllSubagentFields() throws IOException
  {
    SubagentInfo info = new SubagentInfo("abc123", "test-task", SubagentStatus.RUNNING, 5000, 2, "/path/to/worktree");
    MonitorResult result = new MonitorResult(
      java.util.List.of(info),
      new SubagentMonitor.Summary(1, 1, 0, 0));

    String json = result.toJson();

    JsonMapper mapper = JsonMapper.builder().build();
    JsonNode root = mapper.readTree(json);
    JsonNode subagent = root.get("subagents").get(0);
    requireThat(subagent.get("id").asString(), "id").isEqualTo("abc123");
    requireThat(subagent.get("task").asString(), "task").isEqualTo("test-task");
    requireThat(subagent.get("status").asString(), "status").isEqualTo("running");
    requireThat(subagent.get("tokens").asInt(), "tokens").isEqualTo(5000);
    requireThat(subagent.get("compactions").asInt(), "compactions").isEqualTo(2);
    requireThat(subagent.get("worktree").asString(), "worktree").isEqualTo("/path/to/worktree");
  }

  /**
   * Verifies that monitor handles non-existent session base directory.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void monitorHandlesNonExistentSessionBase() throws IOException
  {
    MonitorResult result = SubagentMonitor.monitor("/nonexistent/path");

    requireThat(result.summary().total(), "total").isNotNegative();
    requireThat(result.subagents(), "subagents").isNotNull();
  }

  /**
   * Verifies that MonitorResult validates null subagents list.
   */
  @Test
  public void monitorResultValidatesNullSubagents()
  {
    try
    {
      new MonitorResult(null, new SubagentMonitor.Summary(0, 0, 0, 0));
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("subagents");
    }
  }

  /**
   * Verifies that MonitorResult validates null summary.
   */
  @Test
  public void monitorResultValidatesNullSummary()
  {
    try
    {
      new MonitorResult(java.util.List.of(), null);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("summary");
    }
  }

  /**
   * Verifies that toJson produces parseable JSON with correct field values.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toJsonProducesParseableJsonWithCorrectValues() throws IOException
  {
    SubagentInfo info1 = new SubagentInfo(
      "abc123", "task-one", SubagentStatus.RUNNING, 5_000, 2, "/path/to/worktree1");
    SubagentInfo info2 = new SubagentInfo(
      "def456", "task-two", SubagentStatus.COMPLETE, 10_000, 3, "/path/to/worktree2");
    MonitorResult result = new MonitorResult(
      java.util.List.of(info1, info2),
      new SubagentMonitor.Summary(2, 1, 1, 0));

    String json = result.toJson();

    JsonMapper mapper = JsonMapper.builder().build();
    JsonNode parsed = mapper.readTree(json);

    requireThat(parsed.has("subagents"), "hasSubagents").isTrue();
    requireThat(parsed.has("summary"), "hasSummary").isTrue();

    JsonNode subagents = parsed.get("subagents");
    requireThat(subagents.isArray(), "isArray").isTrue();
    requireThat(subagents.size(), "size").isEqualTo(2);

    JsonNode first = subagents.get(0);
    requireThat(first.get("id").asString(), "id").isEqualTo("abc123");
    requireThat(first.get("task").asString(), "task").isEqualTo("task-one");
    requireThat(first.get("status").asString(), "status").isEqualTo("running");
    requireThat(first.get("tokens").asInt(), "tokens").isEqualTo(5000);
    requireThat(first.get("compactions").asInt(), "compactions").isEqualTo(2);

    JsonNode summary = parsed.get("summary");
    requireThat(summary.get("total").asInt(), "total").isEqualTo(2);
    requireThat(summary.get("running").asInt(), "running").isEqualTo(1);
    requireThat(summary.get("complete").asInt(), "complete").isEqualTo(1);
    requireThat(summary.get("warning").asInt(), "warning").isEqualTo(0);
  }
}
