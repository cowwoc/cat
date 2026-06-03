/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extracts scalar control values from adversarial hardening artifacts.
 * <p>
 * This keeps orchestration prompts from relying on non-portable shell JSON tools or loading full
 * findings reports into agent context.
 */
public final class AdversarialState
{
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  /**
   * Prevents instantiation.
   */
  private AdversarialState()
  {
  }

  /**
   * Runs a subcommand against JSON text.
   *
   * @param command the subcommand to run
   * @param json    the JSON content
   * @return the scalar command result
   * @throws IOException if JSON parsing fails
   */
  public static String run(String command, String json) throws IOException
  {
    JsonNode root = MAPPER.readTree(json);
    return switch (command)
    {
      case "has-critical-high" -> String.valueOf(hasSeverity(root, "CRITICAL", "HIGH"));
      case "has-medium-low" -> String.valueOf(hasSeverity(root, "MEDIUM", "LOW"));
      case "has-new-disputes" -> String.valueOf(hasNewDisputes(root));
      case "rejected-count" -> String.valueOf(rejectedCount(root));
      default -> throw new IllegalArgumentException("Unknown command: " + command);
    };
  }

  private static boolean hasSeverity(JsonNode root, String firstSeverity, String secondSeverity)
  {
    JsonNode loopholes = root.path("loopholes");
    if (!loopholes.isArray())
      return false;
    for (JsonNode loophole: loopholes)
    {
      String severity = loophole.path("severity").asString("");
      if (severity.equals(firstSeverity) || severity.equals(secondSeverity))
        return true;
    }
    return false;
  }

  private static boolean hasNewDisputes(JsonNode root)
  {
    JsonNode disputed = root.path("disputed");
    if (!disputed.isArray())
      return false;
    for (JsonNode entry: disputed)
    {
      String verdict = entry.path("arbitration_verdict").asString("");
      if (!verdict.equals("upheld"))
        return true;
    }
    return false;
  }

  private static int rejectedCount(JsonNode root)
  {
    JsonNode rejectedCount = root.path("rejected_count");
    if (rejectedCount.isIntegralNumber())
      return rejectedCount.asInt();
    JsonNode rejected = root.path("rejected");
    if (rejected.isArray())
      return rejected.size();
    return 0;
  }

  /**
   * Entry point.
   *
   * @param args command-line arguments: {@code <command> [json-file]}. If {@code json-file} is omitted,
   *             JSON is read from stdin.
   * @throws IOException if reading input or parsing JSON fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h"))
    {
      System.out.println("""
        Usage: adversarial-state <command> [json-file]

        Commands:
          has-critical-high  Print true if findings.json has CRITICAL or HIGH loopholes
          has-medium-low     Print true if findings.json has MEDIUM or LOW loopholes
          has-new-disputes   Print true if findings.json has disputes not upheld by arbitration
          rejected-count     Print rejected_count from arbitration report, or rejected array size

        If json-file is omitted, JSON is read from stdin.""");
      return;
    }
    if (args.length > 2)
      throw new IllegalArgumentException("Usage: adversarial-state <command> [json-file]");

    String json;
    if (args.length == 2)
      json = Files.readString(Path.of(args[1]));
    else
      json = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    System.out.println(run(args[0], json));
  }
}
