/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.engine;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.engine.NestedRunnerEngineSubstates;
import io.github.cowwoc.cat.engine.NestedRunnerEvent;
import io.github.cowwoc.cat.engine.NestedRunnerSessionState;
import io.github.cowwoc.cat.engine.NestedRunnerState;
import io.github.cowwoc.cat.engine.NestedRunnerTurnState;
import io.github.cowwoc.cat.claude.engine.ClaudeEventPrefilter.CandidateEvent;
import io.github.cowwoc.cat.claude.engine.ClaudeRunner.ParsedOutput;
import io.github.cowwoc.cat.claude.engine.ClaudeRunner.ParsedSessionOutput;
import io.github.cowwoc.cat.claude.engine.ClaudeRunner.TurnOutput;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses Claude stream-json output into CAT runner state.
 */
final class ClaudeSessionOutputParser
{
  private final ClaudeEventPrefilter prefilter;
  private final JsonMapper mapper;

  /**
   * Creates a new session-output parser.
   *
   * @param mapper the shared JSON mapper
   */
  ClaudeSessionOutputParser(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    this.mapper = mapper;
    this.prefilter = new ClaudeEventPrefilter(mapper);
  }

  /**
   * Parses stream-json output and derives the latest session state, streaming state updates as
   * relevant events arrive.
   *
   * @param reader        the reader supplying stream-json lines
   * @param eventListener receives state snapshots as relevant engine events arrive
   * @return the parsed output and derived state
   * @throws IOException if reading or parsing fails
   */
  ParsedSessionOutput parseSessionOutput(BufferedReader reader,
    Consumer<NestedRunnerEvent> eventListener) throws IOException
  {
    requireThat(reader, "reader").isNotNull();
    requireThat(eventListener, "eventListener").isNotNull();
    SessionAccumulator state = new SessionAccumulator();

    String line = reader.readLine();
    while (line != null)
    {
      String trimmed = line.strip();
      processLine(trimmed, state, eventListener);
      line = reader.readLine();
    }
    finishOpenTurn(state);
    ParsedOutput parsed = new ParsedOutput(state.texts, state.toolUses, state.writeContents,
      state.turns, state.sessionId);
    return new ParsedSessionOutput(parsed, snapshotState(state));
  }

  /**
   * Processes one Claude stream-json line.
   *
   * @param line the raw line without surrounding whitespace
   * @param state the mutable parse state
   * @param eventListener receives state snapshots for relevant events
   * @throws IOException if parsing fails
   */
  private void processLine(String line, SessionAccumulator state,
    Consumer<NestedRunnerEvent> eventListener) throws IOException
  {
    if (line.isEmpty())
      return;
    StateSnapshot before = new StateSnapshot(state);
    boolean waitingForToolResult =
      state.turnState == NestedRunnerTurnState.WAITING_FOR_TOOL_RESULT;
    CandidateEvent candidate = prefilter.candidateEvent(line, waitingForToolResult);
    if (!candidate.relevant())
      return;
    JsonNode event = mapper.readTree(line);
    String type = event.path("type").asString("");
    if (!shouldParseEvent(candidate, type, event, waitingForToolResult))
      return;
    updateEventMetadata(state, event, type);
    if (type.equals("assistant"))
      handleAssistantEvent(state, event);
    else if (type.equals("result"))
      handleResultEvent(state, event);
    else if (type.equals("error"))
      handleErrorEvent(state, event);
    else if ((type.equals("tool") || type.equals("user")) &&
      eventContainsContentBlockType(event, "tool_result"))
    {
      clearToolWaitState(state);
    }
    emitStateChangeIfNeeded(before, state, line, eventListener);
  }

  /**
   * Updates event metadata fields from a parsed Claude event.
   *
   * @param state the mutable parse state
   * @param event the parsed event payload
   * @param type the top-level event type
   */
  private static void updateEventMetadata(SessionAccumulator state, JsonNode event, String type)
  {
    state.latestEventType = type;
    state.latestEventTimestamp = firstText(event, "timestamp");
    if (state.sessionId.isEmpty())
    {
      String id = event.path("session_id").asString("");
      if (!id.isEmpty())
        state.sessionId = id;
    }
  }

  /**
   * Handles a Claude assistant event.
   *
   * @param state the mutable parse state
   * @param event the parsed assistant event
   */
  private static void handleAssistantEvent(SessionAccumulator state, JsonNode event)
  {
    state.sessionState = NestedRunnerSessionState.WORKING;
    state.turnState = NestedRunnerTurnState.WORKING;
    state.canSubmitTurn = false;
    state.engineSubstate = "";
    JsonNode content = event.path("message").path("content");
    if (!content.isArray())
      return;
    for (JsonNode block : content)
      handleAssistantBlock(state, block);
  }

  /**
   * Handles one block inside an assistant event.
   *
   * @param state the mutable parse state
   * @param block the assistant content block
   */
  private static void handleAssistantBlock(SessionAccumulator state, JsonNode block)
  {
    String blockType = block.path("type").asString("");
    if (blockType.equals("text"))
    {
      appendText(state, block.path("text").asString(""));
      return;
    }
    if (!blockType.equals("tool_use"))
      return;
    String name = block.path("name").asString("");
    state.toolUses.add(name);
    state.currentTurnToolUses.add(name);
    state.turnState = NestedRunnerTurnState.WAITING_FOR_TOOL_RESULT;
    state.engineSubstate = NestedRunnerEngineSubstates.WAITING_FOR_TOOL_RESULT;
    if (!name.equals("Write"))
      return;
    String writeContent = block.path("input").path("content").asString("");
    if (!writeContent.isEmpty())
    {
      state.writeContents.add(writeContent);
      state.currentTurnWriteContents.add(writeContent);
    }
  }

  /**
   * Handles a Claude result event.
   *
   * @param state the mutable parse state
   * @param event the parsed result event
   */
  private static void handleResultEvent(SessionAccumulator state, JsonNode event)
  {
    String result = event.path("result").asString("");
    if (!result.isEmpty())
      appendText(state, result);
    boolean resultIsError = isResultError(event);
    if (resultIsError)
    {
      String subtype = event.path("subtype").asString("");
      state.error = result;
      if (state.error.isEmpty())
        state.error = subtype;
      state.turnState = NestedRunnerTurnState.ERROR;
      state.sessionState = NestedRunnerSessionState.ERROR;
      state.canSubmitTurn = false;
    }
    else
    {
      state.turnState = NestedRunnerTurnState.COMPLETED;
      state.sessionState = NestedRunnerSessionState.WAITING_FOR_NEXT_REQUEST;
      state.canSubmitTurn = true;
    }
    state.engineSubstate = "";
    finishCurrentTurn(state);
  }

  /**
   * Returns whether a Claude result event should be treated as an error.
   *
   * @param event the parsed result event
   * @return {@code true} if the result represents an error
   */
  private static boolean isResultError(JsonNode event)
  {
    boolean resultIsError = event.path("is_error").asBoolean(false);
    String subtype = event.path("subtype").asString("");
    if (!subtype.isEmpty() && !subtype.equals("success"))
      resultIsError = true;
    return resultIsError;
  }

  /**
   * Handles a Claude error event.
   *
   * @param state the mutable parse state
   * @param event the parsed error event
   */
  private static void handleErrorEvent(SessionAccumulator state, JsonNode event)
  {
    state.error = firstText(event, "message", "error");
    state.turnState = NestedRunnerTurnState.ERROR;
    state.sessionState = NestedRunnerSessionState.ERROR;
    state.canSubmitTurn = false;
    state.engineSubstate = "";
    finishCurrentTurn(state);
  }

  /**
   * Clears the waiting-for-tool-result substate once Claude emits a tool result.
   *
   * @param state the mutable parse state
   */
  private static void clearToolWaitState(SessionAccumulator state)
  {
    state.sessionState = NestedRunnerSessionState.WORKING;
    state.turnState = NestedRunnerTurnState.WORKING;
    state.canSubmitTurn = false;
    state.engineSubstate = "";
  }

  /**
   * Appends assistant text to both the full-session and current-turn accumulators.
   *
   * @param state the mutable parse state
   * @param text the text to append
   */
  private static void appendText(SessionAccumulator state, String text)
  {
    state.texts.add(text);
    state.currentTurnTexts.add(text);
  }

  /**
   * Emits a streamed state event if the visible runner state changed.
   *
   * @param before the state snapshot before processing the line
   * @param state the mutable parse state after processing the line
   * @param line the raw event line
   * @param eventListener the listener to notify
   */
  private static void emitStateChangeIfNeeded(StateSnapshot before, SessionAccumulator state,
    String line, Consumer<NestedRunnerEvent> eventListener)
  {
    if (!stateChanged(before.sessionId, before.turnState, before.sessionState,
      before.canSubmitTurn, before.engineSubstate, before.error, state.sessionId,
      state.turnState, state.sessionState, state.canSubmitTurn, state.engineSubstate,
      state.error))
    {
      return;
    }
    eventListener.accept(new NestedRunnerEvent(line, snapshotState(state)));
  }

  /**
   * Finalizes a completed or failed turn and resets the current-turn accumulators.
   *
   * @param state the mutable parse state
   */
  private static void finishCurrentTurn(SessionAccumulator state)
  {
    state.turns.add(finishTurn(state.currentTurnTexts, state.currentTurnToolUses,
      state.currentTurnWriteContents));
    state.currentTurnTexts = new ArrayList<>();
    state.currentTurnToolUses = new ArrayList<>();
    state.currentTurnWriteContents = new ArrayList<>();
  }

  /**
   * Finalizes a partially accumulated turn at end-of-stream.
   *
   * @param state the mutable parse state
   */
  private static void finishOpenTurn(SessionAccumulator state)
  {
    if (state.currentTurnTexts.isEmpty() && state.currentTurnToolUses.isEmpty() &&
      state.currentTurnWriteContents.isEmpty())
    {
      return;
    }
    finishCurrentTurn(state);
  }

  /**
   * Builds a snapshot of the latest runner state.
   *
   * @param state the mutable parse state
   * @return the immutable runner-state snapshot
   */
  private static NestedRunnerState snapshotState(SessionAccumulator state)
  {
    return new NestedRunnerState(state.sessionId, "", state.latestEventType,
      state.latestEventTimestamp, state.turnState, state.sessionState, state.canSubmitTurn,
      state.engineSubstate, state.error);
  }

  /**
   * Creates an immutable turn snapshot.
   *
   * @param texts         the assistant texts emitted this turn
   * @param toolUses      the tool uses emitted this turn
   * @param writeContents the write contents emitted this turn
   * @return the turn snapshot
   */
  private static TurnOutput finishTurn(List<String> texts, List<String> toolUses,
    List<String> writeContents)
  {
    return new TurnOutput(List.copyOf(texts), List.copyOf(toolUses), List.copyOf(writeContents));
  }

  /**
   * Returns whether the externally visible runner state changed between two events.
   *
   * @param previousSessionId the prior session identifier
   * @param previousTurnState the prior turn state
   * @param previousSessionState the prior session state
   * @param previousCanSubmitTurn whether the prior snapshot allowed another turn
   * @param previousEngineSubstate the prior engine-specific substate
   * @param previousError the prior error string
   * @param sessionId the current session identifier
   * @param turnState the current turn state
   * @param sessionState the current session state
   * @param canSubmitTurn whether the current snapshot allows another turn
   * @param engineSubstate the current engine-specific substate
   * @param error the current error string
   * @return {@code true} if the externally visible runner state changed
   */
  private static boolean stateChanged(String previousSessionId,
    NestedRunnerTurnState previousTurnState, NestedRunnerSessionState previousSessionState,
    boolean previousCanSubmitTurn, String previousEngineSubstate, String previousError,
    String sessionId, NestedRunnerTurnState turnState, NestedRunnerSessionState sessionState,
    boolean canSubmitTurn, String engineSubstate, String error)
  {
    return !previousSessionId.equals(sessionId) ||
      previousTurnState != turnState ||
      previousSessionState != sessionState ||
      previousCanSubmitTurn != canSubmitTurn ||
      !previousEngineSubstate.equals(engineSubstate) ||
      !previousError.equals(error);
  }

  /**
   * Returns the first string-valued field from a JSON node.
   *
   * @param node the JSON node to inspect
   * @param fieldNames the candidate field names in lookup order
   * @return the first non-empty string field, or an empty string if none exists
   */
  private static String firstText(JsonNode node, String... fieldNames)
  {
    if (node == null || node.isMissingNode() || node.isNull())
      return "";
    for (String fieldName : fieldNames)
    {
      JsonNode value = node.path(fieldName);
      if (value.getNodeType() == JsonNodeType.STRING)
      {
        String text = value.stringValue();
        if (!text.isEmpty())
          return text;
      }
    }
    return "";
  }

  /**
   * Applies post-parse filtering for candidate events.
   *
   * @param candidate the lightweight prefilter result
   * @param actualType the parsed top-level event type
   * @param event the fully parsed event payload
   * @param waitingForToolResult whether the caller is waiting for a tool-result envelope
   * @return {@code true} if the parsed event should affect runner state
   */
  private static boolean shouldParseEvent(CandidateEvent candidate, String actualType, JsonNode event,
    boolean waitingForToolResult)
  {
    if (actualType.equals("assistant") || actualType.equals("result") || actualType.equals("error"))
      return true;
    return waitingForToolResult && candidate.containsToolResult() &&
      (actualType.equals("tool") || actualType.equals("user")) &&
      eventContainsContentBlockType(event, "tool_result");
  }

  /**
   * Returns whether a Claude event contains a content block with the expected type.
   *
   * @param event the Claude event payload
   * @param expectedType the content-block type to look for
   * @return {@code true} if the event contains a matching content block
   */
  private static boolean eventContainsContentBlockType(JsonNode event, String expectedType)
  {
    return containsContentBlockType(event.path("content"), expectedType) ||
      containsContentBlockType(event.path("message").path("content"), expectedType);
  }

  /**
   * Returns whether a content array contains a block with the expected type.
   *
   * @param content the content array to inspect
   * @param expectedType the content-block type to look for
   * @return {@code true} if the content array contains a matching block
   */
  private static boolean containsContentBlockType(JsonNode content, String expectedType)
  {
    if (!content.isArray())
      return false;
    for (JsonNode block : content)
    {
      if (expectedType.equals(block.path("type").asString("")))
        return true;
    }
    return false;
  }

  /**
   * Mutable session-parse accumulator.
   */
  private static final class SessionAccumulator
  {
    private final List<String> texts = new ArrayList<>();
    private final List<String> toolUses = new ArrayList<>();
    private final List<String> writeContents = new ArrayList<>();
    private final List<TurnOutput> turns = new ArrayList<>();
    private List<String> currentTurnTexts = new ArrayList<>();
    private List<String> currentTurnToolUses = new ArrayList<>();
    private List<String> currentTurnWriteContents = new ArrayList<>();
    private String sessionId = "";
    private String latestEventType = "";
    private String latestEventTimestamp = "";
    private NestedRunnerTurnState turnState = NestedRunnerTurnState.UNKNOWN;
    private NestedRunnerSessionState sessionState = NestedRunnerSessionState.UNKNOWN;
    private boolean canSubmitTurn;
    private String engineSubstate = "";
    private String error = "";
  }

  /**
   * Immutable snapshot of the externally visible runner state before processing one line.
   *
   * @param sessionId the prior session identifier
   * @param turnState the prior turn state
   * @param sessionState the prior session state
   * @param canSubmitTurn whether the prior state allowed another turn
   * @param engineSubstate the prior engine-specific substate
   * @param error the prior error string
   */
  private record StateSnapshot(String sessionId, NestedRunnerTurnState turnState,
    NestedRunnerSessionState sessionState, boolean canSubmitTurn, String engineSubstate,
    String error)
  {
    /**
     * Creates a visible-state snapshot from the mutable accumulator.
     *
     * @param state the mutable parse state
     */
    private StateSnapshot(SessionAccumulator state)
    {
      this(state.sessionId, state.turnState, state.sessionState, state.canSubmitTurn,
        state.engineSubstate, state.error);
    }
  }
}
