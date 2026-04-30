/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.ClaudePluginScope;
import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Discovers model-invocable skills from all sources.
 * <p>
 * Sources:
 * <ul>
 *   <li>All installed plugins via {@code ${claudeConfigPath}/plugins/installed_plugins.json}</li>
 *   <li>Project commands in {@code ${projectPath}/.claude/commands/}</li>
 *   <li>User skills in {@code ${claudeConfigPath}/skills/}</li>
 * </ul>
 * Each discovered skill is represented as a {@link SkillEntry} containing the qualified name,
 * description, and optional path patterns. Callers can format the entries as needed.
 */
public final class SkillDiscovery
{
  /**
   * A discovered skill entry with a qualified name, description, and optional path patterns.
   *
   * @param name the qualified skill name (e.g. {@code cat:git-commit})
   * @param description the human-readable description from frontmatter
   * @param paths the glob patterns from the {@code paths:} frontmatter field; empty if the skill
   *   has no path restriction
   */
  public record SkillEntry(String name, String description, List<String> paths)
  {
    /**
     * Creates a new SkillEntry.
     *
     * @param name the qualified skill name (e.g. {@code cat:git-commit})
     * @param description the human-readable description from frontmatter
     * @param paths the glob patterns from the {@code paths:} frontmatter field
     * @throws NullPointerException if {@code name}, {@code description}, or {@code paths} are null
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public SkillEntry
    {
      requireThat(name, "name").isNotBlank();
      requireThat(description, "description").isNotNull();
      requireThat(paths, "paths").isNotNull();
      paths = List.copyOf(paths);
    }
  }

  /**
   * Cache of discovered skill entries keyed by {@code claudeConfigPath|projectPath}.
   * <p>
   * Skill discovery is expensive (directory traversal + file reads). Since skills do not change
   * during a JVM lifetime (plugin installs require a restart), results are cached indefinitely
   * within the process. The cache is shared across sessions that use the same config and project.
   * <p>
   * <b>Thread Safety:</b> This class is thread-safe.
   */
  private static final ConcurrentMap<String, List<SkillEntry>> ENTRY_CACHE =
    new ConcurrentHashMap<>();

  private final Path claudeConfigPath;
  private final Path projectPath;
  private final JsonMapper jsonMapper;
  private final YAMLMapper yamlMapper;

  /**
   * Creates a new SkillDiscovery instance.
   *
   * @param scope the plugin scope providing config path, project path, and JSON mapper
   */
  private SkillDiscovery(ClaudePluginScope scope)
  {
    this.claudeConfigPath = scope.getClaudeConfigPath();
    this.projectPath = scope.getProjectPath();
    this.jsonMapper = scope.getJsonMapper();
    this.yamlMapper = scope.getYamlMapper();
  }

  /**
   * Discovers all model-invocable skills from all sources.
   *
   * @return list of discovered skill entries
   * @throws io.github.cowwoc.pouch10.core.WrappedCheckedException if skill discovery fails due to I/O errors
   *   or malformed plugin configuration
   */
  public List<SkillEntry> discoverAll()
  {
    try
    {
      List<SkillEntry> entries = new ArrayList<>();
      entries.addAll(discoverPluginSkills(claudeConfigPath, jsonMapper, yamlMapper));
      entries.addAll(discoverProjectCommands(projectPath, yamlMapper));
      entries.addAll(discoverUserSkills(claudeConfigPath, yamlMapper));
      return entries;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Formats the session-start skills listing for injection into the main agent context.
   * <p>
   * Returns all skills without {@code paths:} frontmatter for injection at session start.
   * Skills with {@code paths:} are excluded here and injected on-demand by
   * {@link io.github.cowwoc.cat.claude.hook.session.InjectPathRestrictedSkillListing}
   * when a matching file is accessed.
   * Each entry uses the format {@code "- name: description"}, matching Claude Code's native skill listing.
   * The header references {@code get-skill} for loading individual skill instructions.
   *
   * @param scope the hook scope providing config path, project path, and JSON mapper
   * @return the formatted session-start skills listing, or an empty string if no qualifying skills are found
   */
  public static String getMainAgentSkillListing(ClaudeHook scope)
  {
    return buildMainAgentSkillListing(scope);
  }

  /**
   * Formats the session-start skills listing for injection into the main agent context.
   * <p>
   * Returns all skills without {@code paths:} frontmatter for injection at session start.
   * Skills with {@code paths:} are excluded here and injected on-demand by
   * {@link io.github.cowwoc.cat.claude.hook.session.InjectPathRestrictedSkillListing}
   * when a matching file is accessed.
   * Each entry uses the format {@code "- name: description"}, matching Claude Code's native skill listing.
   * The header references {@code get-skill} for loading individual skill instructions.
   *
   * @param scope the tool scope providing config path, project path, and JSON mapper
   * @return the formatted session-start skills listing, or an empty string if no qualifying skills are found
   */
  public static String getMainAgentSkillListing(ClaudeTool scope)
  {
    return buildMainAgentSkillListing(scope);
  }

  /**
   * Builds the main agent skill listing from a plugin scope.
   * <p>
   * Shared implementation for both {@link #getMainAgentSkillListing(ClaudeHook)} and
   * {@link #getMainAgentSkillListing(ClaudeTool)}.
   *
   * @param scope the plugin scope providing config path, project path, and JSON mapper
   * @return the formatted session-start skills listing, or an empty string if no qualifying skills are found
   */
  private static String buildMainAgentSkillListing(ClaudePluginScope scope)
  {
    List<SkillEntry> allEntries = new SkillDiscovery(scope).discoverAll();
    List<SkillEntry> sessionEntries = new ArrayList<>();
    for (SkillEntry entry : allEntries)
    {
      if (entry.paths().isEmpty())
        sessionEntries.add(entry);
    }
    if (sessionEntries.isEmpty())
      return "";
    StringBuilder sb = new StringBuilder(512);
    sb.append("The following skills are available. To load a skill's instructions, run via Bash:\n").
      append("  \"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill\" " +
        "\"<skill-name>\" \"<cat-agent-id>\"\n\n");
    appendSkillEntries(sb, sessionEntries);
    return sb.toString();
  }

  /**
   * Formats the skill listing for injection into subagent context.
   * <p>
   * Returns only the dynamic skill list. Behavioral instructions about when and how to invoke skills
   * are provided separately via {@code plugin/rules/subagent-skill-instructions.md}.
   *
   * @param scope the hook scope providing config path, project path, and JSON mapper
   * @return the formatted skill listing, or an empty string if no skills are found
   */
  public static String getSubagentSkillListing(ClaudeHook scope)
  {
    List<SkillEntry> entries = new SkillDiscovery(scope).discoverAll();
    if (entries.isEmpty())
      return "";
    StringBuilder sb = new StringBuilder(512);
    sb.append("**Available skills:**\n");
    appendSkillEntries(sb, entries);
    return sb.toString();
  }

  /**
   * Discovers all model-invocable skills from all sources.
   * <p>
   * Results are cached per (claudeConfigPath, projectPath) pair for the lifetime of the JVM.
   * Skills do not change without restarting the process (plugin installs require restart), so
   * this cache is safe to hold indefinitely.
   *
   * @param scope the hook scope providing config path, project path, and JSON mapper
   * @return list of all discovered skill entries
   * @throws NullPointerException if {@code scope} is null
   */
  public static List<SkillEntry> discoverAllEntries(ClaudeHook scope)
  {
    String cacheKey = scope.getClaudeConfigPath().toString() + "|" + scope.getProjectPath().toString();
    return ENTRY_CACHE.computeIfAbsent(cacheKey, _ -> new SkillDiscovery(scope).discoverAll());
  }

  /**
   * Appends skill entries to a StringBuilder.
   *
   * @param sb the StringBuilder to append to
   * @param entries the skill entries to append
   */
  private static void appendSkillEntries(StringBuilder sb, List<SkillEntry> entries)
  {
    for (SkillEntry entry : entries)
      sb.append("- ").append(entry.name()).append(": ").append(entry.description()).append('\n');
  }

  /**
   * Discovers model-invocable skills with descriptions from all installed plugins.
   * <p>
   * Reads {@code ${claudeConfigPath}/plugins/installed_plugins.json} and for each plugin entry,
   * scans the plugin's {@code skills/} directory. The skill prefix is derived from the plugin
   * key (the part before {@code @}), e.g. {@code cat@cat} → prefix {@code cat:}.
   * <p>
   * Skills are included if:
   * <ul>
   *   <li>The SKILL.md frontmatter does not have {@code disable-model-invocation: true}</li>
   *   <li>A {@code description:} field is present in the frontmatter</li>
   * </ul>
   *
   * @param configPath the Claude config directory containing {@code plugins/installed_plugins.json}
   * @param jsonMapper the JSON mapper used to parse installed_plugins.json
   * @param yamlMapper the YAML mapper used to parse frontmatter
   * @return list of discovered skill entries
   * @throws IOException if skill discovery fails
   */
  private static List<SkillEntry> discoverPluginSkills(Path configPath, JsonMapper jsonMapper,
    YAMLMapper yamlMapper) throws IOException
  {
    List<SkillEntry> entries = new ArrayList<>();
    Path installedPluginsFile = configPath.resolve("plugins/installed_plugins.json");
    if (!Files.exists(installedPluginsFile))
      return entries;

    JsonNode root = jsonMapper.readTree(Files.readString(installedPluginsFile));
    JsonNode plugins = root.get("plugins");
    if (!(plugins instanceof ObjectNode pluginsObj))
    {
      throw new IOException("Malformed installed_plugins.json: missing or invalid 'plugins' field in " +
        installedPluginsFile);
    }

    for (Map.Entry<String, JsonNode> entry : pluginsObj.properties())
    {
      String pluginKey = entry.getKey();
      // Derive prefix from the part before '@', e.g. "cat@cat" → "cat"
      int atIndex = pluginKey.indexOf('@');
      String prefix;
      if (atIndex >= 0)
        prefix = pluginKey.substring(0, atIndex);
      else
        prefix = pluginKey;

      JsonNode installEntries = entry.getValue();
      if (!installEntries.isArray() || installEntries.isEmpty())
        continue;

      // Use the first install entry's installPath
      JsonNode firstEntry = installEntries.get(0);
      JsonNode installPathNode = firstEntry.get("installPath");
      if (installPathNode == null)
      {
        throw new IOException("Malformed installed_plugins.json: plugin '" + pluginKey +
          "' is missing 'installPath' in " + installedPluginsFile);
      }

      Path pluginRoot = Path.of(installPathNode.asString());
      Path skillsDir = pluginRoot.resolve("skills");
      entries.addAll(discoverSkillsFromDirectory(skillsDir, prefix + ":", yamlMapper));
    }
    return entries;
  }

  /**
   * Discovers model-invocable commands with descriptions from the project's commands directory.
   * <p>
   * Scans {@code ${projectPath}/.claude/commands/} for {@code .md} files. If the file has YAML
   * frontmatter with a {@code description:} field, that description is used. Files without a
   * description are excluded.
   *
   * @param projectPath the Claude project directory
   * @param yamlMapper the YAML mapper used to parse frontmatter
   * @return list of discovered skill entries
   * @throws IOException if discovery fails
   */
  private static List<SkillEntry> discoverProjectCommands(Path projectPath, YAMLMapper yamlMapper) throws IOException
  {
    List<SkillEntry> entries = new ArrayList<>();
    Path commandsDir = projectPath.resolve(".claude/commands");
    if (!Files.isDirectory(commandsDir))
      return entries;

    try (Stream<Path> stream = Files.list(commandsDir))
    {
      for (Path commandFile : stream.filter(p -> p.getFileName().toString().endsWith(".md")).sorted().toList())
      {
        String filename = commandFile.getFileName().toString();
        String name = filename.substring(0, filename.length() - ".md".length());
        String content = Files.readString(commandFile);
        String frontmatter = extractFrontmatter(content);
        if (frontmatter == null)
          continue;
        String description = extractDescription(frontmatter);
        if (description == null || description.isBlank())
          continue;
        List<String> paths = extractPaths(frontmatter, yamlMapper);
        entries.add(new SkillEntry(name, description.strip(), paths));
      }
    }
    return entries;
  }

  /**
   * Discovers model-invocable user skills with descriptions.
   * <p>
   * Scans directories under {@code ${claudeConfigPath}/skills/} for {@code SKILL.md} files. Skills are
   * included if they do not have {@code disable-model-invocation: true}.
   *
   * @param configPath the Claude config directory containing the {@code skills/} subdirectory
   * @param yamlMapper the YAML mapper used to parse frontmatter
   * @return list of discovered skill entries
   * @throws IOException if discovery fails
   */
  private static List<SkillEntry> discoverUserSkills(Path configPath, YAMLMapper yamlMapper) throws IOException
  {
    Path skillsDir = configPath.resolve("skills");
    return discoverSkillsFromDirectory(skillsDir, "", yamlMapper);
  }

  /**
   * Discovers model-invocable skills from a directory of skill subdirectories.
   * <p>
   * Iterates subdirectories in the given directory, reads each {@code SKILL.md} file, checks
   * frontmatter for {@code disable-model-invocation: true}, and extracts the description. Skills without
   * a description or marked as not model-invocable are excluded.
   *
   * @param skillsDir the directory containing skill subdirectories
   * @param prefix the prefix to prepend to each skill name (e.g. {@code "cat:"} or {@code ""})
   * @param yamlMapper the YAML mapper used to parse frontmatter
   * @return list of discovered skill entries, empty if the directory does not exist
   * @throws IOException if directory traversal or file reading fails
   */
  private static List<SkillEntry> discoverSkillsFromDirectory(Path skillsDir, String prefix,
    YAMLMapper yamlMapper) throws IOException
  {
    List<SkillEntry> entries = new ArrayList<>();
    if (!Files.isDirectory(skillsDir))
      return entries;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir, Files::isDirectory))
    {
      List<Path> sortedDirs = new ArrayList<>();
      stream.forEach(sortedDirs::add);
      sortedDirs.sort(Comparator.naturalOrder());
      for (Path skillDir : sortedDirs)
      {
        String name = skillDir.getFileName().toString();
        Path skillMd = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillMd))
          continue;
        String content = Files.readString(skillMd);
        String frontmatter = extractFrontmatter(content);
        if (frontmatter == null)
          continue;
        if (!isModelInvocable(frontmatter))
          continue;
        String description = extractDescription(frontmatter);
        if (description == null || description.isBlank())
          continue;
        List<String> paths = extractPaths(frontmatter, yamlMapper);
        entries.add(new SkillEntry(prefix + name, description.strip(), paths));
      }
    }
    return entries;
  }

  /**
   * Extracts the YAML frontmatter block from a SKILL.md file.
   *
   * @param content the file content
   * @return the frontmatter string (between the {@code ---} markers), or null if none found
   */
  public static String extractFrontmatter(String content)
  {
    return FrontmatterUtils.extractFrontmatter(content);
  }

  /**
   * Returns whether the model may invoke the skill.
   * <p>
   * A skill is excluded from the model-invocable listing if the frontmatter contains
   * {@code disable-model-invocation: true}.
   *
   * @param frontmatter the YAML frontmatter text
   * @return false if the frontmatter has {@code disable-model-invocation: true}, true otherwise
   */
  public static boolean isModelInvocable(String frontmatter)
  {
    for (String line : frontmatter.split("\n"))
    {
      String stripped = line.strip();
      int colon = stripped.indexOf(':');
      if (colon < 0)
        continue;
      String key = stripped.substring(0, colon).strip();
      if (!key.equals("disable-model-invocation"))
        continue;
      String value = stripped.substring(colon + 1).strip();
      int comment = value.indexOf('#');
      if (comment >= 0)
        value = value.substring(0, comment).strip();
      if (value.equalsIgnoreCase("true"))
        return false;
    }
    return true;
  }

  /**
   * Extracts the description value from frontmatter.
   * <p>
   * Handles multi-line YAML block scalar ({@code >}) by collecting continuation lines.
   *
   * @param frontmatter the YAML frontmatter text
   * @return the description string, or null if not present
   */
  public static String extractDescription(String frontmatter)
  {
    String[] lines = frontmatter.split("\n");
    for (int i = 0; i < lines.length; ++i)
    {
      String line = lines[i];
      if (!line.startsWith("description:"))
        continue;
      String value = line.substring("description:".length()).strip();
      if (value.equals(">"))
      {
        // Multi-line block scalar - collect indented continuation lines
        StringBuilder desc = new StringBuilder();
        for (int j = i + 1; j < lines.length; ++j)
        {
          String next = lines[j];
          if (next.startsWith(" ") || next.startsWith("\t"))
            desc.append(next.strip()).append(' ');
          else
            break;
        }
        return desc.toString().strip();
      }
      if (!value.isEmpty())
        return value;
    }
    return null;
  }

  /**
   * Extracts the path glob patterns from the {@code paths:} frontmatter field.
   * <p>
   * Parses the YAML {@code paths:} field using the provided mapper. Returns an empty list if the
   * field is absent or malformed.
   *
   * @param frontmatter the YAML frontmatter text
   * @param yamlMapper the YAML mapper used to parse the frontmatter
   * @return an unmodifiable list of glob patterns, empty if none are defined
   */
  public static List<String> extractPaths(String frontmatter, YAMLMapper yamlMapper)
  {
    try
    {
      JsonNode root = yamlMapper.readTree(frontmatter);
      return parseListNode(root.get("paths"), List.of());
    }
    catch (Exception _)
    {
      return List.of();
    }
  }

  /**
   * Parses a YAML array {@link JsonNode} into an unmodifiable list of strings.
   *
   * @param node the JSON node to parse, may be null
   * @param defaultValue the value to return when the node is absent, null, or not an array
   * @return an unmodifiable list of strings from the array, or {@code defaultValue} if the node
   *   is absent, null, missing, or not an array
   */
  private static List<String> parseListNode(JsonNode node, List<String> defaultValue)
  {
    if (node == null || node.isNull() || node.isMissingNode())
      return defaultValue;
    if (!node.isArray())
      return defaultValue;
    if (node.isEmpty())
      return List.of();
    List<String> result = new ArrayList<>();
    for (JsonNode item : node)
      result.add(item.asString());
    return List.copyOf(result);
  }

  /**
   * Returns {@code true} if {@code value} is surrounded by matching single or double quotes.
   *
   * @param value a string of length >= 2
   * @return {@code true} if the first and last characters are matching quote characters
   */
  private static boolean isQuoted(String value)
  {
    char first = value.charAt(0);
    char last = value.charAt(value.length() - 1);
    return (first == '"' && last == '"') || (first == '\'' && last == '\'');
  }

  /**
   * Extracts the value of a named field from YAML frontmatter.
   *
   * @param frontmatter the YAML frontmatter text
   * @param fieldName   the field name to extract
   * @return the field value (with surrounding quotes removed if present), or an empty string if not found
   */
  public static String extractField(String frontmatter, String fieldName)
  {
    for (String line : frontmatter.split("\n"))
    {
      if (line.startsWith(fieldName + ":"))
      {
        String value = line.substring(fieldName.length() + 1).strip();
        // Strip surrounding single or double quotes
        if (value.length() >= 2 && isQuoted(value))
        {
          value = value.substring(1, value.length() - 1);
        }
        return value;
      }
    }
    return "";
  }
}
