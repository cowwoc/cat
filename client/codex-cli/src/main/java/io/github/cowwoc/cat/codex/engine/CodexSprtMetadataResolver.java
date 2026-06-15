/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.engine;

import io.github.cowwoc.cat.agent.FileContentCache;
import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.skills.SprtMetadataResolver;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Resolves SPRT model/effort metadata for Codex.
 */
public final class CodexSprtMetadataResolver implements SprtMetadataResolver
{
  private static final String DEFAULT_TEST_MODEL = "gpt-5.4-mini";
  private static final String DEFAULT_TEST_EFFORT = "low";
  /**
   * CAT's Codex test-runner model ranking, weakest to strongest.
   * <p>
   * Owner resolution compares whole owner configs: model rank dominates effort rank, and effort
   * is the tie-breaker for owners using the same model.
   */
  private static final List<String> MODEL_STRENGTH =
    List.of("gpt-5.4-mini", "gpt-5.4", "gpt-5.5");
  private static final List<String> EFFORT_STRENGTH =
    List.of("low", "medium", "high", "xhigh");
  private final Path pluginRoot;

  /**
   * Creates a new resolver.
   *
   * @param scope the Codex CLI scope
   */
  public CodexSprtMetadataResolver(CliTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.pluginRoot = scope.getPluginRoot();
  }

  @Override
  public ResolvedConfig resolve(Path instructionPath, JsonNode frontmatter) throws IOException
  {
    requireThat(instructionPath, "instructionPath").isNotNull();
    requireThat(frontmatter, "frontmatter").isNotNull();
    List<ModelEffort> owners = findInvokerAgentConfigs(instructionPath, frontmatter);
    if (owners.isEmpty())
    {
      return new ResolvedConfig(DEFAULT_TEST_MODEL, DEFAULT_TEST_EFFORT,
        CONFIG_SOURCE_DEFAULT);
    }
    ModelEffort weakest = weakestModelEffort(owners);
    return new ResolvedConfig(weakest.modelId(), weakest.effort(), CONFIG_SOURCE_OWNER);
  }

  /**
   * Finds Codex agent configs that own or invoke the instruction under test.
   *
   * @param instructionPath the instruction under test
   * @param frontmatter the instruction frontmatter
   * @return matching owner configs
   * @throws IOException if a descriptor cannot be read
   */
  private List<ModelEffort> findInvokerAgentConfigs(Path instructionPath, JsonNode frontmatter)
    throws IOException
  {
    Path relativePath = pluginRelativePath(instructionPath);
    if (relativePath == null)
      return List.of();

    if (relativePath.startsWith(Path.of("agents/codex")) && isFileType(relativePath, ".toml"))
      return List.of(readAgentConfig(pluginRoot.resolve(relativePath)));
    if (relativePath.startsWith(Path.of("agents/common")) && isFileType(relativePath, ".md"))
      return readAgentConfigsForCommonBody(pluginRoot.resolve(relativePath));
    if (relativePath.startsWith(Path.of("agents/claude")) && isFileType(relativePath, ".md"))
      return optionalConfigList(readAgentConfigByStem(fileStem(relativePath)));
    if (relativePath.startsWith(Path.of("rules")))
      return findRuleAgentConfigs(instructionPath, frontmatter);
    if (relativePath.startsWith(Path.of("skills")))
      return findSkillInvokerConfigs(relativePath);
    return List.of();
  }

  /**
   * Converts an instruction path to a plugin-relative path when possible.
   *
   * @param instructionPath the instruction under test
   * @return the plugin-relative path, or null if the path is outside the plugin
   */
  private Path pluginRelativePath(Path instructionPath)
  {
    Path absolutePath = instructionPath.toAbsolutePath().normalize();
    Path absolutePluginRoot = pluginRoot.toAbsolutePath().normalize();
    if (absolutePath.startsWith(absolutePluginRoot))
      return absolutePluginRoot.relativize(absolutePath);

    String normalized = absolutePath.toString().replace('\\', '/');
    String marker = "/client/plugin/";
    int markerIndex = normalized.indexOf(marker);
    if (markerIndex < 0)
      return null;
    return Path.of(normalized.substring(markerIndex + marker.length()));
  }

  /**
   * Resolves Codex agent configs from rule audience frontmatter.
   *
   * @param rulePath the rule under test
   * @param frontmatter the rule frontmatter
   * @return matching owner configs
   * @throws IOException if a descriptor cannot be read
   */
  private List<ModelEffort> findRuleAgentConfigs(Path rulePath, JsonNode frontmatter)
    throws IOException
  {
    if (frontmatter.has("mainAgent") || frontmatter.has("subAgents"))
    {
      throw new IllegalArgumentException("Legacy rule audience frontmatter is not supported in " +
        rulePath + ": use agents instead of mainAgent/subAgents");
    }
    JsonNode agents = frontmatter.get("agents");
    if (agents == null || agents.isMissingNode() || agents.isNull())
      return readAllAgentConfigs();
    if (!agents.isArray())
      throw new IllegalArgumentException("agents must be a non-empty YAML list in " + rulePath);
    if (agents.isEmpty())
      throw new IllegalArgumentException("agents must not be empty in " + rulePath);

    List<ModelEffort> matches = new ArrayList<>();
    boolean allSubagents = false;
    boolean hasSpecificSubagents = false;
    for (JsonNode agent: agents)
    {
      if (!agent.isString())
        throw new IllegalArgumentException("agents values must be non-blank strings in " + rulePath);
      String value = agent.asString("").strip();
      if (value.isBlank())
        throw new IllegalArgumentException("agents values must be non-blank strings in " + rulePath);
      if (value.equals("main"))
        continue;
      if (value.equals("subagents"))
      {
        allSubagents = true;
        continue;
      }
      if (allSubagents)
      {
        throw new IllegalArgumentException("agents must not combine \"subagents\" with specific " +
          "subagent names in " + rulePath);
      }
      hasSpecificSubagents = true;
      String agentName = normalizeAgentName(value);
      if (agentName.isBlank())
        continue;
      ModelEffort config = readAgentConfigByStem(agentName);
      if (config != null)
        matches.add(config);
    }
    if (allSubagents)
    {
      if (hasSpecificSubagents)
      {
        throw new IllegalArgumentException("agents must not combine \"subagents\" with specific " +
          "subagent names in " + rulePath);
      }
      return readAllAgentConfigs();
    }
    return matches;
  }

  /**
   * Reads all Codex agent configs from the plugin.
   *
   * @return every Codex agent config
   * @throws IOException if a descriptor cannot be read
   */
  private List<ModelEffort> readAllAgentConfigs() throws IOException
  {
    Path codexAgentsDir = pluginRoot.resolve("agents/codex");
    if (!Files.isDirectory(codexAgentsDir))
      return List.of();
    List<ModelEffort> result = new ArrayList<>();
    try (Stream<Path> stream = Files.list(codexAgentsDir))
    {
      for (Path agentPath: stream.
        filter(path -> isFileType(path, ".toml")).
        sorted().
        toList())
      {
        result.add(readAgentConfig(agentPath));
      }
    }
    return result;
  }

  /**
   * Finds Codex agent configs for common agents that invoke the skill under test.
   *
   * @param relativePath the plugin-relative skill path
   * @return matching owner configs
   * @throws IOException if a descriptor cannot be read
   */
  private List<ModelEffort> findSkillInvokerConfigs(Path relativePath) throws IOException
  {
    if (relativePath.getNameCount() < 3)
      return List.of();
    String skillName = relativePath.getName(2).toString();
    Path commonAgentsDir = pluginRoot.resolve("agents/common");
    if (!Files.isDirectory(commonAgentsDir))
      return List.of();

    List<ModelEffort> matches = new ArrayList<>();
    try (Stream<Path> stream = Files.list(commonAgentsDir))
    {
      for (Path agentBody: stream.
        filter(path -> isFileType(path, ".md")).
        sorted().
        toList())
      {
        String content = FileContentCache.readString(agentBody);
        if (containsSkillReference(content, skillName))
          matches.addAll(readAgentConfigsForCommonBody(agentBody));
      }
    }
    return matches;
  }

  /**
   * Returns the weakest model/effort pair from the supplied owner configs.
   *
   * @param configs owner configs to compare
   * @return the weakest config
   */
  private ModelEffort weakestModelEffort(List<ModelEffort> configs)
  {
    if (configs.isEmpty())
      throw new IllegalArgumentException("configs must not be empty");
    ModelEffort result = configs.getFirst();
    for (ModelEffort candidate: configs.subList(1, configs.size()))
    {
      if (isWeakerThan(candidate, result))
        result = candidate;
    }
    return result;
  }

  /**
   * Returns true if the candidate config is weaker than the current config.
   *
   * @param candidate the candidate config
   * @param current the current weakest config
   * @return true if candidate is weaker
   */
  private static boolean isWeakerThan(ModelEffort candidate, ModelEffort current)
  {
    int candidateModelRank = modelStrengthRank(candidate.modelId());
    int currentModelRank = modelStrengthRank(current.modelId());
    if (candidateModelRank != currentModelRank)
      return candidateModelRank < currentModelRank;
    int candidateEffortRank = effortStrengthRank(candidate.effort());
    int currentEffortRank = effortStrengthRank(current.effort());
    if (candidateEffortRank != currentEffortRank)
      return candidateEffortRank < currentEffortRank;
    int modelComparison = candidate.modelId().compareTo(current.modelId());
    if (modelComparison != 0)
      return modelComparison < 0;
    return candidate.effort().compareTo(current.effort()) < 0;
  }

  /**
   * Returns the configured strength rank for a Codex model id.
   *
   * @param modelId the model id
   * @return the model strength rank
   */
  private static int modelStrengthRank(String modelId)
  {
    int result = MODEL_STRENGTH.indexOf(modelId);
    if (result >= 0)
      return result;
    throw new IllegalStateException(
      "No Codex model strength rank configured for '" + modelId + "'. Known values: " +
        MODEL_STRENGTH);
  }

  /**
   * Returns the configured strength rank for a Codex effort value.
   *
   * @param effort the effort value
   * @return the effort strength rank
   */
  private static int effortStrengthRank(String effort)
  {
    int result = EFFORT_STRENGTH.indexOf(effort);
    if (result >= 0)
      return result;
    throw new IllegalStateException(
      "No Codex effort strength rank configured for '" + effort + "'. Known values: " +
        EFFORT_STRENGTH);
  }

  /**
   * Converts a nullable config to a list.
   *
   * @param config the config, or null
   * @return an empty or singleton list
   */
  private static List<ModelEffort> optionalConfigList(ModelEffort config)
  {
    if (config == null)
      return List.of();
    return List.of(config);
  }

  /**
   * Reads a Codex agent config by descriptor file stem.
   *
   * @param agentName the descriptor file stem
   * @return the config, or null if no descriptor exists
   * @throws IOException if the descriptor cannot be read
   */
  private ModelEffort readAgentConfigByStem(String agentName) throws IOException
  {
    Path agentPath = pluginRoot.resolve("agents/codex").resolve(agentName + ".toml");
    if (!Files.exists(agentPath))
      return null;
    return readAgentConfig(agentPath);
  }

  /**
   * Reads Codex wrapper configs that map to a shared common agent body.
   *
   * @param commonBodyPath the shared common agent body path
   * @return matching wrapper configs
   * @throws IOException if a descriptor cannot be read
   */
  private List<ModelEffort> readAgentConfigsForCommonBody(Path commonBodyPath) throws IOException
  {
    Path codexAgentsDir = pluginRoot.resolve("agents/codex");
    if (!Files.isDirectory(codexAgentsDir))
      return List.of();

    List<ModelEffort> result = new ArrayList<>();
    String sameStem = fileStem(commonBodyPath);
    ModelEffort sameStemConfig = readAgentConfigByStem(sameStem);
    if (sameStemConfig != null)
      result.add(sameStemConfig);

    String includeNeedle = "../common/" + commonBodyPath.getFileName();
    try (Stream<Path> stream = Files.list(codexAgentsDir))
    {
      for (Path agentPath: stream.
        filter(path -> isFileType(path, ".toml")).
        sorted().
        toList())
      {
        if (fileStem(agentPath).equals(sameStem))
          continue;
        String content = FileContentCache.readString(agentPath);
        if (content.contains(includeNeedle))
          result.add(readAgentConfig(agentPath));
      }
    }
    return result;
  }

  /**
   * Reads the model/effort pair from a Codex TOML agent descriptor.
   *
   * @param agentPath the agent descriptor path
   * @return the descriptor model/effort pair
   * @throws IOException if the descriptor cannot be read
   */
  private ModelEffort readAgentConfig(Path agentPath) throws IOException
  {
    String model = extractTomlStringField(agentPath, "model");
    String effort = extractTomlStringField(agentPath, "model_reasoning_effort");
    if (model.isBlank() || effort.isBlank())
    {
      throw new IllegalStateException(
        "Codex agent descriptor is missing model or model_reasoning_effort: " + agentPath);
    }
    return new ModelEffort(model, effort);
  }

  /**
   * Extracts a simple string field from a TOML descriptor.
   *
   * @param filePath the TOML file path
   * @param fieldName the field to extract
   * @return the field value, or blank if absent
   * @throws IOException if the file cannot be read
   */
  private static String extractTomlStringField(Path filePath, String fieldName) throws IOException
  {
    for (String line: FileContentCache.readString(filePath).split("\\R"))
    {
      String trimmed = line.strip();
      if (trimmed.isEmpty() || trimmed.startsWith("#"))
        continue;
      int equalsIndex = trimmed.indexOf('=');
      if (equalsIndex < 0)
        continue;
      String key = trimmed.substring(0, equalsIndex).strip();
      if (!key.equals(fieldName))
        continue;
      return parseTomlStringValue(trimmed.substring(equalsIndex + 1).strip());
    }
    return "";
  }

  /**
   * Parses a TOML scalar string value.
   *
   * @param rawValue the raw TOML value
   * @return the parsed scalar value
   */
  private static String parseTomlStringValue(String rawValue)
  {
    if (rawValue.isEmpty())
      return "";
    char quote = rawValue.charAt(0);
    if (quote == '"' || quote == '\'')
    {
      int closingQuote = rawValue.indexOf(quote, 1);
      if (closingQuote < 0)
        return "";
      return rawValue.substring(1, closingQuote);
    }
    int commentIndex = rawValue.indexOf('#');
    String withoutComment = rawValue;
    if (commentIndex >= 0)
      withoutComment = rawValue.substring(0, commentIndex);
    return withoutComment.strip();
  }

  /**
   * Normalizes supported rule agent name spellings to descriptor stems.
   *
   * @param rawName the raw rule agent name
   * @return the normalized descriptor stem
   */
  private static String normalizeAgentName(String rawName)
  {
    String result = rawName.strip();
    if (result.startsWith("cat:"))
      result = result.substring("cat:".length());
    else if (result.startsWith("cat-"))
      result = result.substring("cat-".length());
    return result;
  }

  /**
   * Returns true if an agent body references the named CAT skill.
   *
   * @param content the agent body content
   * @param skillName the CAT skill name without the cat: prefix
   * @return true if the skill is referenced
   */
  private static boolean containsSkillReference(String content, String skillName)
  {
    Pattern pattern = Pattern.compile("(?<![A-Za-z0-9_-])/?cat:" +
      Pattern.quote(skillName) + "(?![A-Za-z0-9_-])");
    return pattern.matcher(content).find();
  }

  /**
   * Returns true if the path has the expected extension.
   *
   * @param path the path to check
   * @param extension the expected extension
   * @return true if the filename ends with the extension
   */
  private static boolean isFileType(Path path, String extension)
  {
    return path.getFileName().toString().endsWith(extension);
  }

  /**
   * Returns the filename without its final extension.
   *
   * @param path the path to inspect
   * @return the file stem
   */
  private static String fileStem(Path path)
  {
    String fileName = path.getFileName().toString();
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex < 0)
      return fileName;
    return fileName.substring(0, dotIndex);
  }
}
