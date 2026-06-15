/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import io.github.cowwoc.cat.agent.AgentScope;
import io.github.cowwoc.cat.agent.FileContentCache;
import io.github.cowwoc.cat.agent.FrontmatterUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Extracts metadata from skill and agent files.
 * <p>
 * Provides operations for resolving model, effort, test directory, and body content from
 * engine-specific skill, rule, and agent files.
 */
final class SkillMetadataExtractor
{
  private final YAMLMapper yamlMapper;
  private final SprtMetadataResolver metadataResolver;

  /**
   * Creates a new SkillMetadataExtractor.
   *
   * @param scope the plugin scope providing JSON mapper and other services
   * @param metadataResolver engine-specific metadata resolver
   * @throws NullPointerException if any argument is null
   */
  SkillMetadataExtractor(AgentScope scope, SprtMetadataResolver metadataResolver)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(metadataResolver, "metadataResolver").isNotNull();
    this.yamlMapper = scope.getYamlMapper();
    this.metadataResolver = metadataResolver;
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
        "SprtRunner extract-units: expected 1 argument <skill_path>, got " + args.length + ".\n" +
        "Usage: skill-test-runner extract-units <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "SprtRunner extract-units: file not found: " + skillPath);
    return bodyWithLineNumbers(skillPath);
  }

  /**
   * Implements the {@code extract-model} command.
   * <p>
   * Delegates engine-specific model resolution to the resolver supplied by the active engine.
   *
   * @param args {@code [skill_path]}
   * @return the fully-qualified model identifier
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException              if the file cannot be read
   */
  String extractModel(String[] args) throws IOException
  {
    return resolveMetadata(args, "extract-model").modelId();
  }

  /**
   * Implements the {@code extract-effort} command.
   * <p>
   * Delegates engine-specific effort resolution to the resolver supplied by the active engine.
   *
   * @param args {@code [skill_path]}
   * @return the effort level (e.g., {@code "high"}), or the default test effort if not specified
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException              if the file cannot be read
   */
  String extractEffort(String[] args) throws IOException
  {
    return resolveMetadata(args, "extract-effort").effort();
  }

  /**
   * Implements the {@code extract-config-source} command.
   *
   * @param args {@code [skill_path]}
   * @return {@code owner}, {@code default}, or {@code frontmatter}
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException if the file cannot be read
   */
  String extractConfigSource(String[] args) throws IOException
  {
    return resolveMetadata(args, "extract-config-source").source();
  }

  private SprtMetadataResolver.ResolvedConfig resolveMetadata(String[] args, String commandName)
    throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "SprtRunner " + commandName + ": expected 1 argument <skill_path>, got " +
        args.length + ".\nUsage: skill-test-runner " + commandName + " <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "SprtRunner " + commandName + ": file not found: " + skillPath);
    return metadataResolver.resolve(skillPath, parseFrontmatterNode(skillPath));
  }

  /**
   * Implements the {@code extract-test-dir} command.
   * <p>
   * Computes the test directory path for a given instruction file path. Maps plugin-relative paths by
   * stripping the {@code client/plugin/} prefix, then prefixes with {@code {projectDir}/client/plugin/tests/}.
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
        "SprtRunner extract-test-dir: expected 2 arguments <instruction-path> <project-dir>, got " +
        args.length + ".\nUsage: skill-test-runner extract-test-dir " +
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

    // Strip "client/plugin/" prefix for plugin files so tests mirror the plugin/ structure.
    String testRelative;
    if (noExtension.startsWith("client/plugin/"))
      testRelative = noExtension.substring("client/plugin/".length());
    else
      testRelative = noExtension;

    return projectDir + "/client/plugin/tests/" + testRelative;
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
    String content = FileContentCache.readString(skillPath);
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
    List<String> lines = FileContentCache.readString(skillPath).lines().toList();
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
