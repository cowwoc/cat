/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.JsonNode;
import io.github.cowwoc.cat.hooks.util.SessionAnalyzer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

/**
 * Tests for SessionAnalyzer.
 */
public final class SessionAnalyzerTest
{
  // Common JSON fragments used to build JSONL test data
  private static final String ASSISTANT_PREFIX =
    "{\"type\":\"assistant\",\"message\":{\"id\":\"";
  private static final String CONTENT_PREFIX = "\",\"content\":[";
  private static final String TOOL_USE_PREFIX =
    "{\"type\":\"tool_use\",\"id\":\"";

  /**
   * Builds an assistant message with a single tool_use entry.
   *
   * @param msgId the message ID
   * @param toolId the tool use ID
   * @param toolName the tool name
   * @param input the JSON input object (without braces wrapping)
   * @return a JSONL line
   */
  private static String assistantMessage(String msgId, String toolId,
    String toolName, String input)
  {
    return assistantMessage(msgId, toolId, toolName, input, "");
  }

  /**
   * Builds an assistant message with a single tool_use entry and usage.
   *
   * @param msgId the message ID
   * @param toolId the tool use ID
   * @param toolName the tool name
   * @param input the JSON input object (without braces wrapping)
   * @param usageSuffix the usage JSON suffix (e.g.
   *   ",\"usage\":{...}")
   * @return a JSONL line
   */
  private static String assistantMessage(String msgId, String toolId,
    String toolName, String input, String usageSuffix)
  {
    return ASSISTANT_PREFIX + msgId + CONTENT_PREFIX +
      TOOL_USE_PREFIX + toolId +
      "\",\"name\":\"" + toolName +
      "\",\"input\":{" + input + "}}]" +
      usageSuffix + "}}";
  }

  /**
   * Builds an assistant message with multiple tool_use entries.
   *
   * @param msgId the message ID
   * @param toolEntries the pre-built tool_use JSON array entries
   * @return a JSONL line
   */
  private static String assistantMultiTool(String msgId,
    String toolEntries)
  {
    return ASSISTANT_PREFIX + msgId + CONTENT_PREFIX +
      toolEntries + "]}}";
  }

  /**
   * Builds a single tool_use JSON object.
   *
   * @param toolId the tool use ID
   * @param toolName the tool name
   * @param input the input content (without braces)
   * @return a tool_use JSON object string
   */
  private static String toolUse(String toolId, String toolName,
    String input)
  {
    return TOOL_USE_PREFIX + toolId +
      "\",\"name\":\"" + toolName +
      "\",\"input\":{" + input + "}}";
  }

  /**
   * Builds a tool_result JSONL line.
   *
   * @param toolUseId the tool use ID
   * @param content the result content
   * @return a JSONL line
   */
  private static String toolResult(String toolUseId, String content)
  {
    return "{\"type\":\"tool_result\"," +
      "\"tool_use_id\":\"" + toolUseId + "\"," +
      "\"content\":\"" + content + "\"}";
  }

  /**
   * Verifies that empty JSONL file returns empty analysis with zero
   * counts.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void emptyFileReturnsEmptyAnalysis() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      Files.writeString(tempFile, "");

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.analyzeSingleAgent(tempFile);

      requireThat(result.path("tool_frequency").size(),
        "tool_frequency_size").isEqualTo(0);
      requireThat(result.path("token_usage").size(),
        "token_usage_size").isEqualTo(0);
      requireThat(result.path("output_sizes").size(),
        "output_sizes_size").isEqualTo(0);
      requireThat(result.path("cache_candidates").size(),
        "cache_candidates_size").isEqualTo(0);
      requireThat(result.path("batch_candidates").size(),
        "batch_candidates_size").isEqualTo(0);
      requireThat(result.path("parallel_candidates").size(),
        "parallel_candidates_size").isEqualTo(0);
      requireThat(
        result.path("summary").path("total_tool_calls").asInt(),
        "total_tool_calls").isEqualTo(0);
      requireThat(
        result.path("summary").path("total_entries").asInt(),
        "total_entries").isEqualTo(0);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that tool_use entries are correctly extracted and
   * counted.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void extractsToolUseEntries() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String usage1 =
        ",\"usage\":{\"input_tokens\":100,\"output_tokens\":50}";
      String usage2 =
        ",\"usage\":{\"input_tokens\":200,\"output_tokens\":75}";
      String jsonl =
        assistantMessage("msg1", "tool1", "Read",
          "\"file_path\":\"/test.txt\"", usage1) + "\n" +
        toolResult("tool1", "file contents") + "\n" +
        assistantMessage("msg2", "tool2", "Write",
          "\"file_path\":\"/out.txt\",\"content\":\"data\"",
          usage2) + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.analyzeSingleAgent(tempFile);

      requireThat(result.path("tool_frequency").size(),
        "tool_frequency_size").isEqualTo(2);

      // Both tools have count=1, so order is not guaranteed
      boolean foundRead = false;
      boolean foundWrite = false;
      for (JsonNode freq : result.path("tool_frequency"))
      {
        String tool = freq.path("tool").asString();
        int count = freq.path("count").asInt();
        if (tool.equals("Read"))
        {
          foundRead = true;
          requireThat(count, "read_count").isEqualTo(1);
        }
        if (tool.equals("Write"))
        {
          foundWrite = true;
          requireThat(count, "write_count").isEqualTo(1);
        }
      }
      requireThat(foundRead, "found_read").isTrue();
      requireThat(foundWrite, "found_write").isTrue();

      requireThat(
        result.path("summary").path("total_tool_calls").asInt(),
        "total_tool_calls").isEqualTo(2);
      requireThat(
        result.path("summary").path("unique_tools").size(),
        "unique_tools_size").isEqualTo(2);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that token usage is correctly calculated per tool.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void calculatesTokenUsagePerTool() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String usage1 =
        ",\"usage\":{\"input_tokens\":100,\"output_tokens\":50}";
      String usage2 =
        ",\"usage\":{\"input_tokens\":150,\"output_tokens\":60}";
      String usage3 =
        ",\"usage\":{\"input_tokens\":200,\"output_tokens\":75}";
      String jsonl =
        assistantMessage("msg1", "tool1", "Read", "",
          usage1) + "\n" +
        assistantMessage("msg2", "tool2", "Read", "",
          usage2) + "\n" +
        assistantMessage("msg3", "tool3", "Write", "",
          usage3) + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.analyzeSingleAgent(tempFile);

      requireThat(result.path("token_usage").size(),
        "token_usage_size").isEqualTo(2);

      JsonNode readUsage = result.path("token_usage").get(0);
      requireThat(readUsage.path("tool").asString(),
        "tool_name").isEqualTo("Read");
      requireThat(readUsage.path("total_input_tokens").asInt(),
        "input_tokens").isEqualTo(250);
      requireThat(readUsage.path("total_output_tokens").asInt(),
        "output_tokens").isEqualTo(110);
      requireThat(readUsage.path("count").asInt(),
        "count").isEqualTo(2);

      JsonNode writeUsage = result.path("token_usage").get(1);
      requireThat(writeUsage.path("tool").asString(),
        "tool_name").isEqualTo("Write");
      requireThat(writeUsage.path("total_input_tokens").asInt(),
        "input_tokens").isEqualTo(200);
      requireThat(writeUsage.path("total_output_tokens").asInt(),
        "output_tokens").isEqualTo(75);
      requireThat(writeUsage.path("count").asInt(),
        "count").isEqualTo(1);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that output sizes are extracted and sorted by length.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void extractsOutputSizesSortedDescending() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String jsonl = """
        {"type":"tool_result","tool_use_id":"tool1","content":"short"}
        {"type":"tool_result","tool_use_id":"tool2","content":"this is a much longer output with more content"}
        {"type":"tool_result","tool_use_id":"tool3","content":"medium length output"}
        """;
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.analyzeSingleAgent(tempFile);

      JsonNode outputSizes = result.path("output_sizes");
      requireThat(outputSizes.size(),
        "output_sizes_size").isEqualTo(3);
      JsonNode largest = outputSizes.get(0);
      requireThat(largest.path("tool_use_id").asString(),
        "largest_id").isEqualTo("tool2");
      requireThat(largest.path("output_length").asInt(),
        "largest_length").isEqualTo(46);
      JsonNode smallest = outputSizes.get(2);
      requireThat(smallest.path("tool_use_id").asString(),
        "smallest_id").isEqualTo("tool1");
      requireThat(smallest.path("output_length").asInt(),
        "smallest_length").isEqualTo(5);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that identical repeated operations are identified as
   * cache candidates.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void identifiesCacheCandidatesForRepeatedOperations()
    throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String filePath = "\"file_path\":\"/test.txt\"";
      String outPath = "\"file_path\":\"/out.txt\"";
      String jsonl =
        assistantMessage("msg1", "tool1", "Read",
          filePath) + "\n" +
        assistantMessage("msg2", "tool2", "Read",
          filePath) + "\n" +
        assistantMessage("msg3", "tool3", "Read",
          filePath) + "\n" +
        assistantMessage("msg4", "tool4", "Write",
          outPath) + "\n" +
        assistantMessage("msg5", "tool5", "Write",
          outPath) + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.analyzeSingleAgent(tempFile);

      requireThat(result.path("cache_candidates").size(),
        "cache_candidates_size").isEqualTo(2);

      JsonNode firstCandidate =
        result.path("cache_candidates").get(0);
      requireThat(
        firstCandidate.path("operation").path("name").asString(),
        "most_repeated_tool").isEqualTo("Read");
      requireThat(firstCandidate.path("repeat_count").asInt(),
        "repeat_count").isEqualTo(3);
      requireThat(firstCandidate.path("optimization").asString(),
        "optimization_type").isEqualTo("CACHE_CANDIDATE");

      JsonNode secondCandidate =
        result.path("cache_candidates").get(1);
      requireThat(
        secondCandidate.path("operation").path("name").asString(),
        "second_tool").isEqualTo("Write");
      requireThat(secondCandidate.path("repeat_count").asInt(),
        "repeat_count").isEqualTo(2);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that consecutive operations of the same tool are
   * identified as batch candidates.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void identifiesBatchCandidatesForConsecutiveOperations()
    throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String jsonl =
        assistantMessage("msg1", "tool1", "Read",
          "\"file_path\":\"/a.txt\"") + "\n" +
        assistantMessage("msg2", "tool2", "Read",
          "\"file_path\":\"/b.txt\"") + "\n" +
        assistantMessage("msg3", "tool3", "Read",
          "\"file_path\":\"/c.txt\"") + "\n" +
        assistantMessage("msg4", "tool4", "Read",
          "\"file_path\":\"/d.txt\"") + "\n" +
        assistantMessage("msg5", "tool5", "Write", "") + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.analyzeSingleAgent(tempFile);

      requireThat(result.path("batch_candidates").size(),
        "batch_candidates_size").isEqualTo(1);

      JsonNode candidate =
        result.path("batch_candidates").get(0);
      requireThat(candidate.path("tool").asString(),
        "tool_name").isEqualTo("Read");
      requireThat(candidate.path("consecutive_count").asInt(),
        "consecutive_count").isEqualTo(4);
      requireThat(candidate.path("optimization").asString(),
        "optimization_type").isEqualTo("BATCH_CANDIDATE");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that multiple tools in the same message are identified
   * as parallel candidates.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void identifiesParallelCandidatesForMultipleToolsInMessage()
    throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String tools1 =
        toolUse("tool1", "Read", "") + "," +
        toolUse("tool2", "Write", "") + "," +
        toolUse("tool3", "Bash", "");
      String tools2 =
        toolUse("tool4", "Read", "") + "," +
        toolUse("tool5", "Write", "");
      String jsonl =
        assistantMultiTool("msg1", tools1) + "\n" +
        assistantMultiTool("msg2", tools2) + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.analyzeSingleAgent(tempFile);

      requireThat(result.path("parallel_candidates").size(),
        "parallel_candidates_size").isEqualTo(2);

      JsonNode firstCandidate =
        result.path("parallel_candidates").get(0);
      requireThat(
        firstCandidate.path("message_id").asString(),
        "message_id").isEqualTo("msg1");
      requireThat(firstCandidate.path("count").asInt(),
        "count").isEqualTo(3);
      requireThat(
        firstCandidate.path("parallel_tools").size(),
        "tools_size").isEqualTo(3);
      requireThat(
        firstCandidate.path("optimization").asString(),
        "optimization_type").isEqualTo("PARALLEL_CANDIDATE");

      JsonNode secondCandidate =
        result.path("parallel_candidates").get(1);
      requireThat(
        secondCandidate.path("message_id").asString(),
        "message_id").isEqualTo("msg2");
      requireThat(secondCandidate.path("count").asInt(),
        "count").isEqualTo(2);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that subagent files are discovered from parent session.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void discoversSubagentFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-");
    Path mainSession = tempDir.resolve("main.jsonl");
    Path subagentsDir = tempDir.resolve("subagents");
    Files.createDirectories(subagentsDir);
    Path subagent1 =
      subagentsDir.resolve("agent-abc123.jsonl");
    Path subagent2 =
      subagentsDir.resolve("agent-def456.jsonl");

    try
    {
      String mainJsonl =
        assistantMessage("msg1", "tool1", "Task", "") +
        "\n" +
        toolResult("tool1",
          "{\\\"agentId\\\":\\\"abc123\\\"," +
          "\\\"status\\\":\\\"running\\\"}") + "\n" +
        assistantMessage("msg2", "tool2", "Task", "") +
        "\n" +
        toolResult("tool2",
          "{\\\"agentId\\\":\\\"def456\\\"," +
          "\\\"status\\\":\\\"running\\\"}") + "\n";
      Files.writeString(mainSession, mainJsonl);

      String subagent1Jsonl =
        assistantMessage("sub1", "t1", "Read", "") + "\n";
      Files.writeString(subagent1, subagent1Jsonl);

      String subagent2Jsonl =
        assistantMessage("sub2", "t2", "Write", "") + "\n";
      Files.writeString(subagent2, subagent2Jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.analyzeSession(mainSession);

      requireThat(result.has("main"), "has_main").isTrue();
      requireThat(result.has("subagents"),
        "has_subagents").isTrue();
      requireThat(result.has("combined"),
        "has_combined").isTrue();

      JsonNode subagents = result.path("subagents");
      requireThat(subagents.has("abc123"),
        "has_abc123").isTrue();
      requireThat(subagents.has("def456"),
        "has_def456").isTrue();

      JsonNode combined = result.path("combined");
      requireThat(
        combined.path("summary").path("agent_count").asInt(),
        "agent_count").isEqualTo(3);
      JsonNode summary = combined.path("summary");
      requireThat(
        summary.path("total_tool_calls").asInt(),
        "total_tool_calls").isEqualTo(4);
    }
    finally
    {
      Files.deleteIfExists(subagent1);
      Files.deleteIfExists(subagent2);
      Files.deleteIfExists(subagentsDir);
      Files.deleteIfExists(mainSession);
      Files.deleteIfExists(tempDir);
    }
  }

  /**
   * Verifies that combined analysis merges tool frequency across
   * agents.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void combinedAnalysisMergesToolFrequency()
    throws IOException
  {
    Path tempDir = Files.createTempDirectory("session-");
    Path mainSession = tempDir.resolve("main.jsonl");
    Path subagentsDir = tempDir.resolve("subagents");
    Files.createDirectories(subagentsDir);
    Path subagent1 =
      subagentsDir.resolve("agent-abc123.jsonl");

    try
    {
      String mainJsonl =
        assistantMessage("msg1", "tool1", "Task", "") +
        "\n" +
        toolResult("tool1",
          "{\\\"agentId\\\":\\\"abc123\\\"}") + "\n" +
        assistantMessage("msg2", "tool2", "Read", "") +
        "\n" +
        assistantMessage("msg3", "tool3", "Read", "") +
        "\n";
      Files.writeString(mainSession, mainJsonl);

      String subagent1Jsonl =
        assistantMessage("sub1", "t1", "Read", "") + "\n" +
        assistantMessage("sub2", "t2", "Write", "") + "\n";
      Files.writeString(subagent1, subagent1Jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.analyzeSession(mainSession);

      JsonNode combined = result.path("combined");
      JsonNode toolFrequency = combined.path("tool_frequency");

      requireThat(toolFrequency.size(),
        "tool_frequency_size").isEqualTo(3);

      JsonNode readFreq = toolFrequency.get(0);
      requireThat(readFreq.path("tool").asString(),
        "most_frequent_tool").isEqualTo("Read");
      requireThat(readFreq.path("count").asInt(),
        "read_count").isEqualTo(3);

      boolean foundTask = false;
      boolean foundWrite = false;
      for (JsonNode freq : toolFrequency)
      {
        if (freq.path("tool").asString().equals("Task"))
        {
          foundTask = true;
          requireThat(freq.path("count").asInt(),
            "task_count").isEqualTo(1);
        }
        if (freq.path("tool").asString().equals("Write"))
        {
          foundWrite = true;
          requireThat(freq.path("count").asInt(),
            "write_count").isEqualTo(1);
        }
      }
      requireThat(foundTask, "found_Task").isTrue();
      requireThat(foundWrite, "found_Write").isTrue();
    }
    finally
    {
      Files.deleteIfExists(subagent1);
      Files.deleteIfExists(subagentsDir);
      Files.deleteIfExists(mainSession);
      Files.deleteIfExists(tempDir);
    }
  }

  /**
   * Verifies that malformed JSONL lines are skipped with warnings.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void skipsMalformedJsonlLines() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String jsonl =
        assistantMessage("msg1", "tool1", "Read", "") +
        "\n" +
        "{invalid json here}\n" +
        assistantMessage("msg2", "tool2", "Write", "") +
        "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.analyzeSingleAgent(tempFile);

      requireThat(
        result.path("summary").path("total_tool_calls").asInt(),
        "total_tool_calls").isEqualTo(2);
      requireThat(result.path("tool_frequency").size(),
        "tool_frequency_size").isEqualTo(2);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that array content in tool_result is properly joined.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handlesArrayContentInToolResult() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String jsonl =
        "{\"type\":\"tool_result\"," +
        "\"tool_use_id\":\"tool1\"," +
        "\"content\":[{\"text\":\"first part\"}," +
        "{\"text\":\"second part\"}]}\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.analyzeSingleAgent(tempFile);

      JsonNode outputSizes = result.path("output_sizes");
      requireThat(outputSizes.size(),
        "output_sizes_size").isEqualTo(1);
      requireThat(
        outputSizes.get(0).path("output_length").asInt(),
        "output_length").isGreaterThan(10);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that search returns entries containing the keyword with context.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void searchReturnsMatchingEntriesWithContext() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String jsonl =
        assistantMessage("msg1", "tool1", "Read",
          "\"file_path\":\"/test.txt\"") + "\n" +
        toolResult("tool1", "file contents with keyword here") + "\n" +
        assistantMessage("msg2", "tool2", "Write",
          "\"file_path\":\"/out.txt\",\"content\":\"no match here\"") + "\n" +
        toolResult("tool2", "no match") + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.search(tempFile, "keyword", 0);

      requireThat(result.path("matches").size(),
        "matches_size").isGreaterThanOrEqualTo(1);
      boolean foundKeyword = false;
      for (JsonNode match : result.path("matches"))
      {
        String text = match.path("text").asString();
        if (text.contains("keyword"))
          foundKeyword = true;
      }
      requireThat(foundKeyword, "found_keyword").isTrue();
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that search with context N returns surrounding lines from the same entry,
   * including the lines before and after the keyword match.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void searchReturnsContextLines() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      // Build an assistant message with text content mentioning the keyword
      String msgWithKeyword = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg1\"," +
        "\"content\":[{\"type\":\"text\",\"text\":\"line1\\nkeyword found here\\nline3\"}]}}";
      Files.writeString(tempFile, msgWithKeyword + "\n");

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.search(tempFile, "keyword", 1);

      requireThat(result.path("matches").size(),
        "matches_size").isEqualTo(1);
      JsonNode match = result.path("matches").get(0);
      String text = match.path("text").asString();
      requireThat(text, "context_text").contains("line1");
      requireThat(text, "context_text").contains("keyword found here");
      requireThat(text, "context_text").contains("line3");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that search returns an empty matches array when the keyword is not found.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void searchReturnsEmptyWhenKeywordNotFound() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String jsonl = assistantMessage("msg1", "tool1", "Read",
        "\"file_path\":\"/test.txt\"") + "\n" +
        toolResult("tool1", "file contents without the search term") + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.search(tempFile, "nonexistent_keyword_xyz", 0);

      requireThat(result.path("matches").size(),
        "matches_size").isEqualTo(0);
      requireThat(result.path("keyword").asString(),
        "keyword").isEqualTo("nonexistent_keyword_xyz");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that search with an empty keyword matches all entries (since every string contains "").
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void searchWithEmptyKeyword() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String jsonl = assistantMessage("msg1", "tool1", "Read",
        "\"file_path\":\"/test.txt\"") + "\n" +
        assistantMessage("msg2", "tool2", "Write",
          "\"file_path\":\"/out.txt\"") + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.search(tempFile, "", 0);

      // Empty keyword matches every entry — behavior is well-defined as "match all"
      requireThat(result.path("matches").size(),
        "matches_size").isEqualTo(2);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that errors method returns tool_result entries with non-zero exit codes,
   * capturing the exit code and error output correctly.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void errorsReturnsFailedToolResults() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String successResult = "{\"type\":\"tool_result\"," +
        "\"tool_use_id\":\"tool1\"," +
        "\"content\":\"{\\\"exit_code\\\":0,\\\"stdout\\\":\\\"success\\\"}\"}\n";
      String errorResult = "{\"type\":\"tool_result\"," +
        "\"tool_use_id\":\"tool2\"," +
        "\"content\":\"{\\\"exit_code\\\":1,\\\"stderr\\\":\\\"ERROR: something failed\\\"}\"}\n";
      String bashToolUse = assistantMessage("msg1", "tool2", "Bash",
        "\"command\":\"some-command\"") + "\n";
      Files.writeString(tempFile, bashToolUse + successResult + errorResult);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.errors(tempFile);

      requireThat(result.path("errors").size(),
        "errors_size").isEqualTo(1);
      JsonNode firstError = result.path("errors").get(0);
      requireThat(firstError.path("tool_use_id").asString(),
        "tool_use_id").isEqualTo("tool2");
      requireThat(firstError.path("exit_code").asInt(),
        "exit_code").isEqualTo(1);
      requireThat(firstError.path("error_output").asString(),
        "error_output").contains("ERROR: something failed");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that errors method detects error patterns in non-JSON content (pattern-based errors).
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void errorsDetectsPatternBasedErrors() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String buildFailedResult = "{\"type\":\"tool_result\"," +
        "\"tool_use_id\":\"tool1\"," +
        "\"content\":\"Compiling sources...\\nBUILD FAILED\\nTotal time: 2s\"}\n";
      String errorColonResult = "{\"type\":\"tool_result\"," +
        "\"tool_use_id\":\"tool2\"," +
        "\"content\":\"Processing...\\nERROR: file not found\\nAborted\"}\n";
      String successResult = "{\"type\":\"tool_result\"," +
        "\"tool_use_id\":\"tool3\"," +
        "\"content\":\"BUILD SUCCESS\\nTotal time: 1s\"}\n";
      Files.writeString(tempFile, buildFailedResult + errorColonResult + successResult);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.errors(tempFile);

      requireThat(result.path("errors").size(),
        "errors_size").isEqualTo(2);
      requireThat(result.path("errors").get(0).path("tool_use_id").asString(),
        "first_error_id").isEqualTo("tool1");
      requireThat(result.path("errors").get(1).path("tool_use_id").asString(),
        "second_error_id").isEqualTo("tool2");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that errors method returns empty list when no errors exist.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void errorsReturnsEmptyListWhenNoErrors() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String successResult = "{\"type\":\"tool_result\"," +
        "\"tool_use_id\":\"tool1\"," +
        "\"content\":\"success output\"}\n";
      Files.writeString(tempFile, successResult);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.errors(tempFile);

      requireThat(result.path("errors").size(),
        "errors_size").isEqualTo(0);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that file-history returns all tool uses referencing a path pattern.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void fileHistoryTracksReadWriteEditOperations() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String jsonl =
        assistantMessage("msg1", "tool1", "Read",
          "\"file_path\":\"/workspace/config.json\"") + "\n" +
        assistantMessage("msg2", "tool2", "Write",
          "\"file_path\":\"/workspace/config.json\"," +
          "\"content\":\"new content\"") + "\n" +
        assistantMessage("msg3", "tool3", "Read",
          "\"file_path\":\"/workspace/other.txt\"") + "\n" +
        assistantMessage("msg4", "tool4", "Edit",
          "\"file_path\":\"/workspace/config.json\"," +
          "\"old_string\":\"old\",\"new_string\":\"new\"") + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.fileHistory(tempFile, "config.json");

      JsonNode operations = result.path("operations");
      requireThat(operations.size(), "operations_size").isEqualTo(3);

      requireThat(operations.get(0).path("tool").asString(),
        "first_tool").isEqualTo("Read");
      requireThat(operations.get(1).path("tool").asString(),
        "second_tool").isEqualTo("Write");
      requireThat(operations.get(2).path("tool").asString(),
        "third_tool").isEqualTo("Edit");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that file-history returns empty list when no matching operations exist.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void fileHistoryReturnsEmptyWhenNoMatchingFiles() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String jsonl =
        assistantMessage("msg1", "tool1", "Read",
          "\"file_path\":\"/workspace/other.txt\"") + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.fileHistory(tempFile, "config.json");

      requireThat(result.path("operations").size(),
        "operations_size").isEqualTo(0);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that Bash tool uses referencing a file pattern are included in file-history.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void fileHistoryIncludesBashCommandsReferencingFile() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String jsonl =
        assistantMessage("msg1", "tool1", "Bash",
          "\"command\":\"cat /workspace/config.json\"") + "\n" +
        assistantMessage("msg2", "tool2", "Read",
          "\"file_path\":\"/workspace/unrelated.txt\"") + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.fileHistory(tempFile, "config.json");

      requireThat(result.path("operations").size(),
        "operations_size").isEqualTo(1);
      requireThat(result.path("operations").get(0).path("tool").asString(),
        "tool").isEqualTo("Bash");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that file-history correctly matches path patterns containing special characters such as dots.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void fileHistoryWithSpecialCharacters() throws IOException
  {
    Path tempFile = Files.createTempFile("session-", ".jsonl");
    try
    {
      String jsonl =
        assistantMessage("msg1", "tool1", "Read",
          "\"file_path\":\"/workspace/pom.xml\"") + "\n" +
        assistantMessage("msg2", "tool2", "Read",
          "\"file_path\":\"/workspace/build.gradle.kts\"") + "\n" +
        assistantMessage("msg3", "tool3", "Bash",
          "\"command\":\"mvn -f pom.xml test\"") + "\n" +
        assistantMessage("msg4", "tool4", "Read",
          "\"file_path\":\"/workspace/README.md\"") + "\n";
      Files.writeString(tempFile, jsonl);

      SessionAnalyzer analyzer =
        new SessionAnalyzer(new TestJvmScope());
      JsonNode result = analyzer.fileHistory(tempFile, "pom.xml");

      // Should match /workspace/pom.xml (Read) and mvn -f pom.xml test (Bash) — 2 operations
      requireThat(result.path("operations").size(),
        "operations_size").isEqualTo(2);
      requireThat(result.path("operations").get(0).path("tool").asString(),
        "first_tool").isEqualTo("Read");
      requireThat(result.path("operations").get(1).path("tool").asString(),
        "second_tool").isEqualTo("Bash");
      requireThat(result.path("path_pattern").asString(),
        "path_pattern").isEqualTo("pom.xml");
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }
}
