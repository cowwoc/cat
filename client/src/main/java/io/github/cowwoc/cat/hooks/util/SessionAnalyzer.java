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
import java.util.Locale;
import java.util.SequencedSet;
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
  private static final Pattern SAFE_AGENT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
  private static final Pattern ERROR_PATTERN = Pattern.compile(
    "build failed|failed|error:|exception|fatal:",
    Pattern.CASE_INSENSITIVE);
  private final Logger log = LoggerFactory.getLogger(SessionAnalyzer.class);
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
   * Resolves a session ID to a JSONL file path.
   * <p>
   * Accepts either:
   * <ul>
   *   <li>A session UUID: {@code b0078f4d-efa0-47a5-8182-03970ffd737a}</li>
   *   <li>A subagent path: {@code b0078f4d-efa0-47a5-8182-03970ffd737a/subagents/agent-ad630cb}</li>
   * </ul>
   *
   * @param sessionId the session ID or subagent path
   * @return the resolved JSONL file path
   * @throws IllegalArgumentException if the resolved path does not exist
   */
  private Path resolveSessionPath(String sessionId)
  {
    Path resolved = scope.getSessionBasePath().resolve(sessionId + ".jsonl");
    if (!Files.exists(resolved))
    {
      throw new IllegalArgumentException("Session file not found: " + resolved +
        "\nSession ID: " + sessionId);
    }
    return resolved;
  }

  /**
   * Extracts tool call sequences around keyword matches.
   * <p>
   * For each keyword, finds all tool_use/tool_result pairs that match the keyword,
   * and includes N tool pairs before and after each match for context.
   *
   * @param filePath      path to the session JSONL file
   * @param keywords      list of keywords to search for in tool results
   * @param contextWindow number of tool pairs to include before/after each match
   * @return JSON object with a "tool_call_sequences" property; keyed by keyword, values are arrays of matches
   * @throws NullPointerException if filePath or keywords are null
   * @throws IOException          if file reading fails
   */
  public JsonNode toolCallSequences(Path filePath, List<String> keywords, int contextWindow)
    throws IOException
  {
    return toolCallSequences(filePath, keywords, contextWindow, Integer.MAX_VALUE);
  }

  /**
   * Extracts tool call sequences around keyword matches, capped at {@code maxMatches} per keyword.
   * <p>
   * For each keyword, finds tool_use/tool_result pairs that match the keyword (up to {@code maxMatches}),
   * and includes N tool pairs before and after each match for context.
   *
   * @param filePath      path to the session JSONL file
   * @param keywords      list of keywords to search for in tool results
   * @param contextWindow number of tool pairs to include before/after each match
   * @param maxMatches    maximum number of matches to include per keyword; use {@link Integer#MAX_VALUE} for unlimited
   * @return JSON object with a "tool_call_sequences" property; keyed by keyword, values are arrays of matches
   * @throws NullPointerException if filePath or keywords are null
   * @throws IOException          if file reading fails
   */
  public JsonNode toolCallSequences(Path filePath, List<String> keywords, int contextWindow,
    int maxMatches) throws IOException
  {
    requireThat(filePath, "filePath").isNotNull();
    requireThat(keywords, "keywords").isNotNull();

    List<JsonNode> entries = parseJsonl(filePath);
    return toolCallSequences(entries, keywords, contextWindow, maxMatches);
  }

  /**
   * Extracts tool call sequences around keyword matches, capped at {@code maxMatches} per keyword.
   * <p>
   * Accepts a pre-parsed list of entries to avoid re-reading the file when the caller already holds the entries.
   *
   * @param entries       list of pre-parsed JSONL entries
   * @param keywords      list of keywords to search for in tool results
   * @param contextWindow number of tool pairs to include before/after each match
   * @param maxMatches    maximum number of matches to include per keyword; use {@link Integer#MAX_VALUE} for unlimited
   * @return JSON object with a "tool_call_sequences" property; keyed by keyword, values are arrays of matches
   * @throws NullPointerException if entries or keywords are null
   */
  JsonNode toolCallSequences(List<JsonNode> entries, List<String> keywords, int contextWindow,
    int maxMatches)
  {
    requireThat(entries, "entries").isNotNull();
    requireThat(keywords, "keywords").isNotNull();

    // Build a list of all tool use/result pairs with their indices
    List<ToolPair> toolPairs = extractToolPairs(entries);

    // For each keyword, find matches and extract context
    ObjectNode sequences = scope.getJsonMapper().createObjectNode();

    for (String keyword : keywords)
    {
      ArrayNode matches = scope.getJsonMapper().createArrayNode();

      for (int i = 0; i < toolPairs.size(); ++i)
      {
        if (matches.size() >= maxMatches)
          break;

        ToolPair pair = toolPairs.get(i);
        String resultText = contentToString(pair.toolResult.path("content"));

        if (resultText.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
        {
          // Found a match — extract context before and after
          ObjectNode match = scope.getJsonMapper().createObjectNode();
          match.put("match_index", i);

          // Context before
          ArrayNode contextBefore = scope.getJsonMapper().createArrayNode();
          int startContext = Math.max(0, i - contextWindow);
          for (int j = startContext; j < i; ++j)
          {
            contextBefore.add(toolPairToJson(toolPairs.get(j)));
          }
          match.set("context_before", contextBefore);

          // Matched pair
          match.set("matched_pair", toolPairToJson(pair));

          // Context after
          ArrayNode contextAfter = scope.getJsonMapper().createArrayNode();
          int endContext = Math.min(toolPairs.size(), i + contextWindow + 1);
          for (int j = i + 1; j < endContext; ++j)
          {
            contextAfter.add(toolPairToJson(toolPairs.get(j)));
          }
          match.set("context_after", contextAfter);

          matches.add(match);
        }
      }

      sequences.set(keyword, matches);
    }

    ObjectNode result = scope.getJsonMapper().createObjectNode();
    result.set("tool_call_sequences", sequences);
    result.put("context_window", contextWindow);
    result.put("total_tool_pairs", toolPairs.size());
    return result;
  }

  /**
   * Extracts the mistake timeline — the sequence of assistant turns and tool calls
   * from the last user message to the first error point.
   *
   * @param filePath path to the session JSONL file
   * @return JSON object with "mistake_timeline" array and metadata
   * @throws NullPointerException if filePath is null
   * @throws IOException          if file reading fails
   */
  public JsonNode mistakeTimeline(Path filePath) throws IOException
  {
    return mistakeTimeline(filePath, Integer.MAX_VALUE);
  }

  /**
   * Extracts the mistake timeline — the sequence of assistant turns and tool calls
   * from the last user message to the first error point, capped at {@code maxEvents} entries.
   *
   * @param filePath  path to the session JSONL file
   * @param maxEvents maximum number of timeline events to include; use {@link Integer#MAX_VALUE} for unlimited
   * @return JSON object with "mistake_timeline" array and metadata
   * @throws NullPointerException if filePath is null
   * @throws IOException          if file reading fails
   */
  public JsonNode mistakeTimeline(Path filePath, int maxEvents) throws IOException
  {
    requireThat(filePath, "filePath").isNotNull();

    List<JsonNode> entries = parseJsonl(filePath);
    return mistakeTimeline(entries, maxEvents);
  }

  /**
   * Extracts the mistake timeline from a pre-parsed list of entries, capped at {@code maxEvents} entries.
   * <p>
   * Accepts a pre-parsed list of entries to avoid re-reading the file when the caller already holds the entries.
   *
   * @param entries   list of pre-parsed JSONL entries
   * @param maxEvents maximum number of timeline events to include; use {@link Integer#MAX_VALUE} for unlimited
   * @return JSON object with "mistake_timeline" array and metadata
   * @throws NullPointerException if entries is null
   */
  JsonNode mistakeTimeline(List<JsonNode> entries, int maxEvents)
  {
    requireThat(entries, "entries").isNotNull();

    // Find the last user message
    int lastUserMessageIndex = -1;
    for (int i = entries.size() - 1; i >= 0; --i)
    {
      if ("user".equals(getStringOrDefault(entries.get(i), "type", "")))
      {
        lastUserMessageIndex = i;
        break;
      }
    }

    // Find the first error after the last user message
    int errorIndex = -1;
    if (lastUserMessageIndex >= 0)
    {
      outer:
      for (int i = lastUserMessageIndex + 1; i < entries.size(); ++i)
      {
        for (JsonNode resultItem : extractToolResults(entries.get(i)))
        {
          String content = contentToString(resultItem.path("content"));
          if (isErrorContent(content))
          {
            errorIndex = i;
            break outer;
          }
        }
      }
    }

    // Extract timeline from last user message to error (or empty if no error)
    ArrayNode timeline = scope.getJsonMapper().createArrayNode();

    if (lastUserMessageIndex >= 0 && errorIndex > lastUserMessageIndex)
    {
      for (int i = lastUserMessageIndex + 1; i <= errorIndex && timeline.size() < maxEvents; ++i)
      {
        JsonNode entry = entries.get(i);
        String entryType = getStringOrDefault(entry, "type", "");

        if ("assistant".equals(entryType))
        {
          // Extract tool_use entries from assistant messages
          JsonNode message = entry.path("message");
          JsonNode content = message.path("content");
          if (content.isArray())
          {
            for (JsonNode item : content)
            {
              if (timeline.size() >= maxEvents)
                break;
              if ("tool_use".equals(getStringOrDefault(item, "type", "")))
              {
                ObjectNode toolUseEvent = scope.getJsonMapper().createObjectNode();
                toolUseEvent.put("type", "tool_use");
                toolUseEvent.put("name", getStringOrDefault(item, "name", ""));
                toolUseEvent.put("id", getStringOrDefault(item, "id", ""));
                toolUseEvent.set("input", item.path("input"));
                timeline.add(toolUseEvent);
              }
              else if ("text".equals(getStringOrDefault(item, "type", "")))
              {
                ObjectNode textEvent = scope.getJsonMapper().createObjectNode();
                textEvent.put("type", "assistant_text");
                textEvent.put("text", getStringOrDefault(item, "text", ""));
                timeline.add(textEvent);
              }
            }
          }
        }
        else if ("tool".equals(entryType))
        {
          // Include tool results (wrapped inside {type:"tool", content:[{type:"tool_result",...}]})
          for (JsonNode resultItem : extractToolResults(entry))
          {
            if (timeline.size() >= maxEvents)
              break;
            ObjectNode resultEvent = scope.getJsonMapper().createObjectNode();
            resultEvent.put("type", "tool_result");
            resultEvent.put("tool_use_id", getStringOrDefault(resultItem, "tool_use_id", ""));
            String resultContent = contentToString(resultItem.path("content"));
            resultEvent.put("content", resultContent);
            timeline.add(resultEvent);
          }
        }
      }
    }

    ObjectNode result = scope.getJsonMapper().createObjectNode();
    result.set("mistake_timeline", timeline);
    if (lastUserMessageIndex >= 0)
      result.put("last_user_message_index", lastUserMessageIndex);
    if (errorIndex >= 0)
      result.put("error_entry_index", errorIndex);
    result.put("total_entries_scanned", entries.size());

    return result;
  }

  /**
   * Converts a tool pair to JSON representation.
   *
   * @param pair the tool pair
   * @return JSON object with tool_use and tool_result
   */
  private ObjectNode toolPairToJson(ToolPair pair)
  {
    ObjectNode node = scope.getJsonMapper().createObjectNode();
    ObjectNode toolUseNode = scope.getJsonMapper().createObjectNode();
    toolUseNode.put("id", pair.toolId);
    toolUseNode.put("name", pair.name);
    toolUseNode.set("input", pair.toolInput);
    node.set("tool_use", toolUseNode);

    ObjectNode toolResultNode = scope.getJsonMapper().createObjectNode();
    toolResultNode.put("tool_use_id", pair.toolId);
    String resultContent = contentToString(pair.toolResult.path("content"));
    toolResultNode.put("content", resultContent);
    node.set("tool_result", toolResultNode);

    return node;
  }

  /**
   * Extracts all tool use/result pairs from entries.
   *
   * @param entries list of JSONL entries
   * @return list of ToolPair objects
   */
  private List<ToolPair> extractToolPairs(List<JsonNode> entries)
  {
    // Build a map of tool_use_id -> tool_use info
    Map<String, ToolUseInfo> toolUses = new HashMap<>();
    for (JsonNode entry : entries)
    {
      if (!"assistant".equals(getStringOrDefault(entry, "type", "")))
        continue;

      JsonNode message = entry.path("message");
      JsonNode content = message.path("content");
      if (!content.isArray())
        continue;

      for (JsonNode item : content)
      {
        if ("tool_use".equals(getStringOrDefault(item, "type", "")))
        {
          String toolId = getStringOrDefault(item, "id", "");
          String toolName = getStringOrDefault(item, "name", "");
          JsonNode input = item.path("input");
          toolUses.put(toolId, new ToolUseInfo(toolId, toolName, input));
        }
      }
    }

    // Build pairs from tool_result entries (wrapped inside {type:"tool", content:[...]})
    List<ToolPair> pairs = new ArrayList<>();
    for (JsonNode entry : entries)
    {
      for (JsonNode resultItem : extractToolResults(entry))
      {
        String toolUseId = getStringOrDefault(resultItem, "tool_use_id", "");
        ToolUseInfo toolUse = toolUses.get(toolUseId);
        if (toolUse != null)
        {
          pairs.add(new ToolPair(toolUseId, toolUse.name, toolUse.input, resultItem));
        }
      }
    }

    return pairs;
  }

  /**
   * Checks if content represents an error.
   *
   * @param content the content string
   * @return true if the content contains error indicators
   */
  private boolean isErrorContent(String content)
  {
    if (content.isEmpty())
      return false;

    // Check for JSON exit_code (both escaped and unescaped forms)
    if (content.contains("exit_code") || content.contains("exitCode"))
    {
      try
      {
        JsonNode json = scope.getJsonMapper().readTree(content);
        int exitCode = json.path("exit_code").asInt(json.path("exitCode").asInt(0));
        if (exitCode != 0)
          return true;
      }
      catch (JacksonException _)
      {
        // Not JSON, check pattern
      }
    }

    // Check for error patterns
    return ERROR_PATTERN.matcher(content).find();
  }

  /**
   * Represents a tool use/result pair.
   *
   * @param toolId the tool use ID
   * @param name the tool name
   * @param toolInput the tool input parameters
   * @param toolResult the tool result entry
   */
  private record ToolPair(String toolId, String name, JsonNode toolInput, JsonNode toolResult)
  {
    private ToolPair
    {
      requireThat(toolId, "toolId").isNotNull();
      requireThat(name, "name").isNotNull();
      requireThat(toolInput, "toolInput").isNotNull();
      requireThat(toolResult, "toolResult").isNotNull();
    }
  }

  /**
   * Represents tool use information.
   *
   * @param toolId the tool use ID
   * @param name the tool name
   * @param input the tool input parameters
   */
  private record ToolUseInfo(String toolId, String name, JsonNode input)
  {
    private ToolUseInfo
    {
      requireThat(toolId, "toolId").isNotNull();
      requireThat(name, "name").isNotNull();
      requireThat(input, "input").isNotNull();
    }
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Subcommands:
   * <ul>
   *   <li>{@code analyze <session-id>} — full session analysis (default when no subcommand given)</li>
   *   <li>{@code search <session-id> <pattern> [--context N] [--regex]} — search for pattern; use
   *     {@code --regex} to treat the pattern as a Java regular expression (supports alternation like
   *     {@code alpha|beta} and inline flags like {@code (?i)} for case-insensitive matching);
   *     without {@code --regex} the pattern is matched as a literal string</li>
   *   <li>{@code errors <session-id>} — list tool_result entries containing error indicators</li>
   *   <li>{@code file-history <session-id> <path-pattern>} — trace tool uses referencing a path pattern</li>
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
        Usage: SessionAnalyzer <session-id>
               SessionAnalyzer analyze <session-id>
               SessionAnalyzer search <session-id> <pattern> [--context N] [--regex]
               SessionAnalyzer errors <session-id>
               SessionAnalyzer file-history <session-id> <path-pattern>""");
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
            System.err.println("Usage: SessionAnalyzer analyze <session-id>");
            System.exit(1);
          }
          result = analyzer.analyzeSession(analyzer.resolveSessionPath(args[1]));
        }
        case "search" ->
        {
          if (args.length < 3)
          {
            System.err.println(
              "Usage: SessionAnalyzer search <session-id> <pattern> [--context N] [--regex]");
            System.exit(1);
          }
          result = runSearchCommand(analyzer, args);
        }
        case "errors" ->
        {
          if (args.length < 2)
          {
            System.err.println("Usage: SessionAnalyzer errors <session-id>");
            System.exit(1);
          }
          result = analyzer.errors(analyzer.resolveSessionPath(args[1]));
        }
        case "file-history" ->
        {
          if (args.length < 3)
          {
            System.err.println("Usage: SessionAnalyzer file-history <session-id> <path-pattern>");
            System.exit(1);
          }
          result = analyzer.fileHistory(analyzer.resolveSessionPath(args[1]), args[2]);
        }
        default ->
        {
          // Backward compatibility: treat first arg as session ID for analyze
          result = analyzer.analyzeSession(analyzer.resolveSessionPath(firstArg));
        }
      }
      System.out.println(scope.getJsonMapper().writeValueAsString(result));
    }
    catch (RuntimeException | AssertionError e)
    {
      LoggerFactory.getLogger(SessionAnalyzer.class).error("Unexpected error", e);
      throw e;
    }
  }

  /**
   * Handles the {@code search} subcommand from the CLI, parsing flags and delegating to
   * {@link #search(Path, String, int, boolean)}.
   *
   * @param analyzer the analyzer instance to use
   * @param args     the full CLI argument array (args[0] is "search", args[1] is session-id,
   *                 args[2] is pattern)
   * @return the search result JSON
   * @throws IOException if the session file cannot be read
   */
  @SuppressWarnings("PMD.DoNotTerminateVM")
  private static JsonNode runSearchCommand(SessionAnalyzer analyzer, String[] args) throws IOException
  {
    Path filePath = analyzer.resolveSessionPath(args[1]);
    String pattern = args[2];
    int contextLines = 0;
    boolean useRegex = false;
    for (int i = 3; i < args.length; ++i)
    {
      if (args[i].equals("--context") && i + 1 < args.length)
      {
        try
        {
          contextLines = Integer.parseInt(args[i + 1]);
          ++i;
        }
        catch (NumberFormatException _)
        {
          System.err.println("Error: --context requires an integer value, got: " + args[i + 1]);
          System.exit(1);
        }
      }
      else if (args[i].equals("--regex"))
        useRegex = true;
    }
    try
    {
      return analyzer.search(filePath, pattern, contextLines, useRegex);
    }
    catch (IllegalArgumentException e)
    {
      System.err.println("Error: " + e.getMessage());
      System.exit(1);
      return null;
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
          log.warn("Skipping malformed line {} in {}: {}", lineNum, filePath, e.getMessage());
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
   * Extracts tool_result items from a session entry.
   * <p>
   * Real Claude JSONL wraps tool results as: {@code {type:"tool", content:[{type:"tool_result",...}]}}.
   * Returns the inner {@code tool_result} items from such entries, or an empty list if the entry is not a
   * {@code tool} entry.
   *
   * @param entry a JSONL entry
   * @return list of tool_result inner nodes; empty if the entry is not a tool entry or has no tool_result items
   * @throws NullPointerException if {@code entry} is null
   */
  private static List<JsonNode> extractToolResults(JsonNode entry)
  {
    requireThat(entry, "entry").isNotNull();

    if (!"tool".equals(getStringOrDefault(entry, "type", "")))
      return List.of();

    List<JsonNode> results = new ArrayList<>();
    JsonNode content = entry.path("content");
    if (content.isArray())
    {
      for (JsonNode item : content)
      {
        if ("tool_result".equals(getStringOrDefault(item, "type", "")))
          results.add(item);
      }
    }
    return results;
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
      for (JsonNode resultItem : extractToolResults(entry))
      {
        JsonNode content = resultItem.path("content");
        String contentStr = contentToString(content);
        sizes.add(new OutputSize(
          getStringOrDefault(resultItem, "tool_use_id", ""),
          contentStr.length()));
      }
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
    throws IOException
  {
    requireThat(entries, "entries").isNotNull();
    requireThat(filePath, "filePath").isNotNull();

    Path sessionDir = filePath.getParent();
    if (sessionDir == null)
      sessionDir = Path.of(".");
    Path subagentDir = sessionDir.resolve("subagents");

    // Phase 1: Filesystem scan — find all agent-*.jsonl files in the subagents directory,
    // excluding compaction artifacts (agent-acompact-*.jsonl).
    Set<Path> subagentPaths = new LinkedHashSet<>();
    if (Files.isDirectory(subagentDir))
    {
      try (java.nio.file.DirectoryStream<Path> stream =
        Files.newDirectoryStream(subagentDir, "agent-*.jsonl"))
      {
        for (Path p : stream)
        {
          String name = p.getFileName().toString();
          if (!name.startsWith("agent-acompact-"))
            subagentPaths.add(p);
        }
      }
    }

    // Phase 2: AgentId parse — finds refs in non-compacted sessions (retained for completeness).
    for (JsonNode entry : entries)
    {
      for (JsonNode resultItem : extractToolResults(entry))
      {
        JsonNode content = resultItem.path("content");
        String contentStr = contentToString(content);

        if (contentStr.contains("\"agentId\":"))
        {
          Matcher matcher = AGENT_ID_PATTERN.matcher(contentStr);
          while (matcher.find())
          {
            String agentId = matcher.group(1);
            if (!SAFE_AGENT_ID_PATTERN.matcher(agentId).matches())
            {
              log.warn("Skipping agentId with unsafe characters: '{}'", agentId);
              continue;
            }
            Path subagentPath = subagentDir.resolve("agent-" + agentId + ".jsonl");
            if (Files.exists(subagentPath))
              subagentPaths.add(subagentPath);
          }
        }
      }
    }

    List<Path> result = new ArrayList<>(subagentPaths);
    result.sort(Path::compareTo);
    return result;
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
    Map<String, TokenStats> tokenUsage = new HashMap<>();
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

        TokenStats stats = tokenUsage.computeIfAbsent(tool, k -> new TokenStats());
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
   * Searches a session JSONL file for entries containing a keyword (literal match).
   * <p>
   * For each matching entry, extracts the relevant text block containing the keyword with
   * up to {@code contextLines} surrounding lines from the same message.
   *
   * @param filePath path to the session JSONL file
   * @param keyword the keyword to search for (case-sensitive, literal match)
   * @param contextLines number of surrounding lines to include before and after the match
   * @return JSON object with a "matches" array, each element containing "type", "text", and "entry_index"
   * @throws NullPointerException if {@code filePath} or {@code keyword} are null
   * @throws IOException if file reading fails
   */
  public JsonNode search(Path filePath, String keyword, int contextLines) throws IOException
  {
    return search(filePath, keyword, contextLines, false);
  }

  /**
   * Searches a session JSONL file for entries matching a pattern.
   * <p>
   * For each matching entry, extracts the relevant text block containing the match with
   * up to {@code contextLines} surrounding lines from the same message.
   * <p>
   * When {@code useRegex} is {@code true}, the pattern is compiled as a Java regular expression.
   * Inline flags such as {@code (?i)} for case-insensitive matching are supported.
   * If the pattern is not a valid regular expression, an {@link IllegalArgumentException} is thrown
   * with a message that includes the invalid pattern and the regex syntax error.
   * <p>
   * When {@code useRegex} is {@code false}, the pattern is treated as a literal string
   * (case-sensitive substring match), identical to {@link #search(Path, String, int)}.
   *
   * @param filePath  path to the session JSONL file
   * @param pattern   the pattern to search for; a literal keyword or a regex depending on {@code useRegex}
   * @param contextLines number of surrounding lines to include before and after the match
   * @param useRegex  {@code true} to compile {@code pattern} as a Java regex; {@code false} for literal match
   * @return JSON object with a "matches" array (each element has "type", "text", "entry_index") and a
   *   "pattern" field containing the original pattern string
   * @throws NullPointerException     if {@code filePath} or {@code pattern} are null
   * @throws IllegalArgumentException if {@code useRegex} is {@code true} and {@code pattern} is not a valid regex
   * @throws IOException              if file reading fails
   */
  public JsonNode search(Path filePath, String pattern, int contextLines, boolean useRegex)
    throws IOException
  {
    requireThat(filePath, "filePath").isNotNull();
    requireThat(pattern, "pattern").isNotNull();

    final Pattern compiledPattern;
    if (useRegex)
    {
      try
      {
        compiledPattern = Pattern.compile(pattern);
      }
      catch (java.util.regex.PatternSyntaxException e)
      {
        throw new IllegalArgumentException(
          "Invalid regex pattern: '" + pattern + "'. " + e.getMessage(), e);
      }
    }
    else
      compiledPattern = null;

    List<JsonNode> entries = parseJsonl(filePath);
    ArrayNode matches = scope.getJsonMapper().createArrayNode();

    for (int entryIndex = 0; entryIndex < entries.size(); ++entryIndex)
    {
      JsonNode entry = entries.get(entryIndex);
      String entryText = extractTextContent(entry);

      boolean entryMatches;
      if (useRegex)
        entryMatches = compiledPattern.matcher(entryText).find();
      else
        entryMatches = entryText.contains(pattern);
      if (!entryMatches)
        continue;

      String[] lines = entryText.split("\n", -1);
      SequencedSet<String> contextBlock = new LinkedHashSet<>();
      for (int lineIndex = 0; lineIndex < lines.length; ++lineIndex)
      {
        boolean lineMatches;
        if (useRegex)
          lineMatches = compiledPattern.matcher(lines[lineIndex]).find();
        else
          lineMatches = lines[lineIndex].contains(pattern);
        if (lineMatches)
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
    result.put("pattern", pattern);
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
    // For tool entries, extract content from wrapped tool_result items
    for (JsonNode resultItem : extractToolResults(entry))
    {
      String resultText = contentToString(resultItem.path("content"));
      if (!resultText.isEmpty())
        sb.append(resultText).append('\n');
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
      for (JsonNode resultItem : extractToolResults(entry))
      {
        String toolUseId = getStringOrDefault(resultItem, "tool_use_id", "");
        JsonNode content = resultItem.path("content");
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
