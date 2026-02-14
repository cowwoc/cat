package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.skills.JsonHelper.getStringOrDefault;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.MainJvmScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
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
  private final JsonMapper mapper;

  /**
   * Creates a new session analyzer.
   *
   * @param mapper the JSON mapper for parsing and serialization
   * @throws NullPointerException if {@code mapper} is null
   */
  public SessionAnalyzer(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    this.mapper = mapper;
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Analyzes a session JSONL file and prints the JSON result to stdout.
   *
   * @param args command-line arguments: {@code <session-file>}
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length < 1)
    {
      System.err.println("Usage: SessionAnalyzer <session-file>");
      System.exit(1);
    }
    try (MainJvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      SessionAnalyzer analyzer = new SessionAnalyzer(mapper);
      JsonNode result = analyzer.analyzeSession(Path.of(args[0]));
      System.out.println(mapper.writeValueAsString(result));
    }
    catch (RuntimeException | Error e)
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

    ObjectNode subagentsNode = mapper.createObjectNode();
    List<JsonNode> allAnalyses = new ArrayList<>();
    allAnalyses.add(mainAnalysis);
    ArrayNode warnings = mapper.createArrayNode();

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

    ObjectNode result = mapper.createObjectNode();
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

    ObjectNode result = mapper.createObjectNode();
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
          entries.add(mapper.readTree(line));
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

    ArrayNode result = mapper.createArrayNode();
    frequency.entrySet().stream().
      sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue())).
      forEach(entry ->
      {
        ObjectNode node = mapper.createObjectNode();
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

    ArrayNode result = mapper.createArrayNode();
    toolTokenUsage.entrySet().stream().
      sorted((e1, e2) -> Integer.compare(e2.getValue().inputTokens, e1.getValue().inputTokens)).
      forEach(entry ->
      {
        ObjectNode node = mapper.createObjectNode();
        node.put("tool", entry.getKey());
        node.put("total_input_tokens", entry.getValue().inputTokens);
        node.put("total_output_tokens", entry.getValue().outputTokens);
        node.put("count", entry.getValue().count);
        result.add(node);
      });

    return result;
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
      String contentStr;

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
        contentStr = sb.toString();
      }
      else
        contentStr = content.asString();

      sizes.add(new OutputSize(
        getStringOrDefault(entry, "tool_use_id", ""),
        contentStr.length()));
    }

    sizes.sort((a, b) -> Integer.compare(b.length(), a.length()));

    ArrayNode result = mapper.createArrayNode();
    for (OutputSize size : sizes)
    {
      ObjectNode node = mapper.createObjectNode();
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

    ArrayNode result = mapper.createArrayNode();
    for (CacheCandidate candidate : candidates)
    {
      ObjectNode node = mapper.createObjectNode();
      ObjectNode operation = mapper.createObjectNode();
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
      return mapper.createArrayNode();

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

    ArrayNode result = mapper.createArrayNode();
    for (BatchCandidate batch : batches)
    {
      ObjectNode node = mapper.createObjectNode();
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

    ArrayNode result = mapper.createArrayNode();
    for (ParallelCandidate candidate : candidates)
    {
      ObjectNode node = mapper.createObjectNode();
      node.put("message_id", candidate.messageId());
      ArrayNode toolsArray = mapper.createArrayNode();
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

    ObjectNode summary = mapper.createObjectNode();
    summary.put("total_tool_calls", toolUses.size());
    ArrayNode toolsArray = mapper.createArrayNode();
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
      String contentStr;

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
        contentStr = sb.toString();
      }
      else
        contentStr = content.asString();

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

    ArrayNode toolFreqArray = mapper.createArrayNode();
    toolFrequency.entrySet().stream().
      sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue())).
      forEach(entry ->
      {
        ObjectNode node = mapper.createObjectNode();
        node.put("tool", entry.getKey());
        node.put("count", entry.getValue());
        toolFreqArray.add(node);
      });

    ArrayNode tokenUsageArray = mapper.createArrayNode();
    tokenUsage.entrySet().stream().
      sorted((e1, e2) -> Integer.compare(e2.getValue().inputTokens, e1.getValue().inputTokens)).
      forEach(entry ->
      {
        ObjectNode node = mapper.createObjectNode();
        node.put("tool", entry.getKey());
        node.put("total_input_tokens", entry.getValue().inputTokens);
        node.put("total_output_tokens", entry.getValue().outputTokens);
        node.put("count", entry.getValue().count);
        tokenUsageArray.add(node);
      });

    ArrayNode cacheCandidatesArray = mapper.createArrayNode();
    cacheOps.values().stream().
      filter(c -> c.count > 1).
      sorted((a, b) -> Integer.compare(b.count, a.count)).
      forEach(candidate ->
      {
        ObjectNode node = mapper.createObjectNode();
        ObjectNode operation = mapper.createObjectNode();
        operation.put("name", candidate.name);
        operation.set("input", candidate.input);
        node.set("operation", operation);
        node.put("repeat_count", candidate.count);
        node.put("optimization", "CACHE_CANDIDATE");
        cacheCandidatesArray.add(node);
      });

    List<String> sortedTools = new ArrayList<>(uniqueTools);
    sortedTools.sort(String::compareTo);

    ArrayNode toolsArray = mapper.createArrayNode();
    for (String tool : sortedTools)
      toolsArray.add(tool);

    ObjectNode summaryNode = mapper.createObjectNode();
    summaryNode.put("total_tool_calls", totalToolCalls);
    summaryNode.set("unique_tools", toolsArray);
    summaryNode.put("total_entries", totalEntries);
    summaryNode.put("agent_count", analyses.size());

    ObjectNode combined = mapper.createObjectNode();
    combined.set("tool_frequency", toolFreqArray);
    combined.set("cache_candidates", cacheCandidatesArray);
    combined.set("token_usage", tokenUsageArray);
    combined.set("summary", summaryNode);

    return combined;
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
