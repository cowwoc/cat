/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.engine;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.tool.skills.PrimingMessage;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds Claude stream-json request messages.
 */
final class ClaudeStreamJsonBuilder
{
  private final JsonMapper mapper;
  private final ObjectWriter compactWriter;

  /**
   * Creates a new stream-json builder.
   *
   * @param mapper the shared JSON mapper
   * @param compactWriter writes compact one-line JSON documents
   */
  ClaudeStreamJsonBuilder(JsonMapper mapper, ObjectWriter compactWriter)
  {
    requireThat(mapper, "mapper").isNotNull();
    requireThat(compactWriter, "compactWriter").isNotNull();
    this.mapper = mapper;
    this.compactWriter = compactWriter;
  }

  /**
   * Builds stream-json input from priming messages and prompt strings.
   * <p>
   * System reminders are appended to each prompt as {@code <system-reminder>} tags.
   *
   * @param primingMessages the priming messages to send before the prompts
   * @param prompts         the prompt strings to send as user messages
   * @param systemReminders system reminder strings to append to each prompt
   * @return the stream-json input string
   */
  String buildInput(List<PrimingMessage> primingMessages, List<String> prompts,
    List<String> systemReminders)
  {
    requireThat(primingMessages, "primingMessages").isNotNull();
    requireThat(prompts, "prompts").isNotNull();
    requireThat(systemReminders, "systemReminders").isNotNull();
    StringJoiner joiner = new StringJoiner("\n");
    int toolUseCounter = 0;
    for (PrimingMessage message : primingMessages)
    {
      switch (message)
      {
        case PrimingMessage.UserMessage userMessage ->
          joiner.add(makeUserMessage(userMessage.text()));
        case PrimingMessage.AssistantMessage assistantMessage ->
          joiner.add(makeAssistantTextMessage(assistantMessage.text()));
        case PrimingMessage.ToolUse toolUse ->
        {
          String toolUseId = "toolu_priming_" + toolUseCounter;
          ++toolUseCounter;
          joiner.add(makeToolUseMessage(toolUseId, toolUse.tool(), toolUse.input()));
          joiner.add(makeToolResultMessage(toolUseId, toolUse.output()));
        }
      }
    }
    for (String prompt : prompts)
      joiner.add(makeUserMessage(appendSystemReminders(prompt, systemReminders)));
    return joiner.toString();
  }

  /**
   * Appends system reminders to a prompt.
   *
   * @param prompt           the prompt text
   * @param systemReminders  the reminder blocks to append
   * @return the final prompt payload
   */
  private static String appendSystemReminders(String prompt, List<String> systemReminders)
  {
    if (systemReminders.isEmpty())
      return prompt;
    StringBuilder builder = new StringBuilder(prompt);
    for (String reminder : systemReminders)
    {
      builder.append("\n<system-reminder>\n").
        append(reminder).
        append("\n</system-reminder>");
    }
    return builder.toString();
  }

  /**
   * Creates a stream-json message with the common envelope structure.
   *
   * @param envelopeType the type field for the outer envelope
   * @param role         the role field for the inner message
   * @param contentBlock the content block to include
   * @return the compact JSON string
   */
  private String buildMessage(String envelopeType, String role, ObjectNode contentBlock)
  {
    ObjectNode message = mapper.createObjectNode();
    message.put("type", envelopeType);

    ObjectNode nestedMessage = mapper.createObjectNode();
    nestedMessage.put("role", role);
    nestedMessage.set("content", mapper.createArrayNode().add(contentBlock));
    message.set("message", nestedMessage);
    return compactWriter.writeValueAsString(message);
  }

  /**
   * Creates a stream-json assistant text message.
   *
   * @param text the assistant text
   * @return the compact JSON string
   */
  private String makeAssistantTextMessage(String text)
  {
    ObjectNode content = mapper.createObjectNode();
    content.put("type", "text");
    content.put("text", text);
    return buildMessage("assistant", "assistant", content);
  }

  /**
   * Creates a stream-json user text message.
   *
   * @param text the user text
   * @return the compact JSON string
   */
  private String makeUserMessage(String text)
  {
    ObjectNode content = mapper.createObjectNode();
    content.put("type", "text");
    content.put("text", text);
    return buildMessage("user", "user", content);
  }

  /**
   * Creates a stream-json assistant tool-use message.
   *
   * @param toolUseId the Claude tool use id
   * @param toolName  the tool name
   * @param toolInput the tool input object
   * @return the compact JSON string
   */
  private String makeToolUseMessage(String toolUseId, String toolName, Map<String, Object> toolInput)
  {
    ObjectNode content = mapper.createObjectNode();
    content.put("type", "tool_use");
    content.put("id", toolUseId);
    content.put("name", toolName);
    content.set("input", mapper.valueToTree(toolInput));
    return buildMessage("assistant", "assistant", content);
  }

  /**
   * Creates a stream-json user tool-result message.
   *
   * @param toolUseId   the Claude tool use id being answered
   * @param toolOutput  the tool output text
   * @return the compact JSON string
   */
  private String makeToolResultMessage(String toolUseId, String toolOutput)
  {
    ObjectNode content = mapper.createObjectNode();
    content.put("type", "tool_result");
    content.put("tool_use_id", toolUseId);
    content.put("content", toolOutput);
    return buildMessage("user", "user", content);
  }
}
