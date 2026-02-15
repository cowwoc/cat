/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Utility methods for extracting values from JSON nodes.
 *
 * Provides type-safe accessors with default values for common JSON extraction patterns.
 */
public final class JsonHelper
{
  /**
   * Private constructor to prevent instantiation.
   */
  private JsonHelper()
  {
  }

  /**
   * Gets a string value from a JSON node or returns a default.
   *
   * @param node the JSON node to read from
   * @param key the key to retrieve
   * @param defaultValue the value to return if key is missing, not a string, or empty
   * @return the string value or default
   * @throws NullPointerException if {@code node} or {@code key} are null
   */
  public static String getStringOrDefault(JsonNode node, String key, String defaultValue)
  {
    requireThat(node, "node").isNotNull();
    requireThat(key, "key").isNotBlank();

    JsonNode child = node.get(key);
    if (child != null && child.isString())
    {
      String value = child.asString();
      if (value != null && !value.isEmpty())
        return value;
    }
    return defaultValue;
  }

  /**
   * Gets an integer value from a JSON node or returns a default.
   *
   * @param node the JSON node to read from
   * @param key the key to retrieve
   * @param defaultValue the value to return if key is missing or not a number
   * @return the integer value or default
   * @throws NullPointerException if node or key is null
   */
  public static int getIntOrDefault(JsonNode node, String key, int defaultValue)
  {
    requireThat(node, "node").isNotNull();
    requireThat(key, "key").isNotBlank();

    JsonNode child = node.get(key);
    if (child != null && child.isNumber())
      return child.asInt();
    return defaultValue;
  }

  /**
   * Gets an array of JsonNode items from a JSON node.
   *
   * @param node the JSON node to read from
   * @param key the key to retrieve
   * @return the list of JsonNode items, or empty list if missing or not an array
   * @throws NullPointerException if node or key is null
   */
  public static List<JsonNode> getArray(JsonNode node, String key)
  {
    requireThat(node, "node").isNotNull();
    requireThat(key, "key").isNotBlank();

    List<JsonNode> result = new ArrayList<>();
    JsonNode child = node.get(key);
    if (child != null && child.isArray())
    {
      for (JsonNode item : child)
        result.add(item);
    }
    return result;
  }

  /**
   * Gets an array of strings from a JSON node.
   *
   * @param node the JSON node to read from
   * @param key the key to retrieve
   * @return the list of string values, or empty list if missing or not an array
   * @throws NullPointerException if node or key is null
   */
  public static List<String> getStringArray(JsonNode node, String key)
  {
    requireThat(node, "node").isNotNull();
    requireThat(key, "key").isNotBlank();

    List<String> result = new ArrayList<>();
    JsonNode child = node.get(key);
    if (child != null && child.isArray())
    {
      for (JsonNode item : child)
      {
        if (item.isString())
          result.add(item.asString());
      }
    }
    return result;
  }
}
