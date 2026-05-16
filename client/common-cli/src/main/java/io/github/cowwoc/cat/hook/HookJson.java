/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hook;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Shared helpers for JSON-based hook payloads.
 */
public final class HookJson
{
  /**
   * Shared mapper for hook payload parsing and response construction.
   */
  public static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
  private static final int MAX_INPUT_BYTES = 1_048_576;

  /**
   * Prevents construction.
   */
  private HookJson()
  {
  }

  /**
   * Reads a hook JSON payload from standard input.
   *
   * @param in standard input
   * @return the parsed payload, or an empty object for blank input
   * @throws NullPointerException if {@code in} is null
   * @throws IllegalArgumentException if the input is not valid JSON
   * @throws IllegalArgumentException if the input exceeds the maximum hook payload size
   */
  public static JsonNode read(InputStream in)
  {
    requireThat(in, "in").isNotNull();
    try
    {
      byte[] bytes = in.readNBytes(MAX_INPUT_BYTES + 1);
      if (bytes.length > MAX_INPUT_BYTES)
      {
        throw new IllegalArgumentException("Hook input exceeds maximum size of " + MAX_INPUT_BYTES +
          " bytes.");
      }
      String raw = new String(bytes, StandardCharsets.UTF_8);
      if (raw.isBlank())
        return JSON_MAPPER.createObjectNode();
      return JSON_MAPPER.readTree(raw);
    }
    catch (JacksonException e)
    {
      throw new IllegalArgumentException("Hook input contains malformed JSON.", e);
    }
    catch (IOException e)
    {
      throw new IllegalArgumentException("Could not read hook input.", e);
    }
  }

  /**
   * Verifies that a hook launcher received no command line arguments.
   *
   * @param args command line arguments
   * @throws NullPointerException if {@code args} is null
   * @throws IllegalArgumentException if any arguments are present
   */
  public static void requireNoArgs(String[] args)
  {
    requireThat(args, "args").isNotNull();
    if (args.length > 0)
      throw new IllegalArgumentException("Unexpected arguments: " + String.join(" ", args));
  }

  /**
   * Returns the supplied object node or an empty object node.
   *
   * @param value a candidate JSON value
   * @return {@code value} when it is an object; otherwise an empty object
   */
  public static JsonNode objectValue(JsonNode value)
  {
    if (value != null && value.isObject())
      return value;
    return JSON_MAPPER.createObjectNode();
  }

  /**
   * Returns the first non-empty string from a list of JSON values.
   *
   * @param values candidate JSON values
   * @return the first non-empty string, or an empty string if none exists
   */
  public static String firstString(JsonNode... values)
  {
    for (JsonNode value : values)
    {
      if (value != null && value.isString() && !value.asString().isEmpty())
        return value.asString();
    }
    return "";
  }
}
