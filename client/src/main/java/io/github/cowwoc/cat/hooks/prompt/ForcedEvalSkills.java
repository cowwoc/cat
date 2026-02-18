/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.prompt;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.PromptHandler;
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
 * Injects a forced skill activation check instruction into every user prompt.
 * <p>
 * This handler ensures Claude explicitly evaluates whether any low-activation skills
 * match the user's prompt before proceeding. This achieves higher activation rates
 * for skills that otherwise have low activation.
 * <p>
 * Skills are discovered dynamically from all sources at construction time:
 * <ul>
 *   <li>All installed plugins (via {@code plugins/installed_plugins.json})</li>
 *   <li>Project commands (from {@code .claude/commands/})</li>
 *   <li>User skills (from {@code skills/} in the config dir)</li>
 * </ul>
 * All skills that are not "-first-use" internal skills and do not have
 * {@code user-invocable: false} in their frontmatter are included.
 */
public final class ForcedEvalSkills implements PromptHandler
{
  private final String instruction;

  /**
   * Creates a new forced evaluation skills handler, discovering skills from the given scope.
   *
   * @param scope the JVM scope providing environment paths and configuration
   * @throws NullPointerException if {@code scope} is null
   * @throws io.github.cowwoc.pouch10.core.WrappedCheckedException if skill discovery fails due to I/O errors
   *   or malformed plugin configuration
   */
  public ForcedEvalSkills(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    try
    {
      this.instruction = buildInstruction(scope);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Returns the forced skill evaluation instruction for every prompt.
   *
   * @param prompt the user's prompt text
   * @param sessionId the current session ID
   * @return the forced evaluation instruction string
   * @throws IllegalArgumentException if {@code prompt} or {@code sessionId} are blank
   */
  @Override
  public String check(String prompt, String sessionId)
  {
    requireThat(prompt, "prompt").isNotBlank();
    requireThat(sessionId, "sessionId").isNotBlank();
    return instruction;
  }

  /**
   * Builds the forced skill evaluation instruction from skills discovered from all sources.
   *
   * @param scope the JVM scope providing environment paths
   * @return the formatted instruction string
   */
  private static String buildInstruction(JvmScope scope) throws IOException
  {
    List<String> skillLines = discoverAllSkills(scope);
    StringBuilder sb = new StringBuilder(512);
    sb.append("""
      SKILL ACTIVATION CHECK: Before responding, evaluate whether the user's message matches any of \
      these skills. For each skill, decide YES or NO. If YES, invoke it using the Skill tool BEFORE \
      doing anything else.

      Skills to evaluate:
      """);
    for (String line : skillLines)
      sb.append(line).append('\n');
    sb.append("\nIf NONE match, proceed normally without invoking any skill.");
    return sb.toString();
  }

  /**
   * Discovers model-invocable skills from all sources.
   * <p>
   * Sources:
   * <ul>
   *   <li>All installed plugins via {@code ${configDir}/plugins/installed_plugins.json}</li>
   *   <li>Project commands in {@code ${projectDir}/.claude/commands/}</li>
   *   <li>User skills in {@code ${configDir}/skills/}</li>
   * </ul>
   *
   * @param scope the JVM scope providing environment paths
   * @return list of formatted skill lines
   */
  private static List<String> discoverAllSkills(JvmScope scope) throws IOException
  {
    List<String> lines = new ArrayList<>();
    lines.addAll(discoverPluginSkills(scope.getClaudeConfigDir(), scope.getJsonMapper()));
    lines.addAll(discoverProjectCommands(scope.getClaudeProjectDir()));
    lines.addAll(discoverUserSkills(scope.getClaudeConfigDir()));
    return lines;
  }

  /**
   * Discovers model-invocable skills from all installed plugins.
   * <p>
   * Reads {@code ${configDir}/plugins/installed_plugins.json} and for each plugin entry,
   * scans the plugin's {@code skills/} directory. The skill prefix is derived from the plugin
   * key (the part before {@code @}), e.g. {@code cat@cat} → prefix {@code cat:}.
   * <p>
   * Skills are included if:
   * <ul>
   *   <li>The directory name does not contain {@code -first-use}</li>
   *   <li>The SKILL.md frontmatter does not have {@code user-invocable: false}</li>
   *   <li>A {@code description:} field is present in the frontmatter</li>
   * </ul>
   *
   * @param configDir the Claude config directory containing {@code plugins/installed_plugins.json}
   * @param jsonMapper the JSON mapper used to parse installed_plugins.json
   * @return list of formatted skill lines (e.g. {@code "- cat:name — description"})
   */
  private static List<String> discoverPluginSkills(Path configDir, JsonMapper jsonMapper) throws IOException
  {
    List<String> lines = new ArrayList<>();
    Path installedPluginsFile = configDir.resolve("plugins/installed_plugins.json");
    if (!Files.exists(installedPluginsFile))
      return lines;

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
      lines.addAll(discoverSkillsFromPluginRoot(pluginRoot, prefix));
    }
    return lines;
  }

  /**
   * Discovers model-invocable skills from a single plugin root directory.
   *
   * @param pluginRoot the plugin root directory containing a {@code skills/} subdirectory
   * @param prefix the skill prefix (e.g. {@code cat})
   * @return list of formatted skill lines (e.g. {@code "- cat:name — description"})
   */
  private static List<String> discoverSkillsFromPluginRoot(Path pluginRoot, String prefix) throws IOException
  {
    List<String> lines = new ArrayList<>();
    Path skillsDir = pluginRoot.resolve("skills");
    if (!Files.isDirectory(skillsDir))
      return lines;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir, Files::isDirectory))
    {
      List<Path> sortedDirs = new ArrayList<>();
      stream.forEach(sortedDirs::add);
      sortedDirs.sort(Comparator.naturalOrder());
      for (Path skillDir : sortedDirs)
      {
        String name = skillDir.getFileName().toString();
        if (name.contains("-first-use"))
          continue;
        Path skillMd = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillMd))
          continue;
        String content = Files.readString(skillMd);
        String frontmatter = extractFrontmatter(content);
        if (frontmatter == null)
          continue;
        if (isUserInvocableFalse(frontmatter))
          continue;
        String description = extractDescription(frontmatter);
        if (description == null || description.isBlank())
          continue;
        lines.add("- " + prefix + ":" + name + " — " + description.strip());
      }
    }
    return lines;
  }

  /**
   * Discovers model-invocable commands from the project's {@code .claude/commands/} directory.
   * <p>
   * Scans {@code ${projectDir}/.claude/commands/} for {@code .md} files. If the file has YAML
   * frontmatter with a {@code description:} field, that description is used. Files without a
   * description are excluded.
   *
   * @param projectDir the Claude project directory
   * @return list of formatted skill lines (e.g. {@code "- review — description"})
   */
  private static List<String> discoverProjectCommands(Path projectDir) throws IOException
  {
    List<String> lines = new ArrayList<>();
    Path commandsDir = projectDir.resolve(".claude/commands");
    if (!Files.isDirectory(commandsDir))
      return lines;

    try (Stream<Path> stream = Files.list(commandsDir))
    {
      for (Path commandFile : stream.filter(p -> p.getFileName().toString().endsWith(".md")).sorted().toList())
      {
        String filename = commandFile.getFileName().toString();
        String name = filename.substring(0, filename.length() - ".md".length());
        String content = Files.readString(commandFile);
        String frontmatter = extractFrontmatter(content);
        String description = null;
        if (frontmatter != null)
          description = extractDescription(frontmatter);
        if (description == null || description.isBlank())
          continue;
        lines.add("- " + name + " — " + description.strip());
      }
    }
    return lines;
  }

  /**
   * Discovers model-invocable user skills from {@code ${configDir}/skills/}.
   * <p>
   * Scans directories under {@code ${configDir}/skills/} for {@code SKILL.md} files. Skills are
   * included if they are not "-first-use" and do not have {@code user-invocable: false}.
   *
   * @param configDir the Claude config directory containing the {@code skills/} subdirectory
   * @return list of formatted skill lines (e.g. {@code "- my-skill — description"})
   */
  private static List<String> discoverUserSkills(Path configDir) throws IOException
  {
    List<String> lines = new ArrayList<>();
    Path skillsDir = configDir.resolve("skills");
    if (!Files.isDirectory(skillsDir))
      return lines;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir, Files::isDirectory))
    {
      List<Path> sortedDirs = new ArrayList<>();
      stream.forEach(sortedDirs::add);
      sortedDirs.sort(Comparator.naturalOrder());
      for (Path skillDir : sortedDirs)
      {
        String name = skillDir.getFileName().toString();
        if (name.contains("-first-use"))
          continue;
        Path skillMd = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillMd))
          continue;
        String content = Files.readString(skillMd);
        String frontmatter = extractFrontmatter(content);
        if (frontmatter == null)
          continue;
        if (isUserInvocableFalse(frontmatter))
          continue;
        String description = extractDescription(frontmatter);
        if (description == null || description.isBlank())
          continue;
        lines.add("- " + name + " — " + description.strip());
      }
    }
    return lines;
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
   * Returns true if the frontmatter contains {@code user-invocable: false}.
   *
   * @param frontmatter the YAML frontmatter text
   * @return true if the skill is not user-invocable
   */
  public static boolean isUserInvocableFalse(String frontmatter)
  {
    for (String line : frontmatter.split("\n"))
    {
      String stripped = line.strip();
      if (stripped.equals("user-invocable: false"))
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
