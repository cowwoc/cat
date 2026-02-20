/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.skills.JsonHelper.getStringOrDefault;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Analyzes Claude Code session JSONL files for optimization opportunities.
 * <p>
 * Extracts tool usage patterns, identifies batching/caching/parallel candidates,
 * and provides metrics for optimization recommendations.
 */
public final class SessionAnalyzer
{
  private static final int MIN_BATCH_SIZE = 2;
  private static final Pattern AGENT_ID_PATTERN = Pattern.compile("\"agentId\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern ERROR_PATTERN = Pattern.compile(
    "build failed|failed|error:|exception|fatal:",
    Pattern.CASE_INSENSITIVE);
  private final JvmScope scope;

  /**
   * Creates a new session analyzer.
   *
   * @param scope the JVM scope providing JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public SessionAnalyzer(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Subcommands:
   * <ul>
   *   <li>{@code analyze <file>} — full session analysis (default when no subcommand given)</li>
   *   <li>{@code search <file> <keyword> [--context N]} — search for keyword with N lines of context</li>
   *   <li>{@code errors <file>} — list tool_result entries containing error indicators</li>
   *   <li>{@code file-history <file> <path-pattern>} — trace tool uses referencing a path pattern</li>
   * </ul>
   *
   * @param args command-line arguments
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length < 1)
    {
      System.err.println("""
        Usage: SessionAnalyzer <session-file>
               SessionAnalyzer analyze <session-file>
               SessionAnalyzer search <session-file> <keyword> [--context N]
               SessionAnalyzer errors <session-file>
               SessionAnalyzer file-history <session-file> <path-pattern>""");
      System.exit(1);
    }
    try (MainJvmScope scope = new MainJvmScope())
    {
      SessionAnalyzer analyzer = new SessionAnalyzer(scope);
      String firstArg = args[0];
      JsonNode result;
      switch (firstArg)
      {
        case "analyze" ->
        {
          if (args.length < 2)
          {
            System.err.println("Usage: SessionAnalyzer analyze <session-file>");
            System.exit(1);
          }
          result = analyzer.analyzeSession(Path.of(args[1]));
        }
        case "search" ->
        {
          if (args.length < 3)
          {
            System.err.println("Usage: SessionAnalyzer search <session-file> <keyword> [--context N]");
            System.exit(1);
          }
          Path filePath = Path.of(args[1]);
          String keyword = args[2];
          int contextLines = 0;
          for (int i = 3; i < args.length - 1; ++i)
          {
            if (args[i].equals("--context"))
            {
              try
              {
                contextLines = Integer.parseInt(args[i + 1]);
              }
              catch (NumberFormatException e)
              {
                System.err.println("Error: --context requires an integer value, got: " + args[i + 1]);
                System.exit(1);
              }
              break;
            }
          }
          result = analyzer.search(filePath, keyword, contextLines);
        }
        case "errors" ->
        {
          if (args.length < 2)
          {
            System.err.println("Usage: SessionAnalyzer errors <session-file>");
            System.exit(1);
          }
          result = analyzer.errors(Path.of(args[1]));
        }
        case "file-history" ->
        {
          if (args.length < 3)
          {
            System.err.println("Usage: SessionAnalyzer file-history <session-file> <path-pattern>");
            System.exit(1);
          }
          result = analyzer.fileHistory(Path.of(args[1]), args[2]);
        }
        default ->
        {
          // Backward compatibility: treat first arg as session file for analyze
          result = analyzer.analyzeSession(Path.of(firstArg));
        }
      }
      System.out.println(scope.getJsonMapper().writeValueAsString(result));
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(SessionAnalyzer.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }


  /**
   * Analyzes a session JSONL file with subagent discovery and combined analysis.
   * <p>
   * Analyzes the main session and discovers any subagent sessions, providing
   * per-agent and combined metrics.
   *
   * @param filePath path to the session JSONL file
   * @return JSON object containing main, subagents, and combined analysis
   * @throws NullPointerException if filePath is null
   * @throws IOException if file reading or parsing fails
   */
  public JsonNode analyzeSession(Path filePath) throws IOException
  {
    requireThat(filePath, "filePath").isNotNull();

    List<JsonNode> entries = parseJsonl(filePath);
    JsonNode mainAnalysis = analyzeSingleAgent(entries);
    List<Path> subagentPaths = discoverSubagents(entries, filePath);

    ObjectNode subagentsNode = scope.getJsonMapper().createObjectNode();
    List<JsonNode> allAnalyses = new ArrayList<>();
    allAnalyses.add(mainAnalysis);
    ArrayNode warnings = scope.getJsonMapper().createArrayNode();

    for (Path subagentPath : subagentPaths)
    {
      String agentId = subagentPath.getFileName().toString().replace("agent-", "").replace(".jsonl", "");
      try
      {
        JsonNode subagentAnalysis = analyzeSingleAgent(subagentPath);
        subagentsNode.set(agentId, subagentAnalysis);
        allAnalyses.add(subagentAnalysis);
      }
      catch (IOException e)
      {
        warnings.add("Warning: Failed to analyze subagent " + agentId + ": " + e.getMessage());
      }
    }

    JsonNode combined = buildCombinedAnalysis(allAnalyses);

    ObjectNode result = scope.getJsonMapper().createObjectNode();
    result.set("main", mainAnalysis);
    result.set("subagents", subagentsNode);
    result.set("combined", combined);
    if (!warnings.isEmpty())
      result.set("warnings", warnings);

    return result;
  }

  /**
   * Analyzes a single agent's JSONL file.
   * <p>
   * Returns analysis without subagent or combined keys.
   *
   * @param filePath path to the agent's JSONL file
   * @return JSON object containing tool frequency, token usage, output sizes, candidates, and summary
   * @throws NullPointerException if filePath is null
   * @throws IOException if file reading or parsing fails
   */
  public JsonNode analyzeSingleAgent(Path filePath) throws IOException
  {
    requireThat(filePath, "filePath").isNotNull();

    List<JsonNode> entries = parseJsonl(filePath);
    return analyzeSingleAgent(entries);
  }

  /**
   * Analyzes a single agent's entries.
   * <p>
   * Returns analysis without subagent or combined keys.
   *
   * @param entries list of parsed JSONL entries
   * @return JSON object containing tool frequency, token usage, output sizes, candidates, and summary
   * @throws NullPointerException if entries is null
   */
  private JsonNode analyzeSingleAgent(List<JsonNode> entries)
  {
    requireThat(entries, "entries").isNotNull();

    List<ToolUse> toolUses = extractToolUses(entries);

    ObjectNode result = scope.getJsonMapper().createObjectNode();
    result.set("tool_frequency", calculateToolFrequency(toolUses));
    result.set("token_usage", calculateTokenUsage(entries, toolUses));
    result.set("output_sizes", extractOutputSizes(entries));
    result.set("cache_candidates", findCacheCandidates(toolUses));
    result.set("batch_candidates", findBatchCandidates(toolUses));
    result.set("parallel_candidates", findParallelCandidates(toolUses));
    result.set("summary", buildSummary(entries, toolUses));

    return result;
  }

  /**
   * Parses a JSONL file into a list of JSON objects.
   *
   * @param filePath path to the JSONL file
   * @return list of parsed JSON objects
   * @throws NullPointerException if filePath is null
   * @throws IOException if file reading fails
   */
  private List<JsonNode> parseJsonl(Path filePath) throws IOException
  {
    requireThat(filePath, "filePath").isNotNull();

    List<JsonNode> entries = new ArrayList<>();
    List<String> parseWarnings = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(filePath))
    {
      String line;
      int lineNum = 0;
      while (true)
      {
        line = reader.readLine();
        if (line == null)
          break;
        ++lineNum;
        line = line.trim();
        if (line.isEmpty())
          continue;

        try
        {
          entries.add(scope.getJsonMapper().readTree(line));
        }
        catch (JacksonException e)
        {
          parseWarnings.add("Warning: Skipping malformed line " + lineNum + ": " + e.getMessage());
        }
      }
    }
    return entries;
  }

  /**
   * Extracts all tool_use entries from assistant messages.
   *
   * @param entries list of session entries
   * @return list of tool uses
   * @throws NullPointerException if entries is null
   */
  private List<ToolUse> extractToolUses(List<JsonNode> entries)
  {
    requireThat(entries, "entries").isNotNull();

    List<ToolUse> toolUses = new ArrayList<>();
    for (JsonNode entry : entries)
    {
      if (!"assistant".equals(getStringOrDefault(entry, "type", "")))
        continue;

      JsonNode message = entry.path("message");
      JsonNode content = message.path("content");
      if (!content.isArray())
        continue;

      String messageId = getStringOrDefault(message, "id", "");

      for (JsonNode item : content)
      {
        if ("tool_use".equals(getStringOrDefault(item, "type", "")))
        {
          String name = getStringOrDefault(item, "name", "");
          if (name.isEmpty())
            continue;

          toolUses.add(new ToolUse(
            getStringOrDefault(item, "id", ""),
            name,
            item.path("input"),
            messageId));
        }
      }
    }
    return toolUses;
  }

  /**
   * Calculates frequency of each tool type.
   *
   * @param toolUses list of tool uses
   * @return JSON array of tool frequency objects sorted by count descending
   * @throws NullPointerException if toolUses is null
   */
  private ArrayNode calculateToolFrequency(List<ToolUse> toolUses)
  {
    requireThat(toolUses, "toolUses").isNotNull();

    Map<String, Integer> frequency = new HashMap<>();
    for (ToolUse tool : toolUses)
      frequency.merge(tool.name(), 1, Integer::sum);

    ArrayNode result = scope.getJsonMapper().createArrayNode();
    frequency.entrySet().stream().
      sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue())).
      forEach(entry ->
      {
        ObjectNode node = scope.getJsonMapper().createObjectNode();
        node.put("tool", entry.getKey());
        node.put("count", entry.getValue());
        result.add(node);
      });

    return result;
  }

  /**
   * Calculates token usage per tool type.
   *
   * @param entries list of session entries
   * @param toolUses list of tool uses
   * @return JSON array of token usage objects sorted by input tokens descending
   * @throws NullPointerException if any parameter is null
   */
  private ArrayNode calculateTokenUsage(List<JsonNode> entries, List<ToolUse> toolUses)
  {
    requireThat(entries, "entries").isNotNull();
    requireThat(toolUses, "toolUses").isNotNull();

    Map<String, List<String>> messageTools = new HashMap<>();
    for (ToolUse tool : toolUses)
    {
      if (!tool.messageId().isEmpty())
      {
        messageTools.computeIfAbsent(tool.messageId(), k -> new ArrayList<>()).add(tool.name());
      }
    }

    Map<String, TokenStats> toolTokenUsage = new HashMap<>();
    for (JsonNode entry : entries)
    {
      if (!"assistant".equals(getStringOrDefault(entry, "type", "")))
        continue;

      JsonNode message = entry.path("message");
      String messageId = getStringOrDefault(message, "id", "");
      JsonNode usage = message.path("usage");

      if (messageId.isEmpty() || usage.isMissingNode())
        continue;

      List<String> tools = messageTools.getOrDefault(messageId, List.of());
      String primaryTool;
      if (tools.isEmpty())
        primaryTool = "conversation";
      else
        primaryTool = tools.get(0);

      int inputTokens = usage.path("input_tokens").asInt(0);
      int outputTokens = usage.path("output_tokens").asInt(0);

      TokenStats stats = toolTokenUsage.computeIfAbsent(primaryTool, k -> new TokenStats());
      stats.inputTokens += inputTokens;
      stats.outputTokens += outputTokens;
      ++stats.count;
    }

    ArrayNode result = scope.getJsonMapper().createArrayNode();
    toolTokenUsage.entrySet().stream().
      sorted((e1, e2) -> Integer.compare(e2.getValue().inputTokens, e1.getValue().inputTokens)).
      forEach(entry ->
      {
        ObjectNode node = scope.getJsonMapper().createObjectNode();
        node.put("tool", entry.getKey());
        node.put("total_input_tokens", entry.getValue().inputTokens);
        node.put("total_output_tokens", entry.getValue().outputTokens);
        node.put("count", entry.getValue().count);
        result.add(node);
      });

    return result;
  }

  /**
   * Converts a JsonNode content field (which may be an array of text objects or a plain string) to a single string.
   *
   * @param content the content JsonNode from a session entry
   * @return the concatenated string representation of the content
   * @throws NullPointerException if {@code content} is null
   */
  private static String contentToString(JsonNode content)
  {
    requireThat(content, "content").isNotNull();

    if (content.isArray())
    {
      StringBuilder sb = new StringBuilder();
      for (JsonNode item : content)
      {
        String text;
        if (item.isObject())
          text = getStringOrDefault(item, "text", "");
        else
          text = item.asString();
        sb.append(text).append('\n');
      }
      return sb.toString();
    }
    return content.asString();
  }

  /**
   * Extracts output sizes from tool_result entries.
   *
   * @param entries list of session entries
   * @return JSON array of output size objects sorted by length descending
   * @throws NullPointerException if entries is null
   */
  private ArrayNode extractOutputSizes(List<JsonNode> entries)
  {
    requireThat(entries, "entries").isNotNull();

    List<OutputSize> sizes = new ArrayList<>();
    for (JsonNode entry : entries)
    {
      if (!"tool_result".equals(getStringOrDefault(entry, "type", "")))
        continue;

      JsonNode content = entry.path("content");
      String contentStr = contentToString(content);

      sizes.add(new OutputSize(
        getStringOrDefault(entry, "tool_use_id", ""),
        contentStr.length()));
    }

    sizes.sort((a, b) -> Integer.compare(b.length(), a.length()));

    ArrayNode result = scope.getJsonMapper().createArrayNode();
    for (OutputSize size : sizes)
    {
      ObjectNode node = scope.getJsonMapper().createObjectNode();
      node.put("tool_use_id", size.toolUseId());
      node.put("output_length", size.length());
      result.add(node);
    }

    return result;
  }

  /**
   * Finds repeated identical operations (cache candidates).
   *
   * @param toolUses list of tool uses
   * @return JSON array of cache candidate objects sorted by repeat count descending
   * @throws NullPointerException if toolUses is null
   */
  private ArrayNode findCacheCandidates(List<ToolUse> toolUses)
  {
    requireThat(toolUses, "toolUses").isNotNull();

    Map<String, List<ToolUse>> operations = new HashMap<>();
    for (ToolUse tool : toolUses)
    {
      String key = tool.name() + ":" + tool.input().toString();
      operations.computeIfAbsent(key, k -> new ArrayList<>()).add(tool);
    }

    List<CacheCandidate> candidates = new ArrayList<>();
    for (Map.Entry<String, List<ToolUse>> entry : operations.entrySet())
    {
      if (entry.getValue().size() > 1)
      {
        ToolUse first = entry.getValue().get(0);
        candidates.add(new CacheCandidate(first.name(), first.input(), entry.getValue().size()));
      }
    }

    candidates.sort((a, b) -> Integer.compare(b.repeatCount(), a.repeatCount()));

    ArrayNode result = scope.getJsonMapper().createArrayNode();
    for (CacheCandidate candidate : candidates)
    {
      ObjectNode node = scope.getJsonMapper().createObjectNode();
      ObjectNode operation = scope.getJsonMapper().createObjectNode();
      operation.put("name", candidate.name());
      operation.set("input", candidate.input());
      node.set("operation", operation);
      node.put("repeat_count", candidate.repeatCount());
      node.put("optimization", "CACHE_CANDIDATE");
      result.add(node);
    }

    return result;
  }

  /**
   * Finds consecutive similar operations (batch candidates).
   *
   * @param toolUses list of tool uses
   * @return JSON array of batch candidate objects sorted by consecutive count descending
   * @throws NullPointerException if toolUses is null
   */
  private ArrayNode findBatchCandidates(List<ToolUse> toolUses)
  {
    requireThat(toolUses, "toolUses").isNotNull();

    if (toolUses.isEmpty())
      return scope.getJsonMapper().createArrayNode();

    List<BatchCandidate> batches = new ArrayList<>();
    List<ToolUse> currentBatch = new ArrayList<>();
    currentBatch.add(toolUses.get(0));

    for (int i = 1; i < toolUses.size(); ++i)
    {
      ToolUse tool = toolUses.get(i);
      if (tool.name().equals(currentBatch.get(currentBatch.size() - 1).name()))
        currentBatch.add(tool);
      else
      {
        if (currentBatch.size() > MIN_BATCH_SIZE)
          batches.add(new BatchCandidate(currentBatch.get(0).name(), currentBatch.size()));
        currentBatch.clear();
        currentBatch.add(tool);
      }
    }

    if (currentBatch.size() > MIN_BATCH_SIZE)
      batches.add(new BatchCandidate(currentBatch.get(0).name(), currentBatch.size()));

    batches.sort((a, b) -> Integer.compare(b.count(), a.count()));

    ArrayNode result = scope.getJsonMapper().createArrayNode();
    for (BatchCandidate batch : batches)
    {
      ObjectNode node = scope.getJsonMapper().createObjectNode();
      node.put("tool", batch.tool());
      node.put("consecutive_count", batch.count());
      node.put("optimization", "BATCH_CANDIDATE");
      result.add(node);
    }

    return result;
  }

  /**
   * Finds independent operations in same message (parallel candidates).
   *
   * @param toolUses list of tool uses
   * @return JSON array of parallel candidate objects sorted by count descending
   * @throws NullPointerException if toolUses is null
   */
  private ArrayNode findParallelCandidates(List<ToolUse> toolUses)
  {
    requireThat(toolUses, "toolUses").isNotNull();

    Map<String, List<ToolUse>> messageTools = new HashMap<>();
    for (ToolUse tool : toolUses)
    {
      if (!tool.messageId().isEmpty())
      {
        messageTools.computeIfAbsent(tool.messageId(), k -> new ArrayList<>()).add(tool);
      }
    }

    List<ParallelCandidate> candidates = new ArrayList<>();
    for (Map.Entry<String, List<ToolUse>> entry : messageTools.entrySet())
    {
      if (entry.getValue().size() > 1)
      {
        List<String> toolNames = entry.getValue().stream().
          map(ToolUse::name).
          collect(Collectors.toList());
        candidates.add(new ParallelCandidate(entry.getKey(), toolNames, toolNames.size()));
      }
    }

    candidates.sort((a, b) -> Integer.compare(b.count(), a.count()));

    ArrayNode result = scope.getJsonMapper().createArrayNode();
    for (ParallelCandidate candidate : candidates)
    {
      ObjectNode node = scope.getJsonMapper().createObjectNode();
      node.put("message_id", candidate.messageId());
      ArrayNode toolsArray = scope.getJsonMapper().createArrayNode();
      for (String tool : candidate.tools())
        toolsArray.add(tool);
      node.set("parallel_tools", toolsArray);
      node.put("count", candidate.count());
      node.put("optimization", "PARALLEL_CANDIDATE");
      result.add(node);
    }

    return result;
  }

  /**
   * Builds summary statistics for a session.
   *
   * @param entries list of session entries
   * @param toolUses list of tool uses
   * @return JSON object containing summary metrics
   * @throws NullPointerException if any parameter is null
   */
  private ObjectNode buildSummary(List<JsonNode> entries, List<ToolUse> toolUses)
  {
    requireThat(entries, "entries").isNotNull();
    requireThat(toolUses, "toolUses").isNotNull();

    Set<String> uniqueTools = toolUses.stream().
      map(ToolUse::name).
      collect(Collectors.toSet());

    List<String> sortedTools = new ArrayList<>(uniqueTools);
    sortedTools.sort(String::compareTo);

    ObjectNode summary = scope.getJsonMapper().createObjectNode();
    summary.put("total_tool_calls", toolUses.size());
    ArrayNode toolsArray = scope.getJsonMapper().createArrayNode();
    for (String tool : sortedTools)
      toolsArray.add(tool);
    summary.set("unique_tools", toolsArray);
    summary.put("total_entries", entries.size());

    return summary;
  }

  /**
   * Discovers subagent JSONL files from parent session.
   * <p>
   * Parses the session JSONL for Task tool_result entries containing agentId,
   * then resolves subagent file paths. Returns only paths that exist on disk.
   *
   * @param entries list of parsed JSONL entries from parent session
   * @param filePath path to parent session JSONL file (used to resolve subagent directory)
   * @return list of discovered subagent file paths (only existing files)
   * @throws NullPointerException if any parameter is null
   */
  private List<Path> discoverSubagents(List<JsonNode> entries, Path filePath)
  {
    requireThat(entries, "entries").isNotNull();
    requireThat(filePath, "filePath").isNotNull();

    Set<String> agentIds = new HashSet<>();

    for (JsonNode entry : entries)
    {
      if (!"tool_result".equals(getStringOrDefault(entry, "type", "")))
        continue;

      JsonNode content = entry.path("content");
      String contentStr = contentToString(content);

      if (contentStr.contains("\"agentId\":"))
      {
        Matcher matcher = AGENT_ID_PATTERN.matcher(contentStr);
        while (matcher.find())
        {
          agentIds.add(matcher.group(1));
        }
      }
    }

    Path sessionDir = filePath.getParent();
    if (sessionDir == null)
      sessionDir = Path.of(".");
    Path subagentDir = sessionDir.resolve("subagents");

    List<Path> subagentPaths = new ArrayList<>();
    for (String agentId : agentIds)
    {
      Path subagentPath = subagentDir.resolve("agent-" + agentId + ".jsonl");
      if (Files.exists(subagentPath))
        subagentPaths.add(subagentPath);
    }

    subagentPaths.sort(Path::compareTo);
    return subagentPaths;
  }

  /**
   * Builds combined analysis from multiple agent analyses.
   *
   * @param analyses list of per-agent analyses
   * @return JSON object containing combined metrics
   * @throws NullPointerException if analyses is null
   */
  private ObjectNode buildCombinedAnalysis(List<JsonNode> analyses)
  {
    requireThat(analyses, "analyses").isNotNull();

    Map<String, Integer> toolFrequency = new HashMap<>();
    Map<String, CombinedTokenStats> tokenUsage = new HashMap<>();
    Map<String, CacheOccurrence> cacheOps = new LinkedHashMap<>();
    int totalToolCalls = 0;
    int totalEntries = 0;
    Set<String> uniqueTools = new HashSet<>();

    for (JsonNode analysis : analyses)
    {
      for (JsonNode item : analysis.path("tool_frequency"))
      {
        String tool = getStringOrDefault(item, "tool", "");
        int count = item.path("count").asInt(0);
        toolFrequency.merge(tool, count, Integer::sum);
      }

      for (JsonNode item : analysis.path("token_usage"))
      {
        String tool = getStringOrDefault(item, "tool", "");
        int inputTokens = item.path("total_input_tokens").asInt(0);
        int outputTokens = item.path("total_output_tokens").asInt(0);
        int count = item.path("count").asInt(0);

        CombinedTokenStats stats = tokenUsage.computeIfAbsent(tool, k -> new CombinedTokenStats());
        stats.inputTokens += inputTokens;
        stats.outputTokens += outputTokens;
        stats.count += count;
      }

      for (JsonNode item : analysis.path("cache_candidates"))
      {
        JsonNode operation = item.path("operation");
        String name = getStringOrDefault(operation, "name", "");
        JsonNode input = operation.path("input");
        String key = name + ":" + input.toString();
        int repeatCount = item.path("repeat_count").asInt(0);

        CacheOccurrence occurrence = cacheOps.computeIfAbsent(key,
          k -> new CacheOccurrence(name, input, 0));
        occurrence.count += repeatCount;
      }

      JsonNode summary = analysis.path("summary");
      totalToolCalls += summary.path("total_tool_calls").asInt(0);
      totalEntries += summary.path("total_entries").asInt(0);

      for (JsonNode tool : summary.path("unique_tools"))
      {
        uniqueTools.add(tool.asString());
      }
    }

    ArrayNode toolFreqArray = scope.getJsonMapper().createArrayNode();
    toolFrequency.entrySet().stream().
      sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue())).
      forEach(entry ->
      {
        ObjectNode node = scope.getJsonMapper().createObjectNode();
        node.put("tool", entry.getKey());
        node.put("count", entry.getValue());
        toolFreqArray.add(node);
      });

    ArrayNode tokenUsageArray = scope.getJsonMapper().createArrayNode();
    tokenUsage.entrySet().stream().
      sorted((e1, e2) -> Integer.compare(e2.getValue().inputTokens, e1.getValue().inputTokens)).
      forEach(entry ->
      {
        ObjectNode node = scope.getJsonMapper().createObjectNode();
        node.put("tool", entry.getKey());
        node.put("total_input_tokens", entry.getValue().inputTokens);
        node.put("total_output_tokens", entry.getValue().outputTokens);
        node.put("count", entry.getValue().count);
        tokenUsageArray.add(node);
      });

    ArrayNode cacheCandidatesArray = scope.getJsonMapper().createArrayNode();
    cacheOps.values().stream().
      filter(c -> c.count > 1).
      sorted((a, b) -> Integer.compare(b.count, a.count)).
      forEach(candidate ->
      {
        ObjectNode node = scope.getJsonMapper().createObjectNode();
        ObjectNode operation = scope.getJsonMapper().createObjectNode();
        operation.put("name", candidate.name);
        operation.set("input", candidate.input);
        node.set("operation", operation);
        node.put("repeat_count", candidate.count);
        node.put("optimization", "CACHE_CANDIDATE");
        cacheCandidatesArray.add(node);
      });

    List<String> sortedTools = new ArrayList<>(uniqueTools);
    sortedTools.sort(String::compareTo);

    ArrayNode toolsArray = scope.getJsonMapper().createArrayNode();
    for (String tool : sortedTools)
      toolsArray.add(tool);

    ObjectNode summaryNode = scope.getJsonMapper().createObjectNode();
    summaryNode.put("total_tool_calls", totalToolCalls);
    summaryNode.set("unique_tools", toolsArray);
    summaryNode.put("total_entries", totalEntries);
    summaryNode.put("agent_count", analyses.size());

    ObjectNode combined = scope.getJsonMapper().createObjectNode();
    combined.set("tool_frequency", toolFreqArray);
    combined.set("cache_candidates", cacheCandidatesArray);
    combined.set("token_usage", tokenUsageArray);
    combined.set("summary", summaryNode);

    return combined;
  }

  /**
   * Searches a session JSONL file for entries containing a keyword.
   * <p>
   * For each matching entry, extracts the relevant text block containing the keyword with
   * up to {@code contextLines} surrounding lines from the same message.
   *
   * @param filePath path to the session JSONL file
   * @param keyword the keyword to search for (case-sensitive)
   * @param contextLines number of surrounding lines to include before and after the match
   * @return JSON object with a "matches" array, each element containing "type", "text", and "entry_index"
   * @throws NullPointerException if {@code filePath} or {@code keyword} are null
   * @throws IOException if file reading fails
   */
  public JsonNode search(Path filePath, String keyword, int contextLines) throws IOException
  {
    requireThat(filePath, "filePath").isNotNull();
    requireThat(keyword, "keyword").isNotNull();

    List<JsonNode> entries = parseJsonl(filePath);
    ArrayNode matches = scope.getJsonMapper().createArrayNode();

    for (int entryIndex = 0; entryIndex < entries.size(); ++entryIndex)
    {
      JsonNode entry = entries.get(entryIndex);
      String entryText = extractTextContent(entry);
      if (!entryText.contains(keyword))
        continue;

      String[] lines = entryText.split("\n", -1);
      LinkedHashSet<String> contextBlock = new LinkedHashSet<>();
      for (int lineIndex = 0; lineIndex < lines.length; ++lineIndex)
      {
        if (lines[lineIndex].contains(keyword))
        {
          int start = Math.max(0, lineIndex - contextLines);
          int end = Math.min(lines.length, lineIndex + contextLines + 1);
          for (int i = start; i < end; ++i)
            contextBlock.add(lines[i]);
        }
      }

      ObjectNode match = scope.getJsonMapper().createObjectNode();
      String type = getStringOrDefault(entry, "type", "unknown");
      match.put("type", type);
      match.put("entry_index", entryIndex);
      match.put("text", String.join("\n", contextBlock));
      matches.add(match);
    }

    ObjectNode result = scope.getJsonMapper().createObjectNode();
    result.set("matches", matches);
    result.put("keyword", keyword);
    result.put("total_entries_scanned", entries.size());
    return result;
  }

  /**
   * Extracts readable text content from a session entry for search purposes.
   *
   * @param entry a JSONL entry
   * @return concatenated text content from the entry
   */
  private String extractTextContent(JsonNode entry)
  {
    StringBuilder sb = new StringBuilder();
    // For assistant messages, extract text and tool_use content
    JsonNode message = entry.path("message");
    if (!message.isMissingNode())
    {
      JsonNode content = message.path("content");
      if (content.isArray())
      {
        for (JsonNode item : content)
        {
          String itemType = getStringOrDefault(item, "type", "");
          if (itemType.equals("text"))
            sb.append(getStringOrDefault(item, "text", "")).append('\n');
          else if (itemType.equals("tool_use"))
            sb.append(item.toString()).append('\n');
        }
      }
      else if (content.isString())
        sb.append(content.asString()).append('\n');
    }
    // For tool_result entries, extract content
    JsonNode content = entry.path("content");
    if (!content.isMissingNode())
    {
      if (content.isArray())
      {
        for (JsonNode item : content)
        {
          if (item.isObject())
            sb.append(getStringOrDefault(item, "text", "")).append('\n');
          else
            sb.append(item.asString()).append('\n');
        }
      }
      else if (content.isString())
        sb.append(content.asString()).append('\n');
      else if (!content.isMissingNode())
        sb.append(content.toString()).append('\n');
    }
    // Fall back to full entry string if nothing extracted
    if (sb.isEmpty())
      sb.append(entry.toString());
    return sb.toString();
  }

  /**
   * Scans a session JSONL file for tool_result entries containing error indicators.
   * <p>
   * Detects errors via non-zero exit codes in JSON content, or error keyword patterns
   * in the output text.
   *
   * @param filePath path to the session JSONL file
   * @return JSON object with an "errors" array, each element containing "tool_use_id",
   *   "exit_code", "error_output", and "entry_index"
   * @throws NullPointerException if {@code filePath} is null
   * @throws IOException if file reading fails
   */
  public JsonNode errors(Path filePath) throws IOException
  {
    requireThat(filePath, "filePath").isNotNull();

    List<JsonNode> entries = parseJsonl(filePath);
    ArrayNode errors = scope.getJsonMapper().createArrayNode();

    for (int entryIndex = 0; entryIndex < entries.size(); ++entryIndex)
    {
      JsonNode entry = entries.get(entryIndex);
      if (!"tool_result".equals(getStringOrDefault(entry, "type", "")))
        continue;

      String toolUseId = getStringOrDefault(entry, "tool_use_id", "");
      JsonNode content = entry.path("content");
      String contentStr = contentToString(content);

      int exitCode = 0;
      String errorOutput = "";
      boolean isError = false;

      // Try to parse content as JSON to extract exit code — only when content looks like JSON
      if (contentStr.contains("exit_code") || contentStr.contains("exitCode"))
      try
      {
        JsonNode contentJson = scope.getJsonMapper().readTree(contentStr);
        JsonNode exitCodeNode = contentJson.path("exit_code");
        if (!exitCodeNode.isMissingNode())
          exitCode = exitCodeNode.asInt(0);
        JsonNode exitCodeCamel = contentJson.path("exitCode");
        if (!exitCodeCamel.isMissingNode() && exitCode == 0)
          exitCode = exitCodeCamel.asInt(0);

        if (exitCode != 0)
        {
          isError = true;
          String stderr = getStringOrDefault(contentJson, "stderr", "");
          String stdout = getStringOrDefault(contentJson, "stdout", "");
          if (stderr.isEmpty())
            errorOutput = stdout;
          else
            errorOutput = stderr;
        }
      }
      catch (JacksonException _)
      {
        // Content is not JSON — check for error patterns in raw text
      }

      if (!isError && ERROR_PATTERN.matcher(contentStr).find())
      {
        isError = true;
        errorOutput = contentStr;
      }

      if (isError)
      {
        ObjectNode errorNode = scope.getJsonMapper().createObjectNode();
        errorNode.put("tool_use_id", toolUseId);
        errorNode.put("exit_code", exitCode);
        String effectiveErrorOutput;
        if (errorOutput.isEmpty())
          effectiveErrorOutput = contentStr;
        else
          effectiveErrorOutput = errorOutput;
        errorNode.put("error_output", effectiveErrorOutput);
        errorNode.put("entry_index", entryIndex);
        errors.add(errorNode);
      }
    }

    ObjectNode result = scope.getJsonMapper().createObjectNode();
    result.set("errors", errors);
    result.put("total_entries_scanned", entries.size());
    return result;
  }

  /**
   * Traces all Read, Write, Edit, and Bash tool uses referencing a file path pattern.
   * <p>
   * Returns operations in chronological order as they appear in the session file.
   *
   * @param filePath path to the session JSONL file
   * @param pathPattern substring pattern to match against file paths and command text
   * @return JSON object with an "operations" array, each element containing "tool", "input",
   *   "message_id", and "tool_use_id"
   * @throws NullPointerException if {@code filePath} or {@code pathPattern} are null
   * @throws IOException if file reading fails
   */
  public JsonNode fileHistory(Path filePath, String pathPattern) throws IOException
  {
    requireThat(filePath, "filePath").isNotNull();
    requireThat(pathPattern, "pathPattern").isNotNull();

    List<JsonNode> entries = parseJsonl(filePath);
    ArrayNode operations = scope.getJsonMapper().createArrayNode();

    for (JsonNode entry : entries)
    {
      if (!"assistant".equals(getStringOrDefault(entry, "type", "")))
        continue;

      JsonNode message = entry.path("message");
      JsonNode content = message.path("content");
      if (!content.isArray())
        continue;

      String messageId = getStringOrDefault(message, "id", "");

      for (JsonNode item : content)
      {
        if (!"tool_use".equals(getStringOrDefault(item, "type", "")))
          continue;

        String toolName = getStringOrDefault(item, "name", "");
        JsonNode input = item.path("input");
        String toolUseId = getStringOrDefault(item, "id", "");

        boolean matches = false;
        switch (toolName)
        {
          case "Read", "Write", "Edit" ->
          {
            String itemFilePath = getStringOrDefault(input, "file_path", "");
            if (itemFilePath.contains(pathPattern))
              matches = true;
          }
          case "Bash" ->
          {
            String command = getStringOrDefault(input, "command", "");
            if (command.contains(pathPattern))
              matches = true;
          }
          default ->
          {
            // Other tools are not file operations — skip
          }
        }

        if (matches)
        {
          ObjectNode operation = scope.getJsonMapper().createObjectNode();
          operation.put("tool", toolName);
          operation.set("input", input);
          operation.put("message_id", messageId);
          operation.put("tool_use_id", toolUseId);
          operations.add(operation);
        }
      }
    }

    ObjectNode result = scope.getJsonMapper().createObjectNode();
    result.set("operations", operations);
    result.put("path_pattern", pathPattern);
    result.put("total_entries_scanned", entries.size());
    return result;
  }

  /**
   * Represents a tool use from the session.
   *
   * @param id tool use ID
   * @param name tool name
   * @param input tool input parameters
   * @param messageId message ID containing this tool use
   */
  private record ToolUse(String id, String name, JsonNode input, String messageId)
  {
    /**
     * Creates a new tool use record.
     *
     * @param id tool use ID
     * @param name tool name
     * @param input tool input parameters
     * @param messageId message ID containing this tool use
     * @throws NullPointerException if any parameter is null
     */
    private ToolUse
    {
      requireThat(id, "id").isNotNull();
      requireThat(name, "name").isNotNull();
      requireThat(input, "input").isNotNull();
      requireThat(messageId, "messageId").isNotNull();
    }
  }

  /**
   * Represents output size information.
   *
   * @param toolUseId tool use ID
   * @param length output length in characters
   */
  private record OutputSize(String toolUseId, int length)
  {
    /**
     * Creates a new output size record.
     *
     * @param toolUseId tool use ID
     * @param length output length in characters
     * @throws NullPointerException if toolUseId is null
     */
    private OutputSize
    {
      requireThat(toolUseId, "toolUseId").isNotNull();
    }
  }

  /**
   * Represents a cache candidate operation.
   *
   * @param name tool name
   * @param input tool input parameters
   * @param repeatCount number of times this operation was repeated
   */
  private record CacheCandidate(String name, JsonNode input, int repeatCount)
  {
    /**
     * Creates a new cache candidate record.
     *
     * @param name tool name
     * @param input tool input parameters
     * @param repeatCount number of times this operation was repeated
     * @throws NullPointerException if name or input is null
     */
    private CacheCandidate
    {
      requireThat(name, "name").isNotNull();
      requireThat(input, "input").isNotNull();
    }
  }

  /**
   * Represents a batch candidate operation.
   *
   * @param tool tool name
   * @param count consecutive occurrence count
   */
  private record BatchCandidate(String tool, int count)
  {
    /**
     * Creates a new batch candidate record.
     *
     * @param tool tool name
     * @param count consecutive occurrence count
     * @throws NullPointerException if tool is null
     */
    private BatchCandidate
    {
      requireThat(tool, "tool").isNotNull();
    }
  }

  /**
   * Represents a parallel candidate operation.
   *
   * @param messageId message ID
   * @param tools list of tool names in this message
   * @param count number of tools
   */
  private record ParallelCandidate(String messageId, List<String> tools, int count)
  {
    /**
     * Creates a new parallel candidate record.
     *
     * @param messageId message ID
     * @param tools list of tool names in this message
     * @param count number of tools
     * @throws NullPointerException if any parameter is null
     */
    private ParallelCandidate
    {
      requireThat(messageId, "messageId").isNotNull();
      requireThat(tools, "tools").isNotNull();
    }
  }

  /**
   * Tracks token usage statistics for a tool.
   */
  private static final class TokenStats
  {
    private int inputTokens;
    private int outputTokens;
    private int count;
  }

  /**
   * Tracks combined token usage statistics across multiple agents.
   */
  private static final class CombinedTokenStats
  {
    private int inputTokens;
    private int outputTokens;
    private int count;
  }

  /**
   * Tracks cache operation occurrences for combined analysis.
   */
  private static final class CacheOccurrence
  {
    private final String name;
    private final JsonNode input;
    private int count;

    /**
     * Creates a new cache occurrence tracker.
     *
     * @param name tool name
     * @param input tool input parameters
     * @param count initial count
     * @throws NullPointerException if name or input is null
     */
    private CacheOccurrence(String name, JsonNode input, int count)
    {
      requireThat(name, "name").isNotNull();
      requireThat(input, "input").isNotNull();
      this.name = name;
      this.input = input;
      this.count = count;
    }
  }
}
