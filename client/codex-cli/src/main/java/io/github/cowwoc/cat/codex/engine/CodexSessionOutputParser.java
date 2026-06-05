/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.engine;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.engine.NestedRunnerEngineSubstates;
import io.github.cowwoc.cat.engine.NestedRunnerEvent;
import io.github.cowwoc.cat.engine.NestedRunnerSessionState;
import io.github.cowwoc.cat.engine.NestedRunnerState;
import io.github.cowwoc.cat.engine.NestedRunnerTurnState;
import io.github.cowwoc.cat.codex.engine.CodexRunner.ParsedOutput;
import io.github.cowwoc.cat.codex.engine.CodexRunner.ParsedSessionOutput;
import io.github.cowwoc.cat.codex.engine.CodexRunner.TurnOutput;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses Codex JSONL output into CAT runner state.
 */
final class CodexSessionOutputParser
{
  private final JsonMapper mapper;

  /**
   * Creates a new Codex session-output parser.
   *
   * @param mapper the shared JSON mapper
   */
  CodexSessionOutputParser(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    this.mapper = mapper;
  }

  /**
   * Parses Codex JSONL output and derives the latest session state.
   *
   * @param reader the source of JSONL events
   * @param eventListener receives streamed state updates
   * @param rawOutput optional raw-output buffer, or {@code null} to skip buffering
   * @return the parsed output and latest derived state
   * @throws IOException if reading from {@code reader} fails
   */
  ParsedSessionOutput parseSessionOutput(BufferedReader reader,
    Consumer<NestedRunnerEvent> eventListener, StringBuilder rawOutput) throws IOException
  {
    requireThat(reader, "reader").isNotNull();
    requireThat(eventListener, "eventListener").isNotNull();
    ParseState state = new ParseState();

    String line = reader.readLine();
    while (line != null)
    {
      if (rawOutput != null)
        rawOutput.append(line).append('\n');
      processLine(line.strip(), state, eventListener);
      line = reader.readLine();
    }

    return new ParsedSessionOutput(toParsedOutput(state), toRunnerState(state));
  }

  /**
   * Processes one stripped Codex JSONL line.
   *
   * @param line the stripped JSONL line
   * @param state the mutable parse state
   * @param eventListener receives state updates when the visible runner state changes
   * @throws IOException if the JSON event cannot be parsed
   */
  private void processLine(String line, ParseState state,
    Consumer<NestedRunnerEvent> eventListener) throws IOException
  {
    if (line.isEmpty() || line.charAt(0) != '{')
      return;
    StateSnapshot before = new StateSnapshot(state);
    JsonNode event = mapper.readTree(line);
    updateEventMetadata(state, event);
    collectText(event, state.texts);
    String toolName = collectToolUse(event, state.toolUses, state.writeContents);
    updateRunnerState(state, event, toolName);
    emitStateChangeIfNeeded(before, state, line, eventListener);
  }

  /**
   * Updates metadata fields that are copied directly from the latest event.
   *
   * @param state the mutable parse state
   * @param event the parsed JSON event
   */
  private static void updateEventMetadata(ParseState state, JsonNode event)
  {
    state.latestEventType = event.path("type").asString("");
    state.latestEventTimestamp = firstText(event, "timestamp");
    if (state.sessionId.isEmpty())
    {
      state.sessionId = firstText(event, "session_id", "sessionId", "conversation_id",
        "thread_id");
    }
    String candidateTurnId = firstText(event, "turn_id", "turnId");
    if (!candidateTurnId.isEmpty())
      state.turnId = candidateTurnId;
  }

  /**
   * Updates the visible runner state from one parsed event.
   *
   * @param state the mutable parse state
   * @param event the parsed JSON event
   * @param toolName the extracted tool name, or an empty string when not applicable
   */
  private static void updateRunnerState(ParseState state, JsonNode event, String toolName)
  {
    String type = state.latestEventType.toLowerCase(Locale.ROOT);
    if (type.equals("turn.started"))
    {
      state.turnState = NestedRunnerTurnState.WAITING_FOR_MODEL;
      state.sessionState = NestedRunnerSessionState.WORKING;
      state.canSubmitTurn = false;
      state.engineSubstate = NestedRunnerEngineSubstates.WAITING_FOR_MODEL;
      return;
    }
    if (isToolRequestEvent(type, toolName))
    {
      state.turnState = NestedRunnerTurnState.WAITING_FOR_TOOL_RESULT;
      state.sessionState = NestedRunnerSessionState.WORKING;
      state.canSubmitTurn = false;
      state.engineSubstate = NestedRunnerEngineSubstates.WAITING_FOR_TOOL_RESULT;
      return;
    }
    if (isToolResultEvent(type))
    {
      state.turnState = NestedRunnerTurnState.WORKING;
      state.sessionState = NestedRunnerSessionState.WORKING;
      state.canSubmitTurn = false;
      state.engineSubstate = "";
      return;
    }
    if (type.equals("turn.completed"))
    {
      state.turnState = NestedRunnerTurnState.COMPLETED;
      state.sessionState = NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST;
      state.canSubmitTurn = true;
      state.engineSubstate = "";
      return;
    }
    if (type.equals("turn.failed") || type.equals("error"))
    {
      state.error = firstText(event, "message");
      if (state.error.isEmpty())
        state.error = firstText(event.path("error"), "message");
      state.turnState = NestedRunnerTurnState.ERROR;
      state.sessionState = NestedRunnerSessionState.ERROR;
      state.canSubmitTurn = false;
      state.engineSubstate = "";
    }
  }

  /**
   * Emits a callback when the externally visible runner state changes.
   *
   * @param before the state snapshot before processing the event
   * @param state the mutable parse state after processing the event
   * @param rawLine the raw JSONL line
   * @param eventListener receives the new state snapshot
   */
  private static void emitStateChangeIfNeeded(StateSnapshot before, ParseState state,
    String rawLine, Consumer<NestedRunnerEvent> eventListener)
  {
    if (!before.changed(state))
      return;
    eventListener.accept(new NestedRunnerEvent(rawLine, toRunnerState(state)));
  }

  /**
   * Builds the parsed-output view from the mutable parse state.
   *
   * @param state the mutable parse state
   * @return the parsed output view
   */
  private static ParsedOutput toParsedOutput(ParseState state)
  {
    List<TurnOutput> turns;
    if (state.texts.isEmpty() && state.toolUses.isEmpty() && state.writeContents.isEmpty())
      turns = List.of();
    else
      turns = List.of(new TurnOutput(List.copyOf(state.texts), List.copyOf(state.toolUses),
        List.copyOf(state.writeContents)));
    return new ParsedOutput(List.copyOf(state.texts), List.copyOf(state.toolUses),
      List.copyOf(state.writeContents), turns, state.sessionId);
  }

  /**
   * Builds the externally visible runner state from the mutable parse state.
   *
   * @param state the mutable parse state
   * @return the immutable runner state snapshot
   */
  private static NestedRunnerState toRunnerState(ParseState state)
  {
    return new NestedRunnerState(state.sessionId, state.turnId, state.latestEventType,
      state.latestEventTimestamp, state.turnState, state.sessionState, state.canSubmitTurn,
      state.engineSubstate, state.error);
  }

  /**
   * Extracts assistant-facing text fragments from a Codex event.
   *
   * @param event the event to inspect
   * @param texts the destination list
   */
  private static void collectText(JsonNode event, List<String> texts)
  {
    String type = event.path("type").asString("").toLowerCase(Locale.ROOT);
    if (isToolResultEvent(type))
      return;
    if (!type.contains("message") && !type.contains("assistant") && !type.contains("response") &&
      !type.contains("result"))
    {
      return;
    }

    String text = firstText(event, "message", "text", "content", "delta", "output");
    if (!text.isEmpty())
    {
      texts.add(text);
      return;
    }

    JsonNode item = event.path("item");
    if (!item.isMissingNode())
    {
      text = firstText(item, "message", "text", "content", "delta", "output");
      if (!text.isEmpty())
        texts.add(text);
    }
  }

  /**
   * Extracts tool-use metadata and write payloads from a Codex event.
   *
   * @param event the event to inspect
   * @param toolUses the destination tool-use list
   * @param writeContents the destination write-content list
   * @return the extracted tool name, or an empty string if the event is not a tool request
   */
  private static String collectToolUse(JsonNode event, List<String> toolUses,
    List<String> writeContents)
  {
    String type = event.path("type").asString("").toLowerCase(Locale.ROOT);
    String toolName = firstText(event, "tool_name", "toolName", "name");
    if (toolName.isEmpty())
      toolName = firstText(event.path("tool"), "name");
    if (toolName.isEmpty())
      toolName = firstText(event.path("item"), "tool_name", "toolName", "name");
    if (!isToolRequestEvent(type, toolName))
      return "";

    toolUses.add(toolName);
    if (!isWriteTool(toolName))
      return toolName;
    String content = firstText(event.path("arguments"), "patch", "content", "cmd", "command");
    if (content.isEmpty())
      content = firstText(event.path("input"), "patch", "content", "cmd", "command");
    if (content.isEmpty())
      content = firstText(event.path("tool_input"), "patch", "content", "cmd", "command");
    if (!content.isEmpty())
      writeContents.add(content);
    return toolName;
  }

  /**
   * Returns whether an event represents a tool request emitted by Codex.
   *
   * @param type the event type
   * @param toolName the extracted tool name
   * @return {@code true} if the event is a tool request
   */
  private static boolean isToolRequestEvent(String type, String toolName)
  {
    if (toolName.isEmpty())
      return false;
    return type.equals("tool_call") || type.equals("tool.called") ||
      type.equals("function_call") || type.equals("function.called");
  }

  /**
   * Returns whether an event represents tool-result completion data.
   *
   * @param type the event type
   * @return {@code true} if the event is a tool-result event
   */
  private static boolean isToolResultEvent(String type)
  {
    return type.equals("tool_result") || type.equals("tool.completed") ||
      type.equals("function_result") || type.equals("function.completed");
  }

  /**
   * Returns whether a tool name should be treated as file-writing behavior.
   *
   * @param toolName the tool name
   * @return {@code true} if the tool should be treated as writing content
   */
  private static boolean isWriteTool(String toolName)
  {
    String lowerCaseToolName = toolName.toLowerCase(Locale.ROOT);
    return lowerCaseToolName.contains("apply_patch") || lowerCaseToolName.equals("write") ||
      lowerCaseToolName.equals("edit") || lowerCaseToolName.endsWith(".write") ||
      lowerCaseToolName.endsWith(".edit");
  }

  /**
   * Returns the first string-valued field from a JSON node.
   *
   * @param node the node to inspect
   * @param fieldNames candidate field names in priority order
   * @return the first non-missing string value, or an empty string
   */
  private static String firstText(JsonNode node, String... fieldNames)
  {
    if (node == null || node.isMissingNode() || node.isNull())
      return "";
    for (String fieldName : fieldNames)
    {
      JsonNode value = node.path(fieldName);
      if (value.isString())
        return value.asString("");
    }
    return "";
  }

  /**
   * Mutable state accumulated while parsing Codex JSONL output.
   */
  private static final class ParseState
  {
    private final List<String> texts = new ArrayList<>();
    private final List<String> toolUses = new ArrayList<>();
    private final List<String> writeContents = new ArrayList<>();
    private String sessionId = "";
    private String turnId = "";
    private String latestEventType = "";
    private String latestEventTimestamp = "";
    private NestedRunnerTurnState turnState = NestedRunnerTurnState.UNKNOWN;
    private NestedRunnerSessionState sessionState = NestedRunnerSessionState.UNKNOWN;
    private boolean canSubmitTurn;
    private String engineSubstate = "";
    private String error = "";
  }

  /**
   * Immutable snapshot used to detect externally visible state changes.
   *
   * @param turnId the current turn identifier
   * @param turnState the current turn state
   * @param sessionState the current session state
   * @param canSubmitTurn whether the runner can accept another turn
   * @param engineSubstate the engine-specific substate
   * @param error the current error string
   */
  private record StateSnapshot(String turnId, NestedRunnerTurnState turnState,
                               NestedRunnerSessionState sessionState, boolean canSubmitTurn,
                               String engineSubstate, String error)
  {
    /**
     * Captures the current externally visible state.
     *
     * @param state the mutable parse state
     */
    private StateSnapshot(ParseState state)
    {
      this(state.turnId, state.turnState, state.sessionState, state.canSubmitTurn,
        state.engineSubstate, state.error);
    }

    /**
     * Returns whether the externally visible state differs from the supplied mutable state.
     *
     * @param state the current mutable parse state
     * @return {@code true} if the visible state changed
     */
    private boolean changed(ParseState state)
    {
      return !turnId.equals(state.turnId) ||
        turnState != state.turnState ||
        sessionState != state.sessionState ||
        canSubmitTurn != state.canSubmitTurn ||
        !engineSubstate.equals(state.engineSubstate) ||
        !error.equals(state.error);
    }
  }
}
