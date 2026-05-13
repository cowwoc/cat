/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import io.github.cowwoc.cat.hook.HookJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.io.PrintStream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Native Codex hook input/output helpers.
 */
final class CodexHookInput
{
  /**
   * Prevents construction.
   */
  private CodexHookInput()
  {
  }

  /**
   * Reads a native Codex hook payload from standard input.
   *
   * @param in standard input
   * @return the parsed payload, or an empty object for blank input
   * @throws NullPointerException if {@code in} is null
   * @throws IllegalArgumentException if the input is not valid JSON
   */
  static JsonNode read(InputStream in)
  {
    return HookJson.read(in);
  }

  /**
   * Verifies that Codex did not pass launcher arguments.
   *
   * @param args command line arguments
   * @throws NullPointerException if {@code args} is null
   * @throws IllegalArgumentException if any arguments are present
   */
  static void requireNoArgs(String[] args)
  {
    HookJson.requireNoArgs(args);
  }

  /**
   * Writes the neutral Codex hook response.
   *
   * @param out standard output
   * @throws NullPointerException if {@code out} is null
   */
  static void empty(PrintStream out)
  {
    requireThat(out, "out").isNotNull();
    out.println("{}");
  }

  /**
   * Writes a Codex hook block response.
   *
   * @param out standard output
   * @param reason the human-readable block reason
   * @throws NullPointerException if {@code out} is null
   * @throws IllegalArgumentException if {@code reason} is blank
   */
  static void block(PrintStream out, String reason)
  {
    requireThat(out, "out").isNotNull();
    requireThat(reason, "reason").isNotBlank();
    ObjectNode response = HookJson.JSON_MAPPER.createObjectNode();
    response.put("decision", "block");
    response.put("reason", reason);
    out.println(response.toString());
  }

  /**
   * Extracts the command string from known native Codex command payload shapes.
   *
   * @param nativeInput the native Codex hook payload
   * @return the command, or an empty string if no command is present
   */
  static String command(JsonNode nativeInput)
  {
    JsonNode arguments = HookJson.objectValue(nativeInput.get("arguments"));
    JsonNode input = HookJson.objectValue(nativeInput.get("input"));
    JsonNode toolInput = HookJson.objectValue(nativeInput.get("tool_input"));
    return HookJson.firstString(arguments.get("cmd"), arguments.get("command"), input.get("cmd"), input.get("command"),
      toolInput.get("cmd"), toolInput.get("command"), nativeInput.get("cmd"), nativeInput.get("command"));
  }
}
