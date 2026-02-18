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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extracts investigation context from a Claude session JSONL file for the learn skill.
 * <p>
 * Performs a single-pass extraction of all tool calls relevant to mistake investigation,
 * eliminating the need for the investigation subagent to re-parse the session file.
 * <p>
 * The extractor collects documents read (Read/Glob tools), skill invocations (Skill tool),
 * and Bash commands with optional keyword filtering. Tool results are correlated with Bash
 * commands using a HashMap for O(1) lookup by tool_use_id.
 */
public final class InvestigationContextExtractor
{
  private static final int MAX_RESULT_LENGTH = 2000;
  private static final int MAX_TIMELINE_EVENTS = 200;
  private static final int MAX_PARSE_ERROR_PREVIEW = 100;
  private final JvmScope scope;

  /**
   * Creates a new investigation context extractor.
   *
   * @param scope the JVM scope providing JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public InvestigationContextExtractor(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Extracts investigation context from a session JSONL file and prints the JSON result to stdout.
   *
   * @param args command-line arguments: {@code <session-file> [keyword1 keyword2 ...]}
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length < 1)
    {
      System.err.println("""
        {
          "error": "Usage: extract-investigation-context <session-file> [keyword1 keyword2 ...]"
        }""");
      System.exit(1);
    }
    try (MainJvmScope scope = new MainJvmScope())
    {
      InvestigationContextExtractor extractor = new InvestigationContextExtractor(scope);
      List<String> keywords = new ArrayList<>();
      for (int i = 1; i < args.length; ++i)
        keywords.add(args[i]);
      JsonNode result = extractor.extract(Path.of(args[0]), keywords);
      System.out.println(scope.getJsonMapper().writeValueAsString(result));
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(InvestigationContextExtractor.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }

  /**
   * Extracts investigation context from a session JSONL file.
   * <p>
   * Performs a single-pass extraction, correlating tool results with Bash commands using
   * a HashMap for O(1) lookup by tool_use_id.
   *
   * @param sessionFile the path to the session JSONL file
   * @param keywords    optional keywords to filter Bash commands; empty list includes all commands
   * @return JSON object with extracted investigation context; includes a {@code parse_errors} array where each entry
   *   contains {@code line_number}, {@code line_preview} (up to 100 chars), and {@code error} message for each
   *   malformed line, and a {@code parse_errors_skipped} count equal to the size of that array. Each entry in
   *   {@code bash_commands} includes a {@code line_number} field (1-based JSONL line where the Bash tool_use appeared)
   *   and a {@code result_truncated} boolean flag indicating whether the result was truncated to
   *   {@value MAX_RESULT_LENGTH} characters.
   * @throws NullPointerException if {@code sessionFile} or {@code keywords} are null
   * @throws IOException          if reading the session file fails
   */
  public JsonNode extract(Path sessionFile, List<String> keywords) throws IOException
  {
    requireThat(sessionFile, "sessionFile").isNotNull();
    requireThat(keywords, "keywords").isNotNull();

    ExtractionState state = new ExtractionState(keywords, scope);

    try (BufferedReader reader = Files.newBufferedReader(sessionFile))
    {
      String line;
      int lineNumber = 0;
      while (true)
      {
        line = reader.readLine();
        if (line == null)
          break;
        ++lineNumber;
        line = line.strip();
        if (line.isEmpty())
          continue;

        JsonNode entry;
        try
        {
          entry = scope.getJsonMapper().readTree(line);
        }
        catch (JacksonException e)
        {
          ObjectNode errorEntry = scope.getJsonMapper().createObjectNode();
          errorEntry.put("line_number", lineNumber);
          String preview;
          if (line.length() > MAX_PARSE_ERROR_PREVIEW)
            preview = line.substring(0, MAX_PARSE_ERROR_PREVIEW);
          else
            preview = line;
          errorEntry.put("line_preview", preview);
          errorEntry.put("error", e.getMessage());
          state.parseErrors.add(errorEntry);
          continue;
        }

        ++state.totalMessages;
        String timestamp = getStringOrDefault(entry, "timestamp", "");
        String entryType = getStringOrDefault(entry, "type", "");

        if (entryType.equals("assistant"))
          processAssistantEntry(entry, timestamp, lineNumber, state);
        else if (entryType.equals("tool"))
          processToolEntry(entry, state);
      }
    }

    ArrayNode bashArray = scope.getJsonMapper().createArrayNode();
    for (ObjectNode cmd : state.bashCommands)
      bashArray.add(cmd);

    ArrayNode keywordsArray = scope.getJsonMapper().createArrayNode();
    for (String kw : keywords)
      keywordsArray.add(kw);

    ObjectNode output = scope.getJsonMapper().createObjectNode();
    output.put("session_file", sessionFile.toString());
    output.set("keywords", keywordsArray);
    output.set("documents_read", state.documentsRead);
    output.set("skill_invocations", state.skillInvocations);
    output.set("bash_commands", bashArray);
    output.set("timeline_events", state.timelineEvents);
    output.put("timeline_truncated", state.timelineTruncated);
    output.put("total_messages_scanned", state.totalMessages);
    output.set("parse_errors", state.parseErrors);
    output.put("parse_errors_skipped", state.parseErrors.size());
    output.put("timezone_context", "TZ=" + scope.getTimezone());

    return output;
  }

  /**
   * Processes a single assistant message entry, extracting tool_use blocks.
   *
   * @param entry      the assistant JSONL entry
   * @param timestamp  the timestamp of the entry
   * @param lineNumber the 1-based JSONL line number of this entry
   * @param state      the mutable extraction state
   * @throws NullPointerException if any parameter is null
   */
  private void processAssistantEntry(JsonNode entry, String timestamp, int lineNumber,
    ExtractionState state)
  {
    requireThat(entry, "entry").isNotNull();
    requireThat(timestamp, "timestamp").isNotNull();
    requireThat(state, "state").isNotNull();

    JsonNode message = entry.path("message");
    JsonNode content = message.path("content");
    if (!content.isArray())
      return;

    for (JsonNode item : content)
    {
      if (!"tool_use".equals(getStringOrDefault(item, "type", "")))
        continue;

      String toolName = getStringOrDefault(item, "name", "");
      String toolId = getStringOrDefault(item, "id", "");
      JsonNode toolInput = item.path("input");

      switch (toolName)
      {
        case "Read", "Glob" -> processReadGlobTool(toolName, toolInput, timestamp, state);
        case "Skill" -> processSkillTool(toolInput, timestamp, state);
        case "Bash" -> processBashTool(toolId, toolInput, timestamp, lineNumber, state);
        default ->
        {
          // Other tool types are not tracked
        }
      }
    }
  }

  /**
   * Processes a Read or Glob tool_use, adding a document entry and optional timeline event.
   *
   * @param toolName  the tool name (Read or Glob)
   * @param toolInput the tool input JSON node
   * @param timestamp the timestamp of the entry
   * @param state     the mutable extraction state
   * @throws NullPointerException if any parameter is null
   */
  private void processReadGlobTool(String toolName, JsonNode toolInput, String timestamp,
    ExtractionState state)
  {
    requireThat(toolName, "toolName").isNotNull();
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(timestamp, "timestamp").isNotNull();
    requireThat(state, "state").isNotNull();

    String filePath;
    if (toolName.equals("Read"))
      filePath = getStringOrDefault(toolInput, "file_path", "");
    else
      filePath = getStringOrDefault(toolInput, "pattern", "");

    ObjectNode docEntry = scope.getJsonMapper().createObjectNode();
    docEntry.put("path", filePath);
    docEntry.put("tool", toolName);
    docEntry.put("timestamp", timestamp);
    state.documentsRead.add(docEntry);

    if (!filePath.isEmpty() && !state.timelineTruncated)
    {
      if (state.timelineEvents.size() >= MAX_TIMELINE_EVENTS)
        state.timelineTruncated = true;
      else
        state.timelineEvents.add("[" + timestamp + "] Read: " + filePath);
    }
  }

  /**
   * Processes a Skill tool_use, adding a skill invocation entry and optional timeline event.
   *
   * @param toolInput the tool input JSON node
   * @param timestamp the timestamp of the entry
   * @param state     the mutable extraction state
   * @throws NullPointerException if any parameter is null
   */
  private void processSkillTool(JsonNode toolInput, String timestamp, ExtractionState state)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(timestamp, "timestamp").isNotNull();
    requireThat(state, "state").isNotNull();

    String skillName = getStringOrDefault(toolInput, "skill", "");
    String skillArgs = getStringOrDefault(toolInput, "args", "");

    ObjectNode skillEntry = scope.getJsonMapper().createObjectNode();
    skillEntry.put("skill", skillName);
    skillEntry.put("args", skillArgs);
    skillEntry.put("timestamp", timestamp);
    state.skillInvocations.add(skillEntry);

    if (!state.timelineTruncated)
    {
      if (state.timelineEvents.size() >= MAX_TIMELINE_EVENTS)
        state.timelineTruncated = true;
      else
      {
        StringBuilder eventText = new StringBuilder("[").append(timestamp).
          append("] Skill: ").append(skillName);
        if (!skillArgs.isEmpty())
          eventText.append(' ').append(skillArgs);
        state.timelineEvents.add(eventText.toString());
      }
    }
  }

  /**
   * Processes a Bash tool_use, adding a bash command entry if it matches the keywords filter.
   *
   * @param toolId     the tool use ID for result correlation
   * @param toolInput  the tool input JSON node
   * @param timestamp  the timestamp of the entry
   * @param lineNumber the 1-based JSONL line number of this entry
   * @param state      the mutable extraction state
   * @throws NullPointerException if any parameter is null
   */
  private void processBashTool(String toolId, JsonNode toolInput, String timestamp,
    int lineNumber, ExtractionState state)
  {
    requireThat(toolId, "toolId").isNotNull();
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(timestamp, "timestamp").isNotNull();
    requireThat(state, "state").isNotNull();

    String command = getStringOrDefault(toolInput, "command", "");
    List<String> matchedKeywords = matchKeywords(command, state.keywords);

    if (!state.keywords.isEmpty() && matchedKeywords.isEmpty())
      return;

    ObjectNode bashEntry = scope.getJsonMapper().createObjectNode();
    bashEntry.put("command", command);
    bashEntry.putNull("result");
    bashEntry.put("result_truncated", false);
    bashEntry.put("timestamp", timestamp);
    bashEntry.put("line_number", lineNumber);
    ArrayNode matchedArray = scope.getJsonMapper().createArrayNode();
    for (String kw : matchedKeywords)
      matchedArray.add(kw);
    bashEntry.set("matched_keywords", matchedArray);

    int index = state.bashCommands.size();
    state.bashCommands.add(bashEntry);
    if (!toolId.isEmpty())
      state.bashCommandIndexByToolUseId.put(toolId, index);

    if (!state.timelineTruncated)
    {
      if (state.timelineEvents.size() >= MAX_TIMELINE_EVENTS)
        state.timelineTruncated = true;
      else
      {
        String preview;
        if (command.length() > 120)
          preview = command.substring(0, 120) + " [truncated]";
        else
          preview = command;
        state.timelineEvents.add("[" + timestamp + "] Bash: " + preview);
      }
    }
  }

  /**
   * Processes a tool result entry, correlating results with previously recorded Bash commands.
   *
   * @param entry the tool JSONL entry
   * @param state the mutable extraction state
   * @throws NullPointerException if any parameter is null
   */
  private void processToolEntry(JsonNode entry, ExtractionState state)
  {
    requireThat(entry, "entry").isNotNull();
    requireThat(state, "state").isNotNull();

    JsonNode content = entry.path("content");
    if (!content.isArray())
      return;

    for (JsonNode resultItem : content)
    {
      if (!"tool_result".equals(getStringOrDefault(resultItem, "type", "")))
        continue;

      String resultId = getStringOrDefault(resultItem, "tool_use_id", "");
      if (resultId.isEmpty())
        continue;

      Integer bashIndex = state.bashCommandIndexByToolUseId.get(resultId);
      if (bashIndex == null)
        continue;

      String resultText = extractResultText(resultItem.path("content"));
      boolean truncated = resultText.length() > MAX_RESULT_LENGTH;
      if (truncated)
        resultText = resultText.substring(0, MAX_RESULT_LENGTH);
      state.bashCommands.get(bashIndex).put("result", resultText);
      state.bashCommands.get(bashIndex).put("result_truncated", truncated);
    }
  }

  /**
   * Returns the list of keywords that appear in the given text (case-insensitive).
   *
   * @param text     the text to search within
   * @param keywords the keywords to look for
   * @return list of matched keywords; empty list if none match or keywords is empty
   * @throws NullPointerException if {@code text} or {@code keywords} are null
   */
  private List<String> matchKeywords(String text, List<String> keywords)
  {
    requireThat(text, "text").isNotNull();
    requireThat(keywords, "keywords").isNotNull();

    if (keywords.isEmpty())
      return List.of();
    String textLower = text.toLowerCase(Locale.ROOT);
    List<String> matched = new ArrayList<>();
    for (String keyword : keywords)
    {
      if (textLower.contains(keyword.toLowerCase(Locale.ROOT)))
        matched.add(keyword);
    }
    return matched;
  }

  /**
   * Extracts text from a tool_result content node.
   * <p>
   * Handles both string content and array content with text items.
   *
   * @param content the content node from a tool_result
   * @return the extracted text, or empty string if content is missing or unrecognized
   * @throws NullPointerException if {@code content} is null
   */
  private String extractResultText(JsonNode content)
  {
    requireThat(content, "content").isNotNull();

    if (content.isString())
      return content.asString();
    if (content.isArray())
    {
      StringBuilder sb = new StringBuilder();
      for (JsonNode item : content)
      {
        if (item.isObject() && "text".equals(getStringOrDefault(item, "type", "")))
        {
          String text = getStringOrDefault(item, "text", "");
          if (sb.length() > 0)
            sb.append('\n');
          sb.append(text);
        }
      }
      return sb.toString();
    }
    return "";
  }

  /**
   * Mutable state accumulated during a single-pass extraction of a session JSONL file.
   */
  private static final class ExtractionState
  {
    private final List<String> keywords;
    private final ArrayNode documentsRead;
    private final ArrayNode skillInvocations;
    private final List<ObjectNode> bashCommands = new ArrayList<>();
    private final ArrayNode timelineEvents;
    private final Map<String, Integer> bashCommandIndexByToolUseId = new HashMap<>();
    private final ArrayNode parseErrors;
    private int totalMessages;
    private boolean timelineTruncated;

    /**
     * Creates a new extraction state.
     *
     * @param keywords the keyword filter list
     * @param scope    the JVM scope used to create JSON nodes
     * @throws NullPointerException if {@code keywords} or {@code scope} are null
     */
    private ExtractionState(List<String> keywords, JvmScope scope)
    {
      requireThat(keywords, "keywords").isNotNull();
      requireThat(scope, "scope").isNotNull();
      this.keywords = keywords;
      this.documentsRead = scope.getJsonMapper().createArrayNode();
      this.skillInvocations = scope.getJsonMapper().createArrayNode();
      this.timelineEvents = scope.getJsonMapper().createArrayNode();
      this.parseErrors = scope.getJsonMapper().createArrayNode();
    }
  }
}
