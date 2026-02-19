/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.prompt.ForcedEvalSkills;
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
 * Injects the full skill listing with descriptions into Claude's context after compaction.
 * <p>
 * Claude Code provides skill listings at initial startup, but does NOT re-send them after
 * conversation compaction. This handler checks the SessionStart event source and only injects
 * the skill listing when the source is {@code "compact"}, ensuring descriptions remain available
 * after context is compressed.
 */
public final class InjectSkillListing implements SessionStartHandler
{
  private final JvmScope scope;

  /**
   * Creates a new InjectSkillListing handler.
   *
   * @param scope the JVM scope providing environment paths and configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public InjectSkillListing(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Returns the full skill listing with descriptions as additional context.
   *
   * @param input the hook input
   * @return a result containing the skill listing
   * @throws NullPointerException if {@code input} is null
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();
    // Only inject skill listing after compaction. Claude Code provides listings at initial startup,
    // but does not re-send them after conversation compaction.
    String source = input.getString("source");
    if (!source.equals("compact"))
      return Result.empty();
    try
    {
      List<String> skillLines = discoverAllSkills();
      if (skillLines.isEmpty())
        return Result.empty();
      StringBuilder sb = new StringBuilder(512);
      sb.append("""
        ## Model-Invocable Skills

        Skills available for activation. Use description to determine when each skill applies.

        """);
      for (String line : skillLines)
        sb.append(line).append('\n');
      return Result.context(sb.toString());
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Discovers all model-invocable skills with descriptions from all sources.
   *
   * @return list of formatted skill lines with descriptions (e.g. "- cat:add -- description")
   * @throws IOException if skill discovery fails
   */
  private List<String> discoverAllSkills() throws IOException
  {
    List<String> lines = new ArrayList<>();
    lines.addAll(discoverPluginSkills(scope.getClaudeConfigDir(), scope.getJsonMapper()));
    lines.addAll(discoverProjectCommands(scope.getClaudeProjectDir()));
    lines.addAll(discoverUserSkills(scope.getClaudeConfigDir()));
    return lines;
  }

  /**
   * Discovers model-invocable skills with descriptions from all installed plugins.
   *
   * @param configDir the Claude config directory
   * @param jsonMapper the JSON mapper
   * @return list of formatted skill lines with descriptions
   * @throws IOException if discovery fails
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
      int atIndex = pluginKey.indexOf('@');
      String prefix;
      if (atIndex >= 0)
        prefix = pluginKey.substring(0, atIndex);
      else
        prefix = pluginKey;

      JsonNode installEntries = entry.getValue();
      if (!installEntries.isArray() || installEntries.isEmpty())
        continue;

      JsonNode firstEntry = installEntries.get(0);
      JsonNode installPathNode = firstEntry.get("installPath");
      if (installPathNode == null)
      {
        throw new IOException("Malformed installed_plugins.json: plugin '" + pluginKey +
          "' is missing 'installPath' in " + installedPluginsFile);
      }

      Path pluginRoot = Path.of(installPathNode.asString());
      Path skillsDir = pluginRoot.resolve("skills");
      if (!Files.isDirectory(skillsDir))
        continue;

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
          String frontmatter = ForcedEvalSkills.extractFrontmatter(content);
          if (frontmatter == null)
            continue;
          if (ForcedEvalSkills.isModelInvocableFalse(frontmatter))
            continue;
          String description = ForcedEvalSkills.extractDescription(frontmatter);
          if (description == null || description.isBlank())
            continue;
          lines.add("- " + prefix + ":" + name + " \u2014 " + description.strip());
        }
      }
    }
    return lines;
  }

  /**
   * Discovers model-invocable commands with descriptions from the project's commands directory.
   *
   * @param projectDir the Claude project directory
   * @return list of formatted command lines with descriptions
   * @throws IOException if discovery fails
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
        String frontmatter = ForcedEvalSkills.extractFrontmatter(content);
        String description = null;
        if (frontmatter != null)
          description = ForcedEvalSkills.extractDescription(frontmatter);
        if (description == null || description.isBlank())
          continue;
        lines.add("- " + name + " \u2014 " + description.strip());
      }
    }
    return lines;
  }

  /**
   * Discovers model-invocable user skills with descriptions.
   *
   * @param configDir the Claude config directory
   * @return list of formatted skill lines with descriptions
   * @throws IOException if discovery fails
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
        String frontmatter = ForcedEvalSkills.extractFrontmatter(content);
        if (frontmatter == null)
          continue;
        if (ForcedEvalSkills.isModelInvocableFalse(frontmatter))
          continue;
        String description = ForcedEvalSkills.extractDescription(frontmatter);
        if (description == null || description.isBlank())
          continue;
        lines.add("- " + name + " \u2014 " + description.strip());
      }
    }
    return lines;
  }
}
