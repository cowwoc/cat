/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.hook.util.SkillOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Prepares a skill validation prompt by reading the skill's description frontmatter and
 * formatting the test prompts for evaluation by the skill-validator-agent subagent.
 * <p>
 * Accepts two arguments:
 * <ol>
 *   <li>Path to the skill's SKILL.md file</li>
 *   <li>JSON object containing {@code should_trigger} and {@code should_not_trigger} arrays</li>
 * </ol>
 * Outputs a formatted validation request for the agent to evaluate.
 */
public final class SkillValidator implements SkillOutput
{
  /**
   * Pattern to extract quoted strings from JSON arrays.
   */
  private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");

  /**
   * Creates a SkillValidator instance.
   *
   * @param scope the ClaudeTool for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public SkillValidator(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
  }

  /**
   * Generates a formatted skill validation prompt.
   * <p>
   * Reads the skill description from the provided SKILL.md path and formats the test prompts
   * into a structured evaluation request.
   *
   * @param args two arguments: [skill-path, test-prompts-json]
   * @return a formatted validation prompt, or an error message if inputs are invalid
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if the wrong number of arguments is provided
   * @throws IOException              if the skill file cannot be read
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
      throw new IllegalArgumentException(
        "SkillValidator requires 2 arguments: [skill-path, test-prompts-json]. " +
        "Got " + args.length + " argument(s).");

    String skillPath = args[0];
    String testPromptsJson = args[1];

    // Read skill description from frontmatter
    Path skillFile = Path.of(skillPath);
    if (!Files.exists(skillFile))
      throw new IOException("Skill file not found: " + skillPath +
        ". Provide an absolute or relative path to a SKILL.md file.");

    String skillContent = Files.readString(skillFile);
    String description = SkillFrontmatter.extractDescription(skillContent, skillPath);

    // Parse test prompts from JSON
    List<String> shouldTrigger = extractJsonArray(testPromptsJson, "should_trigger");
    List<String> shouldNotTrigger = extractJsonArray(testPromptsJson, "should_not_trigger");

    return formatValidationPrompt(description, shouldTrigger, shouldNotTrigger);
  }

  /**
   * Extracts a JSON array of strings by key from a simple JSON object.
   * <p>
   * Supports simple JSON arrays containing only quoted string values. Does not support
   * nested objects, arrays within arrays, or other complex JSON structures.
   *
   * @param json the JSON object string
   * @param key  the key whose array value to extract
   * @return the list of string values, or an empty list if the key is not found
   */
  public List<String> extractJsonArray(String json, String key)
  {
    List<String> results = new ArrayList<>();

    // Find the key and its array value
    String keyPattern = "\"" + key + "\"";
    int keyIndex = json.indexOf(keyPattern);
    if (keyIndex < 0)
      return results;

    // Find the opening bracket of the array
    int bracketOpen = json.indexOf('[', keyIndex);
    if (bracketOpen < 0)
      return results;

    int bracketClose = json.indexOf(']', bracketOpen);
    if (bracketClose < 0)
      return results;

    // Extract all quoted strings within the array
    String arrayContent = json.substring(bracketOpen, bracketClose + 1);
    Matcher matcher = QUOTED_STRING_PATTERN.matcher(arrayContent);
    while (matcher.find())
    {
      String quoted = matcher.group();
      // Remove surrounding quotes and unescape
      String value = quoted.substring(1, quoted.length() - 1).
        replace("\\\"", "\"").
        replace("\\\\", "\\").
        replace("\\n", "\n").
        replace("\\t", "\t");
      results.add(value);
    }
    return results;
  }

  /**
   * Formats the validation prompt from the extracted inputs.
   *
   * @param description      the skill's description frontmatter text
   * @param shouldTrigger    prompts that should activate the skill
   * @param shouldNotTrigger prompts that should not activate the skill
   * @return the formatted validation prompt
   */
  private String formatValidationPrompt(String description, List<String> shouldTrigger,
    List<String> shouldNotTrigger)
  {
    String triggerLines = buildPromptLines(shouldTrigger);
    String nonTriggerLines = buildPromptLines(shouldNotTrigger);
    return """
      SKILL VALIDATION REQUEST
      ========================

      Description under test:
        %s

      Should-Trigger Prompts (skill SHOULD activate for these):
      %s
      Should-Not-Trigger Prompts (skill should NOT activate for these):
      %s
      For each prompt, determine whether the description would route it to this skill.
      Record PASS or FAIL with a one-sentence explanation.
      """.formatted(description, triggerLines, nonTriggerLines);
  }

  /**
   * Builds a bulleted list of prompt lines, or a placeholder if the list is empty.
   *
   * @param prompts the list of prompts to format
   * @return the formatted prompt lines
   */
  private String buildPromptLines(List<String> prompts)
  {
    if (prompts.isEmpty())
      return "  (none provided)\n";
    StringBuilder sb = new StringBuilder(prompts.size() * 40);
    for (String prompt : prompts)
      sb.append("  - \"").append(prompt).append("\"\n");
    return sb.toString();
  }
}
