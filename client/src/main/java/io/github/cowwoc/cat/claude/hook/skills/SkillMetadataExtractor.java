/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.util.FrontmatterUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Extracts metadata from skill and subagent files.
 * <p>
 * Provides operations for reading model, effort, test directory, and body content
 * from YAML-frontmatter skill files.
 */
final class SkillMetadataExtractor
{
  private final String claudeCodeVersion;
  private final YAMLMapper yamlMapper;

  /**
   * Creates a new SkillMetadataExtractor.
   *
   * @param scope             the Claude plugin scope providing JSON mapper and other services
   * @param claudeCodeVersion the Claude Code version string for model ID resolution
   * @throws NullPointerException if any argument is null
   */
  SkillMetadataExtractor(JvmScope scope, String claudeCodeVersion)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(claudeCodeVersion, "claudeCodeVersion").isNotNull();
    this.claudeCodeVersion = claudeCodeVersion;
    this.yamlMapper = scope.getYamlMapper();
  }

  /**
   * Implements the {@code extract-units} command.
   * <p>
   * Returns the body of the skill file with original line numbers prepended (tab-separated).
   *
   * @param args {@code [skill_path]}
   * @return the line-numbered body text
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException              if the file cannot be read
   */
  String extractUnits(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-units: expected 1 argument <skill_path>, got " + args.length + ".\n" +
        "Usage: skill-test-runner extract-units <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-units: file not found: " + skillPath);
    return bodyWithLineNumbers(skillPath);
  }

  /**
   * Implements the {@code extract-model} command.
   * <p>
   * Reads the YAML frontmatter of the skill and returns the fully-qualified model identifier.
   * The short name from the {@code model:} field is resolved via {@link ModelIdResolver}.
   * Falls back to {@code "haiku"} (resolved to its fully-qualified ID) when the field is absent.
   *
   * @param args {@code [skill_path]}
   * @return the fully-qualified model identifier
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException              if the file cannot be read
   */
  String extractModel(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-model: expected 1 argument <skill_path>, got " + args.length + ".\n" +
        "Usage: skill-test-runner extract-model <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-model: file not found: " + skillPath);

    String model = extractStringField(skillPath, "model");
    if (model.isBlank())
      model = "haiku";
    return ModelIdResolver.resolve(claudeCodeVersion, model);
  }

  /**
   * Implements the {@code extract-effort} command.
   * <p>
   * Reads the YAML frontmatter of the skill file and returns the {@code effort:} field value,
   * or an empty string if the field is absent.
   *
   * @param args {@code [skill_path]}
   * @return the effort level (e.g., {@code "high"}), or {@code ""} if not specified
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException              if the file cannot be read
   */
  String extractEffort(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-effort: expected 1 argument <skill_path>, got " +
        args.length + ".\n" +
        "Usage: instruction-test-runner extract-effort <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-effort: file not found: " + skillPath);

    return extractStringField(skillPath, "effort");
  }

  /**
   * Implements the {@code extract-test-dir} command.
   * <p>
   * Computes the test directory path for a given instruction file path. Maps plugin-relative paths by
   * stripping the {@code plugin/} prefix, then prefixes with {@code {projectDir}/plugin/tests/}.
   *
   * @param args {@code [instruction-text-path, project-dir]} where {@code instruction-text-path} is
   *             worktree-relative
   * @return the absolute test directory path (no trailing slash)
   * @throws IllegalArgumentException if the wrong number of arguments is supplied
   */
  String extractTestDir(String[] args)
  {
    if (args.length != 2)
      throw new IllegalArgumentException(
        "InstructionTestRunner extract-test-dir: expected 2 arguments <instruction-path> <project-dir>, got " +
        args.length + ".\nUsage: instruction-test-runner extract-test-dir " +
        "<instruction-text-path> <project-dir>");
    String instructionPath = args[0];
    String projectDir = args[1];

    // Strip file extension
    int dotIndex = instructionPath.lastIndexOf('.');
    String noExtension;
    if (dotIndex > 0 && dotIndex > instructionPath.lastIndexOf('/'))
      noExtension = instructionPath.substring(0, dotIndex);
    else
      noExtension = instructionPath;

    // Strip "plugin/" prefix for plugin files so tests mirror the plugin/ structure
    String testRelative;
    if (noExtension.startsWith("plugin/"))
      testRelative = noExtension.substring("plugin/".length());
    else
      testRelative = noExtension;

    return projectDir + "/plugin/tests/" + testRelative;
  }

  /**
   * Parses the YAML frontmatter of a skill file into a JSON node for multiple field lookups.
   *
   * @param skillPath the path to the skill file
   * @return the parsed frontmatter as a {@link JsonNode}, or an empty object node if the file has no
   *         frontmatter or parsing fails
   * @throws IOException if the file cannot be read
   */
  JsonNode parseFrontmatterNode(Path skillPath) throws IOException
  {
    String content = Files.readString(skillPath, UTF_8);
    String frontmatter = FrontmatterUtils.extractFrontmatter(content);
    if (frontmatter == null || frontmatter.isBlank())
      return yamlMapper.createObjectNode();
    try
    {
      return yamlMapper.readTree(frontmatter);
    }
    catch (Exception _)
    {
      return yamlMapper.createObjectNode();
    }
  }

  /**
   * Extracts a string field from an already-parsed frontmatter node.
   *
   * @param frontmatter the parsed frontmatter node (from {@link #parseFrontmatterNode})
   * @param fieldName   the YAML field name to extract
   * @return the field value, or an empty string if the field is absent
   */
  String extractStringField(JsonNode frontmatter, String fieldName)
  {
    JsonNode node = frontmatter.get(fieldName);
    if (node == null || node.isNull() || node.isMissingNode())
      return "";
    return node.asString("");
  }

  /**
   * Extracts a string field from the YAML frontmatter of a skill file.
   *
   * @param skillPath the path to the skill file
   * @param fieldName the YAML field name to extract
   * @return the field value, or an empty string if the field is absent or the file has no frontmatter
   * @throws IOException if the file cannot be read
   */
  String extractStringField(Path skillPath, String fieldName) throws IOException
  {
    return extractStringField(parseFrontmatterNode(skillPath), fieldName);
  }

  /**
   * Produces a tab-separated line-numbered representation of a skill's body, using original
   * file line numbers (i.e., offset by the frontmatter line count).
   *
   * @param skillPath path to the skill file
   * @return the line-numbered body text
   * @throws IOException if the file cannot be read
   */
  private String bodyWithLineNumbers(Path skillPath) throws IOException
  {
    List<String> lines = Files.readAllLines(skillPath, UTF_8);
    int bodyStart = 0;
    if (!lines.isEmpty() && lines.get(0).equals("---"))
    {
      for (int i = 1; i < lines.size(); i += 1)
      {
        if (lines.get(i).equals("---"))
        {
          bodyStart = i + 1;
          break;
        }
      }
    }

    StringBuilder result = new StringBuilder();
    for (int i = bodyStart; i < lines.size(); i += 1)
    {
      int originalLineNumber = i + 1;
      result.append(originalLineNumber).append('\t').append(lines.get(i)).append('\n');
    }
    return result.toString().stripTrailing();
  }
}
