/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Discovers model-invocable skills from all sources.
 * <p>
 * Sources:
 * <ul>
 *   <li>All installed plugins via {@code ${configDir}/plugins/installed_plugins.json}</li>
 *   <li>Project commands in {@code ${projectDir}/.claude/commands/}</li>
 *   <li>User skills in {@code ${configDir}/skills/}</li>
 * </ul>
 * Each discovered skill is represented as a {@link SkillEntry} containing the qualified name and
 * description. Callers can format the entries as needed.
 */
public final class SkillDiscovery
{
  /**
   * A discovered skill entry with a qualified name and description.
   *
   * @param name the qualified skill name (e.g. {@code cat:git-commit})
   * @param description the human-readable description from frontmatter
   */
  public record SkillEntry(String name, String description)
  {
    /**
     * Creates a new SkillEntry.
     *
     * @param name the qualified skill name (e.g. {@code cat:git-commit})
     * @param description the human-readable description from frontmatter
     * @throws NullPointerException if {@code name} or {@code description} are null
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public SkillEntry
    {
      requireThat(name, "name").isNotBlank();
      requireThat(description, "description").isNotNull();
    }
  }

  private final JvmScope scope;

  /**
   * Creates a new SkillDiscovery instance.
   *
   * @param scope the JVM scope providing environment paths and configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public SkillDiscovery(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
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
      entries.addAll(discoverPluginSkills(scope.getClaudeConfigDir(), scope.getJsonMapper()));
      entries.addAll(discoverProjectCommands(scope.getClaudeProjectDir()));
      entries.addAll(discoverUserSkills(scope.getClaudeConfigDir()));
      return entries;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Formats the full skill listing for injection into agent context.
   * <p>
   * Discovers all model-invocable skills and returns them as a formatted listing string. Each entry
   * uses the format {@code "- name: description"}, matching Claude Code's native skill listing.
   * The header references {@code load-skill.sh} since subagents cannot use the Skill tool directly.
   *
   * @param scope the JVM scope providing environment paths and configuration
   * @return the formatted skill listing, or an empty string if no skills are found
   * @throws NullPointerException if {@code scope} is null
   */
  public static String formatSkillListing(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    List<SkillEntry> entries = new SkillDiscovery(scope).discoverAll();
    if (entries.isEmpty())
      return "";
    StringBuilder sb = new StringBuilder(512);
    sb.append("The following skills are available. To load a skill's instructions, run via Bash:\n").
      append("  \"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh\" \"${CLAUDE_PLUGIN_ROOT}\" " +
        "\"<skill-name>\" \"<cat-agent-id>\"\n\n");
    for (SkillEntry entry : entries)
      sb.append("- ").append(entry.name()).append(": ").append(entry.description()).append('\n');
    return sb.toString();
  }

  /**
   * Discovers model-invocable skills with descriptions from all installed plugins.
   * <p>
   * Reads {@code ${configDir}/plugins/installed_plugins.json} and for each plugin entry,
   * scans the plugin's {@code skills/} directory. The skill prefix is derived from the plugin
   * key (the part before {@code @}), e.g. {@code cat@cat} → prefix {@code cat:}.
   * <p>
   * Skills are included if:
   * <ul>
   *   <li>The SKILL.md frontmatter does not have {@code model-invocable: false}</li>
   *   <li>A {@code description:} field is present in the frontmatter</li>
   * </ul>
   *
   * @param configDir the Claude config directory containing {@code plugins/installed_plugins.json}
   * @param jsonMapper the JSON mapper used to parse installed_plugins.json
   * @return list of discovered skill entries
   * @throws IOException if skill discovery fails
   */
  private static List<SkillEntry> discoverPluginSkills(Path configDir, JsonMapper jsonMapper) throws IOException
  {
    List<SkillEntry> entries = new ArrayList<>();
    Path installedPluginsFile = configDir.resolve("plugins/installed_plugins.json");
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
      entries.addAll(discoverSkillsFromDirectory(skillsDir, prefix + ":"));
    }
    return entries;
  }

  /**
   * Discovers model-invocable commands with descriptions from the project's commands directory.
   * <p>
   * Scans {@code ${projectDir}/.claude/commands/} for {@code .md} files. If the file has YAML
   * frontmatter with a {@code description:} field, that description is used. Files without a
   * description are excluded.
   *
   * @param projectDir the Claude project directory
   * @return list of discovered skill entries
   * @throws IOException if discovery fails
   */
  private static List<SkillEntry> discoverProjectCommands(Path projectDir) throws IOException
  {
    List<SkillEntry> entries = new ArrayList<>();
    Path commandsDir = projectDir.resolve(".claude/commands");
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
        entries.add(new SkillEntry(name, description.strip()));
      }
    }
    return entries;
  }

  /**
   * Discovers model-invocable user skills with descriptions.
   * <p>
   * Scans directories under {@code ${configDir}/skills/} for {@code SKILL.md} files. Skills are
   * included if they do not have {@code model-invocable: false}.
   *
   * @param configDir the Claude config directory containing the {@code skills/} subdirectory
   * @return list of discovered skill entries
   * @throws IOException if discovery fails
   */
  private static List<SkillEntry> discoverUserSkills(Path configDir) throws IOException
  {
    Path skillsDir = configDir.resolve("skills");
    return discoverSkillsFromDirectory(skillsDir, "");
  }

  /**
   * Discovers model-invocable skills from a directory of skill subdirectories.
   * <p>
   * Iterates subdirectories in the given directory, reads each {@code SKILL.md} file, checks
   * frontmatter for {@code model-invocable: false}, and extracts the description. Skills without
   * a description or marked as not model-invocable are excluded.
   *
   * @param skillsDir the directory containing skill subdirectories
   * @param prefix the prefix to prepend to each skill name (e.g. {@code "cat:"} or {@code ""})
   * @return list of discovered skill entries, empty if the directory does not exist
   * @throws IOException if directory traversal or file reading fails
   */
  private static List<SkillEntry> discoverSkillsFromDirectory(Path skillsDir, String prefix) throws IOException
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
        if (isModelInvocableFalse(frontmatter))
          continue;
        String description = extractDescription(frontmatter);
        if (description == null || description.isBlank())
          continue;
        entries.add(new SkillEntry(prefix + name, description.strip()));
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
    if (!content.startsWith("---"))
      return null;
    int end = content.indexOf("\n---", 3);
    if (end < 0)
      return null;
    return content.substring(3, end).strip();
  }

  /**
   * Returns true if the frontmatter contains {@code model-invocable: false}.
   *
   * @param frontmatter the YAML frontmatter text
   * @return true if the skill is not model-invocable
   */
  public static boolean isModelInvocableFalse(String frontmatter)
  {
    for (String line : frontmatter.split("\n"))
    {
      String stripped = line.strip();
      if (stripped.equals("model-invocable: false"))
        return true;
    }
    return false;
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
}
