/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import io.github.cowwoc.cat.agent.AgentScope;
import io.github.cowwoc.cat.agent.AgentPluginScope;
import io.github.cowwoc.cat.agent.FrontmatterUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Extracts metadata from skill and agent files.
 * <p>
 * Provides operations for resolving model, effort, test directory, and body content from
 * engine-specific skill, rule, and agent files.
 */
final class SkillMetadataExtractor
{
  private static final String CONFIG_SOURCE_DEFAULT = "default";
  private static final String CONFIG_SOURCE_OWNER = "owner";
  private static final String CONFIG_SOURCE_FRONTMATTER = "frontmatter";
  private static final String DEFAULT_CLAUDE_TEST_MODEL = "haiku";
  private static final String DEFAULT_CODEX_TEST_MODEL = "gpt-5.4-mini";
  private static final String DEFAULT_TEST_EFFORT = "low";
  /**
   * CAT's Codex test-runner model ranking, weakest to strongest.
   * <p>
   * Owner resolution compares whole owner configs: model rank dominates effort rank, and effort
   * is the tie-breaker for owners using the same model. Keep this table explicit so new CAT
   * agent model IDs cannot silently change "weakest" selection semantics.
   */
  private static final List<String> CODEX_MODEL_STRENGTH = List.of(
    "gpt-5.4-mini",
    "gpt-5.3-codex-spark",
    "gpt-5.3-codex",
    "gpt-5.4",
    "gpt-5.5");
  private static final List<String> CODEX_EFFORT_STRENGTH =
    List.of("low", "medium", "high", "xhigh");
  private final String claudeCodeVersion;
  private final YAMLMapper yamlMapper;
  private final String runtimeId;
  private final Path pluginRoot;

  /**
   * Creates a new SkillMetadataExtractor.
   *
   * @param scope             the Claude plugin scope providing JSON mapper and other services
   * @param claudeCodeVersion the Claude Code version string for model ID resolution
   * @throws NullPointerException if any argument is null
   */
  SkillMetadataExtractor(AgentScope scope, String claudeCodeVersion)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(claudeCodeVersion, "claudeCodeVersion").isNotNull();
    this.claudeCodeVersion = claudeCodeVersion;
    this.yamlMapper = scope.getYamlMapper();
    if (scope instanceof AgentPluginScope pluginScope)
    {
      this.runtimeId = SprtRunner.runtimeIdFromDescriptor(pluginScope.getPluginDescriptor());
      this.pluginRoot = pluginScope.getPluginRoot();
    }
    else
    {
      this.runtimeId = "claude";
      this.pluginRoot = null;
    }
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
   * Resolves the test-runner model for the active engine.
   * <p>
   * Codex skill files do not support model frontmatter, so Codex uses the weakest model/effort
   * combination among matching invoking agents when the instruction path exposes any, otherwise it uses its fixed
   * default. Claude reads the YAML {@code model:} field and resolves short names via {@link ModelIdResolver},
   * falling back to the Claude default when the field is absent.
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
        "SprtRunner extract-model: expected 1 argument <skill_path>, got " + args.length + ".\n" +
        "Usage: skill-test-runner extract-model <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "SprtRunner extract-model: file not found: " + skillPath);

    if (runtimeId.equals("codex"))
      return resolveCodexTestRunnerConfig(skillPath).modelId();

    String model = extractStringField(skillPath, "model");
    if (model.isBlank())
      return defaultTestModel();
    return ModelIdResolver.resolve(claudeCodeVersion, model);
  }

  /**
   * Implements the {@code extract-effort} command.
   * <p>
   * Resolves the test-runner effort for the active engine.
   * <p>
   * Codex skill files do not support effort frontmatter, so Codex uses the weakest model/effort
   * combination among matching invoking agents when the instruction path exposes any, otherwise it uses the fixed
   * default. Claude reads the YAML {@code effort:} field, falling back to the default test effort
   * when the field is absent.
   *
   * @param args {@code [skill_path]}
   * @return the effort level (e.g., {@code "high"}), or the default test effort if not specified
   * @throws IllegalArgumentException if the argument count is wrong or the file is not found
   * @throws IOException              if the file cannot be read
   */
  String extractEffort(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "SprtRunner extract-effort: expected 1 argument <skill_path>, got " +
        args.length + ".\n" +
        "Usage: skill-test-runner extract-effort <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "SprtRunner extract-effort: file not found: " + skillPath);

    if (runtimeId.equals("codex"))
      return resolveCodexTestRunnerConfig(skillPath).effort();

    String effort = extractStringField(skillPath, "effort");
    if (effort.isBlank())
      return DEFAULT_TEST_EFFORT;
    return effort;
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
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "SprtRunner extract-config-source: expected 1 argument <skill_path>, got " +
        args.length + ".\n" +
        "Usage: skill-test-runner extract-config-source <skill_path>");
    Path skillPath = Path.of(args[0]);
    if (Files.notExists(skillPath))
      throw new IllegalArgumentException(
        "SprtRunner extract-config-source: file not found: " + skillPath);

    if (runtimeId.equals("codex"))
      return resolveCodexTestRunnerConfig(skillPath).source();

    String model = extractStringField(skillPath, "model");
    String effort = extractStringField(skillPath, "effort");
    if (!model.isBlank() || !effort.isBlank())
      return CONFIG_SOURCE_FRONTMATTER;
    return CONFIG_SOURCE_DEFAULT;
  }

  private String defaultTestModel()
  {
    if (runtimeId.equals("codex"))
      return DEFAULT_CODEX_TEST_MODEL;
    return ModelIdResolver.resolve(claudeCodeVersion, DEFAULT_CLAUDE_TEST_MODEL);
  }

  private ResolvedTestRunnerConfig resolveCodexTestRunnerConfig(Path instructionPath) throws IOException
  {
    List<ModelEffort> owners = findCodexInvokerAgentConfigs(instructionPath);
    if (owners.isEmpty())
    {
      return new ResolvedTestRunnerConfig(DEFAULT_CODEX_TEST_MODEL, DEFAULT_TEST_EFFORT,
        CONFIG_SOURCE_DEFAULT);
    }
    ModelEffort weakest = weakestModelEffort(owners);
    return new ResolvedTestRunnerConfig(weakest.modelId(), weakest.effort(), CONFIG_SOURCE_OWNER);
  }

  private List<ModelEffort> findCodexInvokerAgentConfigs(Path instructionPath) throws IOException
  {
    Path relativePath = pluginRelativePath(instructionPath);
    if (relativePath == null)
      return List.of();

    if (relativePath.startsWith(Path.of("agents/codex")) && isFileType(relativePath, ".toml"))
      return List.of(readCodexAgentConfig(pluginRoot.resolve(relativePath)));
    if (relativePath.startsWith(Path.of("agents/common")) && isFileType(relativePath, ".md"))
      return optionalConfigList(readCodexAgentConfigByStem(fileStem(relativePath)));
    if (relativePath.startsWith(Path.of("agents/claude")) && isFileType(relativePath, ".md"))
      return optionalConfigList(readCodexAgentConfigByStem(fileStem(relativePath)));
    if (relativePath.startsWith(Path.of("rules")))
      return findCodexRuleAgentConfigs(instructionPath);
    if (relativePath.startsWith(Path.of("skills")))
      return findCodexSkillInvokerConfigs(relativePath);
    return List.of();
  }

  private Path pluginRelativePath(Path instructionPath)
  {
    if (pluginRoot == null)
      return null;
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

  private List<ModelEffort> findCodexRuleAgentConfigs(Path rulePath) throws IOException
  {
    JsonNode subAgents = parseFrontmatterNode(rulePath).get("subAgents");
    if (subAgents == null || subAgents.isMissingNode() || subAgents.isNull())
      return readAllCodexAgentConfigs();
    if (!subAgents.isArray())
      return List.of();

    List<ModelEffort> matches = new ArrayList<>();
    for (JsonNode subAgent: subAgents)
    {
      String agentName = normalizeAgentName(subAgent.asString(""));
      if (agentName.isBlank())
        continue;
      ModelEffort config = readCodexAgentConfigByStem(agentName);
      if (config != null)
        matches.add(config);
    }
    return matches;
  }

  private List<ModelEffort> readAllCodexAgentConfigs() throws IOException
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
        result.add(readCodexAgentConfig(agentPath));
      }
    }
    return result;
  }

  private List<ModelEffort> findCodexSkillInvokerConfigs(Path relativePath) throws IOException
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
        String content = Files.readString(agentBody, UTF_8);
        if (containsSkillReference(content, skillName))
        {
          ModelEffort config = readCodexAgentConfigByStem(fileStem(agentBody));
          if (config != null)
            matches.add(config);
        }
      }
    }
    return matches;
  }

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

  private static int modelStrengthRank(String modelId)
  {
    int result = CODEX_MODEL_STRENGTH.indexOf(modelId);
    if (result >= 0)
      return result;
    throw new IllegalStateException(
      "No Codex model strength rank configured for '" + modelId + "'. Known values: " +
      CODEX_MODEL_STRENGTH);
  }

  private static int effortStrengthRank(String effort)
  {
    int result = CODEX_EFFORT_STRENGTH.indexOf(effort);
    if (result >= 0)
      return result;
    throw new IllegalStateException(
      "No Codex effort strength rank configured for '" + effort + "'. Known values: " +
      CODEX_EFFORT_STRENGTH);
  }

  private static List<ModelEffort> optionalConfigList(ModelEffort config)
  {
    if (config == null)
      return List.of();
    return List.of(config);
  }

  private ModelEffort readCodexAgentConfigByStem(String agentName) throws IOException
  {
    Path agentPath = pluginRoot.resolve("agents/codex").resolve(agentName + ".toml");
    if (!Files.exists(agentPath))
      return null;
    return readCodexAgentConfig(agentPath);
  }

  private ModelEffort readCodexAgentConfig(Path agentPath) throws IOException
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

  private static String extractTomlStringField(Path filePath, String fieldName) throws IOException
  {
    for (String line: Files.readString(filePath, UTF_8).split("\\R"))
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

  private static String normalizeAgentName(String rawName)
  {
    String result = rawName.strip();
    if (result.startsWith("cat:"))
      result = result.substring("cat:".length());
    else if (result.startsWith("cat-"))
      result = result.substring("cat-".length());
    return result;
  }

  private static boolean containsSkillReference(String content, String skillName)
  {
    Pattern pattern = Pattern.compile("(?<![A-Za-z0-9_-])/?cat:" +
      Pattern.quote(skillName) + "(?![A-Za-z0-9_-])");
    return pattern.matcher(content).find();
  }

  private static boolean isFileType(Path path, String extension)
  {
    return path.getFileName().toString().endsWith(extension);
  }

  private static String fileStem(Path path)
  {
    String fileName = path.getFileName().toString();
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex < 0)
      return fileName;
    return fileName.substring(0, dotIndex);
  }

  private record ModelEffort(String modelId, String effort)
  {
  }

  private record ResolvedTestRunnerConfig(String modelId, String effort, String source)
  {
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
