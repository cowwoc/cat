/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Discovers and filters rule files from {@code .claude/cat/rules/}.
 * <p>
 * Rule files are Markdown files with optional YAML frontmatter controlling which agent audience
 * receives the content:
 * <ul>
 *   <li>{@code mainAgent: true|false} — whether to inject into the main agent (default: true)</li>
 *   <li>{@code subAgents: [type1, type2]} or {@code subAgents: []} —
 *       which subagent types receive this rule (default: all; omit for all)</li>
 *   <li>{@code paths: ["*.java", "src/**"]} — only inject when an active file matches one of
 *       these globs (default: always inject)</li>
 * </ul>
 */
public final class RulesDiscovery
{
  /**
   * A discovered rule file with parsed frontmatter and body content.
   *
   * @param path       the path to the rule file
   * @param mainAgent  whether to inject into the main agent
   * @param subAgents  the subagent types that receive this rule; empty means none, ["all"] means all
 *                   (default when omitted from frontmatter)
   * @param paths      glob patterns restricting injection to matching active files; empty means always inject
   * @param content    the full file content (including frontmatter stripped)
   */
  public record RuleFile(
    Path path,
    boolean mainAgent,
    List<String> subAgents,
    List<String> paths,
    String content)
  {
    /**
     * Creates a new RuleFile record.
     *
     * @param path      the file path
     * @param mainAgent inject into main agent
     * @param subAgents subagent types
     * @param paths     glob patterns
     * @param content   file content (body without frontmatter)
     * @throws NullPointerException if any parameter is null
     */
    public RuleFile
    {
      requireThat(path, "path").isNotNull();
      requireThat(subAgents, "subAgents").isNotNull();
      subAgents = List.copyOf(subAgents);
      requireThat(paths, "paths").isNotNull();
      paths = List.copyOf(paths);
      requireThat(content, "content").isNotNull();
    }
  }

  /**
   * Maximum number of characters to scan when searching for the closing {@code ---} of a
   * frontmatter block. Files where the first {@code \n---} appears beyond this offset are treated
   * as having no frontmatter, preventing excessive memory usage on malformed files.
   */
  private static final int FRONTMATTER_SCAN_LIMIT = 4096;

  private final Path rulesDir;
  private final YAMLMapper yamlMapper;

  /**
   * Creates a new RulesDiscovery instance.
   *
   * @param rulesDir   the directory to discover rules from (typically {@code .claude/cat/rules/})
   * @param yamlMapper the YAML mapper for parsing frontmatter
   * @throws NullPointerException if any parameter is null
   */
  public RulesDiscovery(Path rulesDir, YAMLMapper yamlMapper)
  {
    requireThat(rulesDir, "rulesDir").isNotNull();
    requireThat(yamlMapper, "yamlMapper").isNotNull();
    this.rulesDir = rulesDir;
    this.yamlMapper = yamlMapper;
  }

  /**
   * Discovers all rule files from the rules directory.
   * <p>
   * Returns an empty list if the directory does not exist.
   *
   * @return list of discovered rule files sorted by filename
   * @throws WrappedCheckedException if reading the directory or any file fails
   */
  public List<RuleFile> discoverAll()
  {
    if (!Files.isDirectory(rulesDir))
      return List.of();

    try
    {
      List<Path> files = new ArrayList<>();
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(rulesDir, "*.md"))
      {
        stream.forEach(files::add);
      }
      files.sort(Comparator.naturalOrder());

      List<RuleFile> rules = new ArrayList<>();
      for (Path file : files)
      {
        String rawContent = Files.readString(file);
        rules.add(parseRuleFile(file, rawContent));
      }
      return rules;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Parses a rule file from its content.
   *
   * @param path    the file path
   * @param content the raw file content
   * @return the parsed rule file
   */
  private RuleFile parseRuleFile(Path path, String content)
  {
    String frontmatter = extractFrontmatter(content);
    String body = stripFrontmatter(content);

    if (frontmatter == null)
      return new RuleFile(path, true, List.of("all"), List.of(), body);

    try
    {
      JsonNode root = yamlMapper.readTree(frontmatter);
      boolean mainAgent = true;
      if (root.has("mainAgent"))
        mainAgent = root.get("mainAgent").asBoolean(true);
      List<String> subAgents = parseListNode(root.get("subAgents"), List.of("all"));
      List<String> paths = parseListNode(root.get("paths"), List.of());
      return new RuleFile(path, mainAgent, subAgents, paths, body);
    }
    catch (Exception _)
    {
      // Malformed frontmatter - treat as no frontmatter (defaults)
      return new RuleFile(path, true, List.of("all"), List.of(), body);
    }
  }

  /**
   * Parses a JSON array node into a list of strings, returning a default value if the node is absent
   * or not an array.
   *
   * @param node         the JSON node to parse
   * @param defaultValue the default value if the node is absent or not an array
   * @return the parsed list, or the default value
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
    return result;
  }

  /**
   * Extracts the YAML frontmatter block from file content.
   * <p>
   * If the closing {@code ---} line does not appear within the first
   * {@value #FRONTMATTER_SCAN_LIMIT} characters, the file is treated as having no frontmatter.
   *
   * @param content the file content
   * @return the frontmatter string (between the {@code ---} markers), or null if none
   */
  static String extractFrontmatter(String content)
  {
    if (!content.startsWith("---"))
      return null;
    int scanEnd = Math.min(content.length(), FRONTMATTER_SCAN_LIMIT);
    int end = content.indexOf("\n---", 3);
    if (end < 0 || end > scanEnd)
      return null;
    return content.substring(3, end).strip();
  }

  /**
   * Returns the body content with frontmatter removed.
   * <p>
   * If the closing {@code ---} line does not appear within the first
   * {@value #FRONTMATTER_SCAN_LIMIT} characters, the full content is returned unchanged
   * (consistent with {@link #extractFrontmatter(String)}).
   *
   * @param content the file content
   * @return content without frontmatter block
   */
  static String stripFrontmatter(String content)
  {
    if (!content.startsWith("---"))
      return content;
    int scanEnd = Math.min(content.length(), FRONTMATTER_SCAN_LIMIT);
    int end = content.indexOf("\n---", 3);
    if (end < 0 || end > scanEnd)
      return content;
    // Skip past the closing "---\n"
    int bodyStart = end + 4;
    if (bodyStart < content.length() && content.charAt(bodyStart) == '\n')
      ++bodyStart;
    return content.substring(bodyStart).strip();
  }

  /**
   * Filters rules for the main agent, applying audience and paths filtering.
   *
   * @param rules        all discovered rules
   * @param activeFiles  the list of files currently being operated on (for paths matching)
   * @return rules where {@code mainAgent=true} and paths match (or have no paths restriction)
   * @throws NullPointerException if {@code rules} or {@code activeFiles} is null
   */
  public static List<RuleFile> filterForMainAgent(List<RuleFile> rules, List<String> activeFiles)
  {
    requireThat(rules, "rules").isNotNull();
    requireThat(activeFiles, "activeFiles").isNotNull();

    List<RuleFile> result = new ArrayList<>();
    for (RuleFile rule : rules)
    {
      if (!rule.mainAgent())
        continue;
      if (!matchesPaths(rule.paths(), activeFiles))
        continue;
      result.add(rule);
    }
    return result;
  }

  /**
   * Filters rules for a subagent, applying audience and paths filtering.
   *
   * @param rules           all discovered rules
   * @param subagentType    the type identifier of the subagent (e.g. {@code "cat:work-execute"})
   * @param activeFiles     the list of files currently being operated on (for paths matching)
   * @return rules where subAgents contains the subagent type (or "all") and paths match
   * @throws NullPointerException if any parameter is null
   */
  public static List<RuleFile> filterForSubagent(List<RuleFile> rules, String subagentType,
    List<String> activeFiles)
  {
    requireThat(rules, "rules").isNotNull();
    requireThat(subagentType, "subagentType").isNotNull();
    requireThat(activeFiles, "activeFiles").isNotNull();

    List<RuleFile> result = new ArrayList<>();
    for (RuleFile rule : rules)
    {
      List<String> subAgents = rule.subAgents();
      if (subAgents.isEmpty())
        continue;
      if (!subAgents.contains("all") && !subAgents.contains(subagentType))
        continue;
      if (!matchesPaths(rule.paths(), activeFiles))
        continue;
      result.add(rule);
    }
    return result;
  }

  /**
   * Returns true if the rule should be injected based on its paths restriction and the active files.
   * <p>
   * If the rule has no paths restriction (empty list), it always matches.
   * Otherwise, it matches if any active file matches any of the path globs.
   *
   * @param rulePaths   the path globs from the rule's frontmatter
   * @param activeFiles the files currently active in the session
   * @return true if the rule should be injected
   */
  private static boolean matchesPaths(List<String> rulePaths, List<String> activeFiles)
  {
    if (rulePaths.isEmpty())
      return true;
    if (activeFiles.isEmpty())
      return false;
    for (String activeFile : activeFiles)
    {
      String filename = Path.of(activeFile).getFileName().toString();
      for (String glob : rulePaths)
      {
        if (GlobMatcher.matches(glob, activeFile) || GlobMatcher.matches(glob, filename))
          return true;
      }
    }
    return false;
  }


  /**
   * Discovers, filters, and renders CAT rules for an audience in one step.
   * <p>
   * Constructs a {@link RulesDiscovery} for the given directory, discovers all rule files,
   * applies the provided filter function (which combines audience and paths checks), and returns
   * the rendered content. Returns an empty string if the rules directory does not exist, no rules
   * survive the filter, or all content is blank.
   * <p>
   * This shared pipeline is used by both the main-agent and subagent injection paths.
   * The only difference between callers is which filter function they supply:
   * {@link #filterForMainAgent} or {@link #filterForSubagent}.
   *
   * @param rulesDir    the directory containing rule files (typically {@code .claude/cat/rules/})
   * @param yamlMapper  the YAML mapper for parsing frontmatter
   * @param filterFn    function that takes all rules and active files, and returns the filtered rules
   * @param activeFiles the list of files currently active in the session (for paths matching)
   * @return the rendered rule content, or empty string if no rules apply
   * @throws NullPointerException if any parameter is null
   */
  public static String getCatRulesForAudience(Path rulesDir, YAMLMapper yamlMapper,
    BiFunction<List<RuleFile>, List<String>, List<RuleFile>> filterFn, List<String> activeFiles)
  {
    requireThat(rulesDir, "rulesDir").isNotNull();
    requireThat(yamlMapper, "yamlMapper").isNotNull();
    requireThat(filterFn, "filterFn").isNotNull();
    requireThat(activeFiles, "activeFiles").isNotNull();

    List<RuleFile> allRules = new RulesDiscovery(rulesDir, yamlMapper).discoverAll();
    if (allRules.isEmpty())
      return "";

    List<RuleFile> filtered = filterFn.apply(allRules, activeFiles);
    if (filtered.isEmpty())
      return "";

    String content = renderAll(filtered);
    if (content.isBlank())
      return "";
    return content;
  }

  /**
   * Renders all rule files as a single concatenated string, with the body content of each rule.
   * <p>
   * Returns empty string if the list is empty.
   *
   * @param rules the rule files to render
   * @return the concatenated content of all rules
   * @throws NullPointerException if {@code rules} is null
   */
  public static String renderAll(List<RuleFile> rules)
  {
    requireThat(rules, "rules").isNotNull();
    if (rules.isEmpty())
      return "";
    StringBuilder sb = new StringBuilder();
    for (RuleFile rule : rules)
    {
      if (!sb.isEmpty())
        sb.append("\n\n");
      sb.append(rule.content());
    }
    return sb.toString();
  }
}
