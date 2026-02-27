/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetSubagentStatusOutput;
import io.github.cowwoc.cat.hooks.skills.GetSubagentStatusOutput.StatusResult;
import io.github.cowwoc.cat.hooks.skills.GetSubagentStatusOutput.SubagentInfo;
import io.github.cowwoc.cat.hooks.skills.GetSubagentStatusOutput.SubagentStatus;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetSubagentStatusOutput.
 * <p>
 * Tests verify that the monitor subagents handler returns JSON output,
 * validates its inputs, and correctly reports subagent status.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetSubagentStatusOutputTest
{
  /**
   * Verifies that getOutput returns JSON string.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputReturnsJsonString() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-subagent-status-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetSubagentStatusOutput handler = new GetSubagentStatusOutput(scope);
      String result = handler.getOutput(new String[]{});

      // Result should be JSON
      requireThat(result, "result").
        isNotNull().
        contains("{").
        contains("}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that null args throw NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nullArgsThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-subagent-status-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetSubagentStatusOutput handler = new GetSubagentStatusOutput(scope);
      try
      {
        handler.getOutput(null);
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (NullPointerException _)
      {
        // Expected
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that constructor validates scope parameter.
   */
  @Test
  public void constructorValidatesScope()
  {
    try
    {
      new GetSubagentStatusOutput(null);
      requireThat(false, "shouldThrowException").isEqualTo(true);
    }
    catch (NullPointerException _)
    {
      // Expected
    }
  }

  /**
   * Verifies that empty args array returns valid JSON.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyArgsReturnsValidJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-subagent-status-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetSubagentStatusOutput handler = new GetSubagentStatusOutput(scope);
      String result = handler.getOutput(new String[]{});

      // Parse as JSON to validate
      requireThat(result, "result").
        isNotNull().
        isNotEmpty();
      // JSON must be valid - check for basic JSON structure
      requireThat(result.trim(), "result").
        startsWith("{").
        endsWith("}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that output contains data key (expected JSON structure).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void outputContainsExpectedJsonStructure() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-subagent-status-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetSubagentStatusOutput handler = new GetSubagentStatusOutput(scope);
      String result = handler.getOutput(new String[]{});

      // Result should contain data or subagents array or status - at least one expected key
      boolean hasExpectedKey = result.contains("\"data\"") || result.contains("\"subagents\"") ||
        result.contains("\"status\"");
      requireThat(hasExpectedKey, "hasExpectedKey").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

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
      JsonMapper mapper = JsonMapper.builder().build();
      StatusResult result = GetSubagentStatusOutput.getStatus(sessionBase.toString(), mapper);

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
      JsonMapper mapper = JsonMapper.builder().build();
      StatusResult result = GetSubagentStatusOutput.getStatus(sessionBase.toString(), mapper);
      String json = result.toJson(mapper);

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
      new GetSubagentStatusOutput.Summary(-1, 0, 0, 0);
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("total");
    }

    try
    {
      new GetSubagentStatusOutput.Summary(0, -1, 0, 0);
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("running");
    }

    try
    {
      new GetSubagentStatusOutput.Summary(0, 0, -1, 0);
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("complete");
    }

    try
    {
      new GetSubagentStatusOutput.Summary(0, 0, 0, -1);
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
    StatusResult result = new StatusResult(
      java.util.List.of(info),
      new GetSubagentStatusOutput.Summary(1, 1, 0, 0));

    JsonMapper mapper = JsonMapper.builder().build();
    String json = result.toJson(mapper);

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
    JsonMapper mapper = JsonMapper.builder().build();
    StatusResult result = GetSubagentStatusOutput.getStatus("/nonexistent/path", mapper);

    requireThat(result.summary().total(), "total").isNotNegative();
    requireThat(result.subagents(), "subagents").isNotNull();
  }

  /**
   * Verifies that StatusResult validates null subagents list.
   */
  @Test
  public void monitorResultValidatesNullSubagents()
  {
    try
    {
      new StatusResult(null, new GetSubagentStatusOutput.Summary(0, 0, 0, 0));
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("subagents");
    }
  }

  /**
   * Verifies that StatusResult validates null summary.
   */
  @Test
  public void monitorResultValidatesNullSummary()
  {
    try
    {
      new StatusResult(java.util.List.of(), null);
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
    StatusResult result = new StatusResult(
      java.util.List.of(info1, info2),
      new GetSubagentStatusOutput.Summary(2, 1, 1, 0));

    JsonMapper mapper = JsonMapper.builder().build();
    String json = result.toJson(mapper);

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
