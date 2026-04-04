/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.Strings.block;
import static io.github.cowwoc.cat.hooks.skills.JsonHelper.getStringOrDefault;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.SequencedSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * Analyzes Claude Code session JSONL files for optimization opportunities.
 * <p>
 * Extracts tool usage patterns, identifies batching/caching/parallel candidates,
 * and provides metrics for optimization recommendations.
 */
public final class SessionAnalyzer
{
  private static final int MIN_BATCH_SIZE = 2;
  private static final int MIN_SCRIPT_EXTRACTION_OCCURRENCES = 2;
  private static final int MIN_SCRIPT_EXTRACTION_LENGTH = 2;
  private static final Set<String> CAT_PHASE_SKILLS = Set.of(
    "work-prepare", "work-implement", "work-review", "work-merge");
  private static final Pattern AGENT_ID_PATTERN = Pattern.compile("\"agentId\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern SAFE_AGENT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
  private static final Pattern ERROR_PATTERN = Pattern.compile(
    "build failed|failed|error:|exception|fatal:",
    Pattern.CASE_INSENSITIVE);
  /**
   * Hint message included in search results when no matches are found,
   * explaining that additionalContext from hook events is not stored in JSONL logs.
   */
  private static final String ADDITIONAL_CONTEXT_HINT =
    "No matches found. Note: additionalContext from hook events (e.g., SubagentStart) is injected at the API " +
    "level and is NOT stored in JSONL session logs — session-analyzer searches cannot find this content.";
  private final Logger log = LoggerFactory.getLogger(SessionAnalyzer.class);
  private final ClaudeTool scope;

  /**
   * Creates a new session analyzer.
   *
   * @param scope the Claude tool scope providing JSON mapper and session paths
   * @throws NullPointerException if {@code scope} is null
   */
  public SessionAnalyzer(ClaudeTool scope)
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
  public Path resolveSessionPath(String sessionId)
  {
    Path basePath = scope.getClaudeSessionsPath();
    Path resolved = basePath.resolve(sessionId + ".jsonl");
    if (!resolved.normalize().startsWith(basePath.normalize()))
    {
      throw new IllegalArgumentException(
        "Blocked: resolved path escapes the sessions directory (path traversal detected). " +
          "sessionId=\"" + sessionId + "\", resolved=\"" + resolved.normalize() +
          "\", sessionsDir=\"" + basePath.normalize() + "\"");
    }
    if (Files.exists(resolved))
      return resolved;
    throw new IllegalArgumentException("Session file not found: " + resolved +
      "\nSession ID: " + sessionId +
      "\nSearched in: " + basePath);
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

    // Check for JSON exit_code
    if (content.contains("exit_code"))
    {
      try
      {
        JsonNode json = scope.getJsonMapper().readTree(content);
        int exitCode = json.path("exit_code").asInt(0);
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
   *
   * @param args command-line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException | IOException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        LoggerFactory.getLogger(SessionAnalyzer.class).error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the session analysis command.
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
   * @param scope the scope providing access to session paths and shared services
   * @param args  command-line arguments
   * @param out   the output stream to write to
   * @throws NullPointerException     if any of {@code scope}, {@code args}, or {@code out} are null
   * @throws IllegalArgumentException if the arguments are invalid
   * @throws IOException              if the operation fails
   */
  public static void run(ClaudeTool scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    if (args.length < 1)
    {
      throw new IllegalArgumentException("""
        Usage: SessionAnalyzer <session-id>
               SessionAnalyzer analyze <session-id>
               SessionAnalyzer search <session-id> <pattern> [--context N] [--regex]
               SessionAnalyzer errors <session-id>
               SessionAnalyzer file-history <session-id> <path-pattern>""");
    }

    SessionAnalyzer analyzer = new SessionAnalyzer(scope);
    String firstArg = args[0];
    JsonNode result;
    switch (firstArg)
    {
      case "analyze" ->
      {
        if (args.length < 2)
          throw new IllegalArgumentException("Usage: SessionAnalyzer analyze <session-id>");
        result = analyzer.analyzeSession(analyzer.resolveSessionPath(args[1]));
      }
      case "search" ->
      {
        if (args.length < 3)
        {
          throw new IllegalArgumentException(
            "Usage: SessionAnalyzer search <session-id> <pattern> [--context N] [--regex]");
        }
        result = runSearchCommand(analyzer, args);
      }
      case "errors" ->
      {
        if (args.length < 2)
          throw new IllegalArgumentException("Usage: SessionAnalyzer errors <session-id>");
        result = analyzer.errors(analyzer.resolveSessionPath(args[1]));
      }
      case "file-history" ->
      {
        if (args.length < 3)
        {
          throw new IllegalArgumentException(
            "Usage: SessionAnalyzer file-history <session-id> <path-pattern>");
        }
        result = analyzer.fileHistory(analyzer.resolveSessionPath(args[1]), args[2]);
      }
      default ->
      {
        // No subcommand given — treat first arg as session ID for analyze
        result = analyzer.analyzeSession(analyzer.resolveSessionPath(firstArg));
      }
    }
    out.println(scope.getJsonMapper().writeValueAsString(result));
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
          throw new IllegalArgumentException(
            "--context requires an integer value, got: " + args[i + 1]);
        }
      }
      else if (args[i].equals("--regex"))
        useRegex = true;
    }
    return analyzer.search(filePath, pattern, contextLines, useRegex);
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
        ((ObjectNode) subagentAnalysis).remove("timing");
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
    // Move timing from mainAnalysis to the top level so callers can access
    // timing.session_elapsed_seconds directly without duplication
    JsonNode mainTiming = mainAnalysis.path("timing");
    if (!mainTiming.isMissingNode())
    {
      ((ObjectNode) mainAnalysis).remove("timing");
      result.set("timing", mainTiming);
    }
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
    result.set("pipeline_candidates", findPipelineCandidates(toolUses));
    result.set("script_extraction_candidates", findScriptExtractionCandidates(toolUses));
    result.set("summary", buildSummary(entries, toolUses));

    Optional<JsonNode> timing = extractTiming(entries);
    timing.ifPresent(t -> result.set("timing", t));

    return result;
  }

  /**
   * Extracts timing information from session entries.
   * <p>
   * Parses ISO 8601 {@code timestamp} fields from each entry to compute:
   * <ul>
   *   <li>{@code session_elapsed_seconds} — total session duration from first to last timestamp</li>
   *   <li>{@code tools_elapsed} — per-tool call count and total elapsed time across all entries</li>
   *   <li>{@code phases} — CAT workflow phases detected via Skill invocations, each with its own
   *     per-tool breakdown</li>
   * </ul>
   * <p>
   * Returns empty when fewer than two distinct timestamps are found.
   * <p>
   * Tool elapsed time is the interval between consecutive timestamped assistant entries.
   * The last tool call in the session has no following entry and contributes zero elapsed time.
   *
   * @param entries list of parsed JSONL entries
   * @return an {@link Optional} containing the timing JSON node, or empty if no usable timestamps
   * @throws NullPointerException if entries is null
   */
  private Optional<JsonNode> extractTiming(List<JsonNode> entries)
  {
    requireThat(entries, "entries").isNotNull();

    List<TimedTool> timedTools = collectTimedTools(entries);

    if (timedTools.size() < 2)
      return Optional.empty();

    Instant firstTimestamp = timedTools.get(0).timestamp();
    Instant lastTimestamp = timedTools.get(timedTools.size() - 1).timestamp();
    if (firstTimestamp.equals(lastTimestamp))
      return Optional.empty();

    double sessionElapsed = secondsBetween(firstTimestamp, lastTimestamp);

    ArrayNode toolsElapsedArray = buildToolsElapsedArray(timedTools);
    ArrayNode phasesArray = buildPhaseBreakdown(timedTools);

    ObjectNode timingNode = scope.getJsonMapper().createObjectNode();
    timingNode.put("session_elapsed_seconds", roundToMillis(sessionElapsed));
    timingNode.set("tools_elapsed", toolsElapsedArray);
    if (!phasesArray.isEmpty())
      timingNode.set("phases", phasesArray);

    return Optional.of(timingNode);
  }

  /**
   * Collects timestamped tool events from session entries in chronological order.
   * <p>
   * Only assistant entries carrying a valid ISO 8601 {@code timestamp} field contribute events.
   * Entries without timestamps are skipped.
   *
   * @param entries list of parsed JSONL entries
   * @return list of timed tool events in order of appearance
   */
  private List<TimedTool> collectTimedTools(List<JsonNode> entries)
  {
    List<TimedTool> timedTools = new ArrayList<>();
    for (JsonNode entry : entries)
    {
      String tsStr = getStringOrDefault(entry, "timestamp", "");
      if (tsStr.isEmpty())
        continue;

      Instant ts;
      try
      {
        ts = Instant.parse(tsStr);
      }
      catch (DateTimeParseException _)
      {
        continue;
      }

      if (!"assistant".equals(getStringOrDefault(entry, "type", "")))
        continue;

      JsonNode message = entry.path("message");
      JsonNode content = message.path("content");
      if (!content.isArray())
        continue;

      for (JsonNode item : content)
      {
        if (!"tool_use".equals(getStringOrDefault(item, "type", "")))
          continue;

        String toolName = getStringOrDefault(item, "name", "");
        if (toolName.isEmpty())
          continue;

        String phaseName = null;
        if ("Skill".equals(toolName))
        {
          String skill = getStringOrDefault(item.path("input"), "skill", "");
          phaseName = extractPhaseName(skill);
        }

        timedTools.add(new TimedTool(ts, toolName, phaseName));
      }
    }
    return timedTools;
  }

  /**
   * Builds a JSON array of per-tool elapsed time aggregates across all timed tool events.
   * <p>
   * Each entry records the tool name, total call count, and total elapsed seconds.
   * Elapsed time for each event is the interval to the next event; the last event contributes zero.
   *
   * @param timedTools list of timed tool events in chronological order
   * @return JSON array sorted in order of first appearance
   */
  private ArrayNode buildToolsElapsedArray(List<TimedTool> timedTools)
  {
    Map<String, ToolTimingStats> toolStats = new LinkedHashMap<>();
    for (int i = 0; i < timedTools.size(); ++i)
    {
      TimedTool tool = timedTools.get(i);
      double elapsed = 0.0;
      if (i + 1 < timedTools.size())
        elapsed = secondsBetween(tool.timestamp(), timedTools.get(i + 1).timestamp());
      toolStats.computeIfAbsent(tool.toolName(), k -> new ToolTimingStats()).add(elapsed);
    }

    ArrayNode result = scope.getJsonMapper().createArrayNode();
    toolStats.forEach((name, stats) -> result.add(toolStatsToNode(name, stats)));
    return result;
  }

  /**
   * Builds a JSON object representing per-tool timing statistics.
   *
   * @param toolName the name of the tool
   * @param stats    the accumulated timing statistics for the tool
   * @return a JSON object with {@code tool}, {@code call_count}, and {@code elapsed_seconds} fields
   */
  private ObjectNode toolStatsToNode(String toolName, ToolTimingStats stats)
  {
    ObjectNode node = scope.getJsonMapper().createObjectNode();
    node.put("tool", toolName);
    node.put("call_count", stats.count);
    node.put("elapsed_seconds", roundToMillis(stats.totalSeconds));
    return node;
  }

  /**
   * Builds a JSON array of CAT workflow phase breakdowns.
   * <p>
   * Phases are delimited by phase-marker Skill invocations. Tool calls between two phase markers
   * belong to the earlier phase. Tool calls before the first phase marker are not included in any
   * phase. Each phase records its name, elapsed time (phase-marker timestamp to next phase marker
   * or last event), and per-tool breakdown within the phase.
   *
   * @param timedTools list of timed tool events in chronological order
   * @return JSON array of phase objects; empty if no phase markers are present
   */
  private ArrayNode buildPhaseBreakdown(List<TimedTool> timedTools)
  {
    ArrayNode phasesArray = scope.getJsonMapper().createArrayNode();

    // Find all phase marker indices
    List<Integer> markerIndices = new ArrayList<>();
    for (int i = 0; i < timedTools.size(); ++i)
    {
      if (timedTools.get(i).phaseName() != null)
        markerIndices.add(i);
    }

    if (markerIndices.isEmpty())
      return phasesArray;

    for (int m = 0; m < markerIndices.size(); ++m)
    {
      int phaseStart = markerIndices.get(m);
      int phaseEnd;
      if (m + 1 < markerIndices.size())
        phaseEnd = markerIndices.get(m + 1);
      else
        phaseEnd = timedTools.size();

      TimedTool marker = timedTools.get(phaseStart);
      List<TimedTool> phaseTools = timedTools.subList(phaseStart + 1, phaseEnd);
      TimedTool sentinel;
      if (phaseEnd < timedTools.size())
        sentinel = timedTools.get(phaseEnd);
      else
        sentinel = null;

      phasesArray.add(buildPhaseNode(marker, phaseTools, sentinel));
    }

    return phasesArray;
  }

  /**
   * Builds a single phase JSON node from its marker, tool list, and optional sentinel.
   * <p>
   * Phase elapsed time is computed from the marker timestamp to the sentinel timestamp (when
   * present) or to the last tool in the phase (when the phase is the last one in the session).
   * The sentinel is the next phase's marker and is used only for elapsed calculation, not included
   * in the tool breakdown.
   *
   * @param marker     the phase-marker Skill invocation that opened this phase
   * @param phaseTools tool calls belonging to this phase (excludes the marker itself)
   * @param sentinel   the next phase's marker used as a timing boundary, or {@code null} if this
   *                   is the last phase
   * @return JSON object with {@code name}, {@code elapsed_seconds}, and {@code tools} fields
   */
  private ObjectNode buildPhaseNode(TimedTool marker, List<TimedTool> phaseTools, TimedTool sentinel)
  {
    // Phase elapsed: from marker timestamp to sentinel or last tool in phase
    double phaseElapsed = 0.0;
    if (sentinel != null)
      phaseElapsed = secondsBetween(marker.timestamp(), sentinel.timestamp());
    else if (!phaseTools.isEmpty())
      phaseElapsed = secondsBetween(marker.timestamp(), phaseTools.get(phaseTools.size() - 1).timestamp());

    // Build elapsed list: phaseTools followed by sentinel (if present) for delta calculation
    List<TimedTool> forElapsed = new ArrayList<>(phaseTools);
    if (sentinel != null)
      forElapsed.add(sentinel);

    Map<String, ToolTimingStats> phaseToolStats = new LinkedHashMap<>();
    for (int i = 0; i < phaseTools.size(); ++i)
    {
      TimedTool tool = phaseTools.get(i);
      double elapsed = 0.0;
      if (i + 1 < forElapsed.size())
        elapsed = secondsBetween(tool.timestamp(), forElapsed.get(i + 1).timestamp());
      phaseToolStats.computeIfAbsent(tool.toolName(), k -> new ToolTimingStats()).add(elapsed);
    }

    ArrayNode phaseToolsArray = scope.getJsonMapper().createArrayNode();
    phaseToolStats.forEach((name, stats) -> phaseToolsArray.add(toolStatsToNode(name, stats)));

    ObjectNode phaseNode = scope.getJsonMapper().createObjectNode();
    phaseNode.put("name", marker.phaseName());
    phaseNode.put("elapsed_seconds", roundToMillis(phaseElapsed));
    phaseNode.set("tools", phaseToolsArray);
    return phaseNode;
  }

  /**
   * Extracts the CAT phase name from a skill identifier.
   * <p>
   * Returns a short phase name (e.g., {@code "work-prepare"}) for recognized CAT workflow skills,
   * or {@code null} if the skill is not a phase marker.
   *
   * @param skill the skill identifier (e.g., {@code "cat:work-prepare-agent"})
   * @return the phase name, or {@code null} if not a phase marker
   */
  private static String extractPhaseName(String skill)
  {
    for (String phase : CAT_PHASE_SKILLS)
    {
      if (skill.contains(phase))
        return phase;
    }
    return null;
  }

  /**
   * Returns the number of seconds elapsed between two instants.
   *
   * @param from the earlier instant
   * @param to   the later instant
   * @return elapsed seconds as a double
   */
  private static double secondsBetween(Instant from, Instant to)
  {
    return Duration.between(from, to).toMillis() / 1000.0;
  }

  /**
   * Rounds a seconds value to three decimal places (millisecond precision).
   *
   * @param seconds the value to round
   * @return the value rounded to millisecond precision
   */
  private static double roundToMillis(double seconds)
  {
    return Math.round(seconds * 1000.0) / 1000.0;
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
    // Read file as bytes, accumulating each line into a ByteArrayOutputStream and parsing via
    // readTree(byte[]) instead of readLine() + readTree(String). This avoids the 2x memory
    // overhead of String (1 char = 2 bytes) while still supporting per-line error recovery.
    try (InputStream input = new BufferedInputStream(Files.newInputStream(filePath)))
    {
      ByteArrayOutputStream lineBytes = new ByteArrayOutputStream(4096);
      int lineNum = 0;
      int b = input.read();
      while (b != -1)
      {
        if (b == '\n')
        {
          ++lineNum;
          parseAndAddJsonLine(lineBytes, filePath, lineNum, entries);
          lineBytes.reset();
        }
        else
          lineBytes.write(b);
        b = input.read();
      }
      // Parse any final line that has no trailing newline
      if (lineBytes.size() > 0)
      {
        ++lineNum;
        parseAndAddJsonLine(lineBytes, filePath, lineNum, entries);
      }
    }
    return entries;
  }

  /**
   * Parses one JSONL line from a byte buffer and appends the result to {@code entries}.
   * <p>
   * Skips blank lines and logs a warning for malformed JSON without aborting the caller's loop.
   *
   * @param lineBytes accumulated bytes for this line (may be empty)
   * @param filePath  source file path, used only for log messages
   * @param lineNum   1-based line number, used only for log messages
   * @param entries   list to append successfully parsed nodes to
   */
  private void parseAndAddJsonLine(ByteArrayOutputStream lineBytes, Path filePath, int lineNum,
    List<JsonNode> entries)
  {
    byte[] bytes = lineBytes.toByteArray();
    // Trim whitespace manually to avoid creating a String
    int start = 0;
    int end = bytes.length;
    while (start < end && bytes[start] <= ' ')
      ++start;
    while (end > start && bytes[end - 1] <= ' ')
      --end;
    if (start == end)
      return;
    try
    {
      JsonNode node = scope.getJsonMapper().readTree(bytes, start, end - start);
      if (node != null && !node.isNull() && !node.isMissingNode())
        entries.add(node);
    }
    catch (JacksonException e)
    {
      log.warn("Skipping malformed line {} in {}: {}", lineNum, filePath, e.getMessage());
    }
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
            messageId,
            item.path("input").toString()));
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

    List<Map.Entry<String, Integer>> entries = new ArrayList<>(frequency.entrySet());
    entries.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));
    ArrayNode result = scope.getJsonMapper().createArrayNode();
    for (Map.Entry<String, Integer> entry : entries)
    {
      ObjectNode node = scope.getJsonMapper().createObjectNode();
      node.put("tool", entry.getKey());
      node.put("count", entry.getValue());
      result.add(node);
    }

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

    List<Map.Entry<String, TokenStats>> tokenEntries = new ArrayList<>(toolTokenUsage.entrySet());
    tokenEntries.sort((e1, e2) -> Integer.compare(e2.getValue().inputTokens, e1.getValue().inputTokens));
    ArrayNode result = scope.getJsonMapper().createArrayNode();
    for (Map.Entry<String, TokenStats> entry : tokenEntries)
    {
      ObjectNode node = scope.getJsonMapper().createObjectNode();
      node.put("tool", entry.getKey());
      node.put("total_input_tokens", entry.getValue().inputTokens);
      node.put("total_output_tokens", entry.getValue().outputTokens);
      node.put("count", entry.getValue().count);
      result.add(node);
    }

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
      String key = tool.name() + ":" + tool.inputString();
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
          toList();
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
   * Finds dependent sequential tool chains where a later tool's input references a prior tool's output
   * (pipeline candidates). A pipeline is a sequence of tool calls where each tool's result feeds the next.
   * <p>
   * Detection heuristic: consecutive tool calls where tool B's input JSON contains tool A's tool ID,
   * indicating that B consumes A's output.
   *
   * @param toolUses list of tool uses
   * @return JSON array of pipeline candidate objects sorted by chain length descending
   * @throws NullPointerException if toolUses is null
   */
  private ArrayNode findPipelineCandidates(List<ToolUse> toolUses)
  {
    requireThat(toolUses, "toolUses").isNotNull();

    if (toolUses.size() < 2)
      return scope.getJsonMapper().createArrayNode();

    List<PipelineCandidate> candidates = new ArrayList<>();
    List<ToolUse> currentChain = new ArrayList<>();
    currentChain.add(toolUses.get(0));

    for (int i = 1; i < toolUses.size(); ++i)
    {
      ToolUse previous = toolUses.get(i - 1);
      ToolUse current = toolUses.get(i);

      // Check if current tool's input references the previous tool's ID (output dependency)
      String inputText = current.input().toString();
      boolean dependsOnPrevious = !previous.id().isEmpty() && inputText.contains(previous.id());

      if (dependsOnPrevious)
        currentChain.add(current);
      else
      {
        if (currentChain.size() >= 2)
        {
          List<String> toolNames = currentChain.stream().map(ToolUse::name).toList();
          candidates.add(new PipelineCandidate(toolNames, currentChain.size()));
        }
        currentChain.clear();
        currentChain.add(current);
      }
    }

    if (currentChain.size() >= 2)
    {
      List<String> toolNames = currentChain.stream().map(ToolUse::name).toList();
      candidates.add(new PipelineCandidate(toolNames, currentChain.size()));
    }

    candidates.sort((a, b) -> Integer.compare(b.chainLength(), a.chainLength()));

    ArrayNode result = scope.getJsonMapper().createArrayNode();
    for (PipelineCandidate candidate : candidates)
    {
      ObjectNode node = scope.getJsonMapper().createObjectNode();
      ArrayNode toolsArray = scope.getJsonMapper().createArrayNode();
      for (String tool : candidate.tools())
        toolsArray.add(tool);
      node.set("tools", toolsArray);
      node.put("chain_length", candidate.chainLength());
      node.put("optimization", "PIPELINE_CANDIDATE");
      result.add(node);
    }

    return result;
  }

  /**
   * Finds deterministic multi-step operations that recur in the same order (script extraction candidates).
   * These are repeated tool sequences that could be extracted into standalone scripts.
   * <p>
   * Detection heuristic: finds all subsequences of length 2-5 that appear at least twice in the session,
   * then filters out subsequences that are contained within longer matches.
   *
   * @param toolUses list of tool uses
   * @return JSON array of script extraction candidate objects sorted by occurrence count descending
   * @throws NullPointerException if toolUses is null
   */
  private ArrayNode findScriptExtractionCandidates(List<ToolUse> toolUses)
  {
    requireThat(toolUses, "toolUses").isNotNull();

    if (toolUses.size() < MIN_SCRIPT_EXTRACTION_LENGTH)
      return scope.getJsonMapper().createArrayNode();

    // Build tool name sequences and count occurrences of each subsequence (length 2-5)
    int maxSeqLen = Math.min(5, toolUses.size());
    Map<String, Integer> sequenceCounts = new LinkedHashMap<>();
    Map<String, List<String>> sequenceTools = new LinkedHashMap<>();

    for (int len = MIN_SCRIPT_EXTRACTION_LENGTH; len <= maxSeqLen; ++len)
    {
      for (int i = 0; i <= toolUses.size() - len; ++i)
      {
        List<String> seq = new ArrayList<>();
        for (int j = i; j < i + len; ++j)
          seq.add(toolUses.get(j).name());

        String key = String.join("→", seq);
        sequenceCounts.merge(key, 1, Integer::sum);
        sequenceTools.putIfAbsent(key, seq);
      }
    }

    // Filter to sequences that appear at least MIN_SCRIPT_EXTRACTION_OCCURRENCES times
    List<ScriptExtractionCandidate> candidates = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : sequenceCounts.entrySet())
    {
      if (entry.getValue() >= MIN_SCRIPT_EXTRACTION_OCCURRENCES)
      {
        List<String> tools = sequenceTools.get(entry.getKey());
        candidates.add(new ScriptExtractionCandidate(tools, entry.getValue(), tools.size()));
      }
    }

    // Precompute keys once to avoid O(n²) String.join calls inside the filter loop
    List<String> candidateKeys = new ArrayList<>(candidates.size());
    for (ScriptExtractionCandidate candidate : candidates)
      candidateKeys.add(String.join("→", candidate.tools()));

    // Remove subsequences that are fully contained within a longer candidate
    List<ScriptExtractionCandidate> filtered = new ArrayList<>();
    for (int i = 0; i < candidates.size(); ++i)
    {
      ScriptExtractionCandidate candidate = candidates.get(i);
      String candidateKey = candidateKeys.get(i);
      boolean subsumed = false;
      for (int j = 0; j < candidates.size(); ++j)
      {
        ScriptExtractionCandidate other = candidates.get(j);
        if (other.sequenceLength() > candidate.sequenceLength() &&
          other.occurrences() >= candidate.occurrences() &&
          candidateKeys.get(j).contains(candidateKey))
        {
          subsumed = true;
          break;
        }
      }
      if (!subsumed)
        filtered.add(candidate);
    }

    filtered.sort((a, b) ->
    {
      int cmp = Integer.compare(b.occurrences(), a.occurrences());
      if (cmp != 0)
        return cmp;
      return Integer.compare(b.sequenceLength(), a.sequenceLength());
    });

    ArrayNode result = scope.getJsonMapper().createArrayNode();
    for (ScriptExtractionCandidate candidate : filtered)
    {
      ObjectNode node = scope.getJsonMapper().createObjectNode();
      ArrayNode toolsArray = scope.getJsonMapper().createArrayNode();
      for (String tool : candidate.tools())
        toolsArray.add(tool);
      node.set("sequence", toolsArray);
      node.put("occurrences", candidate.occurrences());
      node.put("sequence_length", candidate.sequenceLength());
      node.put("optimization", "SCRIPT_EXTRACTION_CANDIDATE");
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
    // Extract session name from JSONL filename to locate subagents directory.
    // Claude Code stores subagents at {sessionDir}/{sessionName}/subagents/.
    String sessionName = filePath.getFileName().toString();
    if (sessionName.endsWith(".jsonl"))
      sessionName = sessionName.substring(0, sessionName.length() - ".jsonl".length());
    Path subagentDir = sessionDir.resolve(sessionName).resolve("subagents");

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
   * <p>
   * Note: {@code additionalContext} fields from hook events (such as {@code SubagentStart}) are injected at the
   * API level and are not stored in JSONL session logs. Searches against session files will not return matches
   * for content delivered via {@code additionalContext}.
   *
   * @param filePath  path to the session JSONL file
   * @param pattern   the pattern to search for; a literal keyword or a regex depending on {@code useRegex}
   * @param contextLines number of surrounding lines to include before and after the match
   * @param useRegex  {@code true} to compile {@code pattern} as a Java regex; {@code false} for literal match
   * @return JSON object with a "matches" array (each element has "type", "text", "entry_index") and a
   *   "pattern" field containing the original pattern string; when the "matches" array is empty, a "hint"
   *   field is included explaining that additionalContext from hook events is not stored in JSONL logs
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
    if (matches.isEmpty())
      result.put("hint", ADDITIONAL_CONTEXT_HINT);
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
        if (contentStr.contains("exit_code"))
        try
        {
          JsonNode contentJson = scope.getJsonMapper().readTree(contentStr);
          JsonNode exitCodeNode = contentJson.path("exit_code");
          if (!exitCodeNode.isMissingNode())
            exitCode = exitCodeNode.asInt(0);

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
   * @param inputString the JSON string representation of {@code input}, precomputed to avoid repeated
   *   serialization in hot loops
   */
  private record ToolUse(String id, String name, JsonNode input, String messageId, String inputString)
  {
    /**
     * Creates a new tool use record.
     *
     * @param id tool use ID
     * @param name tool name
     * @param input tool input parameters
     * @param messageId message ID containing this tool use
     * @param inputString the JSON string representation of {@code input}, precomputed to avoid repeated
     *   serialization in hot loops
     * @throws NullPointerException if any parameter is null
     */
    private ToolUse
    {
      requireThat(id, "id").isNotNull();
      requireThat(name, "name").isNotNull();
      requireThat(input, "input").isNotNull();
      requireThat(messageId, "messageId").isNotNull();
      requireThat(inputString, "inputString").isNotNull();
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
   * Represents a pipeline candidate — a chain of dependent sequential tool calls where each tool's input
   * references the previous tool's output.
   *
   * @param tools       ordered list of tool names in the chain
   * @param chainLength number of tools in the chain
   */
  private record PipelineCandidate(List<String> tools, int chainLength)
  {
    /**
     * Creates a new pipeline candidate record.
     *
     * @param tools       ordered list of tool names in the chain
     * @param chainLength number of tools in the chain
     * @throws NullPointerException if tools is null
     */
    private PipelineCandidate
    {
      requireThat(tools, "tools").isNotNull();
    }
  }

  /**
   * Represents a script extraction candidate — a recurring tool sequence that could be extracted into a
   * standalone script.
   *
   * @param tools          ordered list of tool names in the sequence
   * @param occurrences    number of times this sequence appeared
   * @param sequenceLength number of tools in the sequence
   */
  private record ScriptExtractionCandidate(List<String> tools, int occurrences, int sequenceLength)
  {
    /**
     * Creates a new script extraction candidate record.
     *
     * @param tools          ordered list of tool names in the sequence
     * @param occurrences    number of times this sequence appeared
     * @param sequenceLength number of tools in the sequence
     * @throws NullPointerException if tools is null
     */
    private ScriptExtractionCandidate
    {
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

  /**
   * Represents a timestamped tool invocation event.
   *
   * @param timestamp the instant when the entry was recorded
   * @param toolName  the name of the tool (e.g., "Read", "Skill")
   * @param phaseName the CAT phase name if this is a phase-marker Skill invocation,
   *   or {@code null} if not a phase marker
   */
  private record TimedTool(Instant timestamp, String toolName, String phaseName)
  {
    /**
     * Creates a new timed tool record.
     *
     * @param timestamp the instant when the entry was recorded
     * @param toolName  the name of the tool
     * @param phaseName the phase name, or {@code null}
     * @throws NullPointerException if {@code timestamp} or {@code toolName} is null
     */
    private TimedTool
    {
      requireThat(timestamp, "timestamp").isNotNull();
      requireThat(toolName, "toolName").isNotNull();
    }
  }

  /**
   * Mutable accumulator for per-tool timing statistics.
   */
  private static final class ToolTimingStats
  {
    private double totalSeconds;
    private int count;

    /**
     * Adds a single tool call with its elapsed time.
     *
     * @param elapsedSeconds elapsed time for the call in seconds
     */
    private void add(double elapsedSeconds)
    {
      totalSeconds += elapsedSeconds;
      ++count;
    }
  }
}
