/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.read.post;

import io.github.cowwoc.cat.hooks.ReadHandler;
import tools.jackson.databind.JsonNode;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detects sequential tool execution anti-pattern.
 * <p>
 * Monitors Read/Glob/Grep/WebFetch/WebSearch PostToolUse events and warns
 * when multiple sequential single-tool messages are detected, suggesting
 * batching for efficiency.
 */
public final class DetectSequentialTools implements ReadHandler
{
  private static final Set<String> BATCHABLE_TOOLS = Set.of("Read", "Glob", "Grep", "WebFetch", "WebSearch");
  private static final int WINDOW_SECONDS = 30;
  private static final int THRESHOLD = 3;

  private final JsonMapper mapper;

  /**
   * Creates a new sequential tools detector.
   *
   * @param mapper the JSON mapper to use for state serialization
   * @throws NullPointerException if mapper is null
   */
  public DetectSequentialTools(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    this.mapper = mapper;
  }

  @Override
  public Result check(String toolName, JsonNode toolInput, JsonNode toolResult, String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    if (!BATCHABLE_TOOLS.contains(toolName))
      return Result.allow();

    int currentTime = (int) (System.currentTimeMillis() / 1000);
    Path stateFile = Path.of("/tmp/sequential-tool-tracker-" + sessionId + ".json");
    TrackerState state = loadState(stateFile);

    // If more than 30 seconds since last tool, reset
    int timeDiff = currentTime - state.lastToolTime;
    if (timeDiff > WINDOW_SECONDS)
    {
      state = new TrackerState(currentTime, 1, List.of(toolName));
      saveState(stateFile, state);
      return Result.allow();
    }

    // Increment sequential count
    int newCount = state.sequentialCount + 1;
    List<String> newToolNames = new ArrayList<>(state.lastToolNames);
    newToolNames.add(toolName);

    // Warn if threshold reached
    if (newCount >= THRESHOLD)
    {
      Set<String> uniqueTools = Set.copyOf(newToolNames);
      String toolList = String.join(", ", uniqueTools);

      // Reset counter after warning
      saveState(stateFile, new TrackerState(currentTime, 0, List.of()));

      return Result.warn(buildWarning(newCount, toolName, toolList));
    }

    // Update state
    saveState(stateFile, new TrackerState(currentTime, newCount, newToolNames));
    return Result.allow();
  }

  /**
   * Tracker state record.
   *
   * @param lastToolTime Unix timestamp of last tool execution
   * @param sequentialCount count of sequential operations
   * @param lastToolNames list of tool names in sequence
   */
  private record TrackerState(int lastToolTime, int sequentialCount, List<String> lastToolNames)
  {
    /**
     * Creates a tracker state.
     *
     * @param lastToolTime Unix timestamp of last tool execution
     * @param sequentialCount count of sequential operations
     * @param lastToolNames list of tool names in sequence
     * @throws AssertionError if {@code lastToolNames} is null
     */
    TrackerState
    {
      assert that(lastToolNames, "lastToolNames").isNotNull().elseThrow();
    }
  }

  /**
   * Loads tracker state from file.
   *
   * @param stateFile the state file path
   * @return the loaded state or default state
   */
  private TrackerState loadState(Path stateFile)
  {
    if (!Files.exists(stateFile))
      return new TrackerState(0, 0, List.of());
    try
    {
      JsonNode node = mapper.readTree(Files.readString(stateFile));
      int lastTime = 0;
      if (node.get("last_tool_time") != null)
        lastTime = node.get("last_tool_time").asInt();
      int count = 0;
      if (node.get("sequential_count") != null)
        count = node.get("sequential_count").asInt();
      List<String> names = new ArrayList<>();
      JsonNode namesNode = node.get("last_tool_names");
      if (namesNode != null && namesNode.isArray())
      {
        for (JsonNode n : namesNode)
        {
          if (n.isString())
            names.add(n.asString());
        }
      }
      return new TrackerState(lastTime, count, names);
    }
    catch (IOException _)
    {
      return new TrackerState(0, 0, List.of());
    }
  }

  /**
   * Saves tracker state to file.
   *
   * @param stateFile the state file path
   * @param state the state to save
   */
  private void saveState(Path stateFile, TrackerState state)
  {
    try
    {
      ObjectNode node = mapper.createObjectNode();
      node.put("last_tool_time", state.lastToolTime());
      node.put("sequential_count", state.sequentialCount());
      ArrayNode names = mapper.createArrayNode();
      for (String name : state.lastToolNames())
        names.add(name);
      node.set("last_tool_names", names);
      Files.writeString(stateFile, mapper.writeValueAsString(node));
    }
    catch (IOException _)
    {
      // Ignore
    }
  }

  /**
   * Builds the warning message.
   *
   * @param count the sequential count
   * @param toolName the current tool name
   * @param toolList the list of tools used
   * @return the warning message
   */
  private String buildWarning(int count, String toolName, String toolList)
  {
    return "## PERFORMANCE: Sequential Tool Execution Detected\n\n" +
      "**Pattern**: " + count + " consecutive single-tool messages\n" +
      "**Tools**: " + toolList + "\n\n" +
      "## ANTI-PATTERN DETECTED (25-30% overhead)\n\n" +
      "**Current Pattern** (sequential execution):\n" +
      "```\n" +
      "Message 1: " + toolName + " file_1\n" +
      "Message 2: " + toolName + " file_2\n" +
      "Message 3: " + toolName + " file_3\n" +
      "# Result: 3 round-trips = 200-300 extra messages per session\n" +
      "```\n\n" +
      "## REQUIRED PATTERN (parallel execution)\n\n" +
      "**Batch Independent Tools in Single Message**:\n" +
      "```\n" +
      "Single Message:\n" +
      "  " + toolName + " file_1 +\n" +
      "  " + toolName + " file_2 +\n" +
      "  " + toolName + " file_3\n" +
      "# Result: 1 round-trip = 67% message reduction\n" +
      "```\n\n" +
      "## BATCHING RULES\n\n" +
      "**ALWAYS batch these tools when independent**:\n" +
      "1. Read operations - batch all file reads together\n" +
      "2. Glob patterns - batch all file searches together\n" +
      "3. Grep searches - batch all content searches together\n" +
      "4. WebFetch/WebSearch - batch all web operations together\n" +
      "5. Agent invocations - launch all agents in parallel\n\n" +
      "**Only use sequential when**:\n" +
      "- Operations have dependencies (later tool needs earlier tool's output)\n" +
      "- Conditional logic required between operations\n\n" +
      "**This reminder will reset after you batch tools or 30 seconds of inactivity.**";
  }
}
