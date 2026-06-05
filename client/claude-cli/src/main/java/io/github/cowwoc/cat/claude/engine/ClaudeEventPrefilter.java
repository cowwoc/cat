/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.engine;

import java.io.IOException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

/**
 * Performs cheap prefiltering on Claude stream-json lines before full tree parsing.
 */
final class ClaudeEventPrefilter
{
  private final JsonMapper mapper;

  /**
   * Creates a new event prefilter.
   *
   * @param mapper the JSON mapper used to create streaming parsers
   */
  ClaudeEventPrefilter(JsonMapper mapper)
  {
    this.mapper = mapper;
  }

  /**
   * Determines whether a Claude output line is worth fully parsing.
   *
   * @param line one line of Claude stream-json output
   * @param waitingForToolResult whether the caller is waiting for a tool-result envelope
   * @return a candidate-event summary
   * @throws IOException if the JSON stream cannot be read
   */
  CandidateEvent candidateEvent(String line, boolean waitingForToolResult) throws IOException
  {
    if (line.isEmpty() || line.charAt(0) != '{')
      return CandidateEvent.irrelevant();
    String type = "";
    boolean containsToolResult = false;
    try (JsonParser parser = mapper.createParser(line))
    {
      if (parser.nextToken() != JsonToken.START_OBJECT)
        return CandidateEvent.irrelevant();
      while (parser.nextToken() != JsonToken.END_OBJECT)
      {
        String fieldName = parser.currentName();
        JsonToken valueToken = parser.nextToken();
        if (fieldName == null)
        {
          parser.skipChildren();
          continue;
        }
        if (fieldName.equals("type") && valueToken == JsonToken.VALUE_STRING)
          type = parser.getValueAsString("");
        else if (waitingForToolResult && (type.equals("tool") || type.equals("user")) &&
          parserContainsContentBlockType(parser, fieldName, valueToken, "tool_result"))
        {
          containsToolResult = true;
        }
        else
        {
          parser.skipChildren();
        }
      }
    }
    catch (JacksonException e)
    {
      if ((type.equals("tool") || type.equals("user")) && !containsToolResult)
        return CandidateEvent.irrelevant();
      throw new IOException(e);
    }
    boolean relevant = type.equals("assistant") || type.equals("result") || type.equals("error") ||
      waitingForToolResult && containsToolResult;
    return new CandidateEvent(type, containsToolResult, relevant);
  }

  /**
   * Returns whether the current parser position contains the expected content block type.
   *
   * @param parser the streaming parser positioned on a message field
   * @param fieldName the current field name
   * @param valueToken the token that starts the field value
   * @param expectedType the content-block type to look for
   * @return {@code true} if the message contains a matching content block
   * @throws IOException if the JSON stream cannot be read
   */
  private boolean parserContainsContentBlockType(JsonParser parser, String fieldName,
    JsonToken valueToken, String expectedType) throws IOException
  {
    if (fieldName.equals("content"))
      return parserContainsContentArray(parser, valueToken, expectedType);
    if (!fieldName.equals("message") || valueToken != JsonToken.START_OBJECT)
      return false;
    while (parser.nextToken() != JsonToken.END_OBJECT)
    {
      String nestedFieldName = parser.currentName();
      JsonToken nestedValueToken = parser.nextToken();
      if (nestedFieldName == null)
      {
        parser.skipChildren();
        continue;
      }
      if (nestedFieldName.equals("content") &&
        parserContainsContentArray(parser, nestedValueToken, expectedType))
      {
        return true;
      }
      parser.skipChildren();
    }
    return false;
  }

  /**
   * Returns whether a content array contains a block with the expected type.
   *
   * @param parser the streaming parser positioned on the content field value
   * @param valueToken the token that starts the content value
   * @param expectedType the content-block type to look for
   * @return {@code true} if the content array contains a matching block
   * @throws IOException if the JSON stream cannot be read
   */
  private static boolean parserContainsContentArray(JsonParser parser, JsonToken valueToken,
    String expectedType) throws IOException
  {
    if (valueToken != JsonToken.START_ARRAY)
      return false;
    while (parser.nextToken() != JsonToken.END_ARRAY)
    {
      if (parser.currentToken() != JsonToken.START_OBJECT)
      {
        parser.skipChildren();
        continue;
      }
      while (parser.nextToken() != JsonToken.END_OBJECT)
      {
        String fieldName = parser.currentName();
        JsonToken nestedValueToken = parser.nextToken();
        if (fieldName == null)
        {
          parser.skipChildren();
          continue;
        }
        if (fieldName.equals("type") && nestedValueToken == JsonToken.VALUE_STRING &&
          expectedType.equals(parser.getValueAsString("")))
        {
          parser.skipChildren();
          return true;
        }
        parser.skipChildren();
      }
    }
    return false;
  }

  /**
   * Lightweight candidate-event prefilter output.
   *
   * @param type the top-level event type
   * @param containsToolResult whether the event contains a tool_result content block
   * @param relevant whether the line is worth fully parsing
   */
  record CandidateEvent(String type, boolean containsToolResult, boolean relevant)
  {
    /**
     * Returns an irrelevant candidate.
     *
     * @return the irrelevant sentinel
     */
    private static CandidateEvent irrelevant()
    {
      return new CandidateEvent("", false, false);
    }
  }
}
