/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * Discovers and filters rule files from CAT rule directories.
 * <p>
 * Rule files are Markdown files with optional YAML frontmatter controlling which agent audience
 * receives the content:
 * <ul>
 *   <li>{@code mainAgent: true|false} — whether to inject into the main agent (default: true)</li>
 *   <li>{@code subAgents: [type1, type2]} or {@code subAgents: []} —
 *       which agent types receive this rule (default when omitted: all agents)</li>
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
   * @param contextPath the rule path to show in injected context
   * @param mainAgent  whether to inject into the main agent
   * @param subAgents  the agent types that receive this rule; {@code null} means all agents
   *                   (default when omitted from frontmatter), empty means none
   * @param paths      glob patterns restricting injection to matching active files; empty means always inject
   * @param content    the file body with leading YAML frontmatter stripped
   */
  public record RuleFile(
    Path path,
    String contextPath,
    boolean mainAgent,
    List<String> subAgents,
    List<String> paths,
    String content)
  {
    /**
     * Creates a new RuleFile record.
     *
     * @param path      the file path
     * @param contextPath the rule path to show in injected context
     * @param mainAgent inject into main agent
     * @param subAgents agent types ({@code null} means all agents)
     * @param paths     glob patterns
     * @param content   file content (body without frontmatter)
     * @throws NullPointerException if {@code path}, {@code contextPath}, {@code paths}, or
     *   {@code content} are null
     */
    public RuleFile
    {
      requireThat(path, "path").isNotNull();
      requireThat(contextPath, "contextPath").isNotBlank();
      if (subAgents != null)
        subAgents = List.copyOf(subAgents);
      requireThat(paths, "paths").isNotNull();
      paths = List.copyOf(paths);
      requireThat(content, "content").isNotNull();
    }
  }

  private record FileState(String name, long size, long modifiedMillis)
  {
  }

  private record CachedRules(List<FileState> fileStates, List<RuleFile> rules, Instant validatedAt)
  {
    /**
     * Creates cached rule snapshot with defensive copies.
     *
     * @param fileStates file-state snapshot used for invalidation
     * @param rules discovered rules
     * @param validatedAt time cache entry was last validated
     */
    private CachedRules
    {
      fileStates = List.copyOf(fileStates);
      rules = List.copyOf(rules);
      requireThat(validatedAt, "validatedAt").isNotNull();
    }
  }

  /**
   * Maximum allowed size for rule files before reading them into memory.
   * Files exceeding this limit cause an {@link IOException} to prevent OOM errors.
   */
  private static final long MAX_RULE_FILE_SIZE = 1024 * 1024;
  private static final int MAX_CACHE_ENTRIES = 128;
  private static final Duration CACHE_VALIDATION_TTL = Duration.ofSeconds(2);
  private static final Map<String, CachedRules> CACHE = new LinkedHashMap<>(16, 0.75f, true)
  {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, CachedRules> eldest)
    {
      return size() > MAX_CACHE_ENTRIES;
    }
  };

  private final Path rulesDir;
  private final YAMLMapper yamlMapper;

  /**
   * Creates a new RulesDiscovery instance.
   *
   * @param rulesDir   the directory to discover rules from
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
   * @throws IllegalArgumentException if any rule file has malformed YAML frontmatter
   */
  public List<RuleFile> discoverAll()
  {
    if (!Files.isDirectory(rulesDir))
      return List.of();

    try
    {
      Path normalizedRulesDir = rulesDir.toAbsolutePath().normalize();
      String cacheKey = normalizedRulesDir.toString();
      Instant now = Instant.now();
      synchronized (CACHE)
      {
        CachedRules cached = CACHE.get(cacheKey);
        if (cached != null && Duration.between(cached.validatedAt(), now).
          compareTo(CACHE_VALIDATION_TTL) < 0)
        {
          return cached.rules();
        }
      }

      List<Path> files = listRuleFiles();
      List<FileState> fileStates = getFileStates();
      synchronized (CACHE)
      {
        CachedRules cached = CACHE.get(cacheKey);
        if (cached != null && cached.fileStates().equals(fileStates))
        {
          CACHE.put(cacheKey, new CachedRules(fileStates, cached.rules(), now));
          return cached.rules();
        }
      }

      List<RuleFile> rules = new ArrayList<>();
      for (Path file : files)
      {
        if (Files.size(file) > MAX_RULE_FILE_SIZE)
          throw new IOException("Rule file exceeds 1 MB size limit: " + file.getFileName());
        String rawContent = SourceIncludeProcessor.expand(file, Files.readString(file), this::isAllowedIncludeTarget);
        rules.add(parseRuleFile(file, rawContent));
      }
      rules = List.copyOf(rules);
      synchronized (CACHE)
      {
        CACHE.put(cacheKey, new CachedRules(fileStates, rules, now));
      }
      return rules;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Lists markdown rule files in rules directory.
   *
   * @return sorted markdown rule files
   * @throws IOException if directory listing fails
   */
  private List<Path> listRuleFiles() throws IOException
  {
    List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(rulesDir, "*.md"))
    {
      stream.forEach(files::add);
    }
    files.sort(Comparator.naturalOrder());
    return files;
  }

  /**
   * Captures cache-relevant file metadata for rules and shared includes.
   *
   * @return immutable file-state snapshot
   * @throws IOException if metadata lookup fails
   */
  private List<FileState> getFileStates() throws IOException
  {
    List<FileState> fileStates = new ArrayList<>();
    Path normalizedRulesDir = rulesDir.toAbsolutePath().normalize();
    collectFileStates(fileStates, normalizedRulesDir, normalizedRulesDir);
    Path rulesRoot = normalizedRulesDir.getParent();
    if (rulesRoot != null)
    {
      Path includeDir = rulesRoot.resolve("include").normalize();
      if (Files.isDirectory(includeDir))
        collectFileStates(fileStates, normalizedRulesDir, includeDir);
    }
    return List.copyOf(fileStates);
  }

  /**
   * Collects file metadata beneath root for cache invalidation.
   *
   * @param fileStates destination list
   * @param normalizedRulesDir normalized rules directory used as relativization base
   * @param root directory to walk
   * @throws IOException if directory walk or metadata lookup fails
   */
  private void collectFileStates(List<FileState> fileStates, Path normalizedRulesDir, Path root)
    throws IOException
  {
    try (Stream<Path> stream = Files.walk(root))
    {
      List<Path> files = stream.
        filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).
        sorted().
        toList();
      for (Path file : files)
      {
        Path normalizedFile = file.toAbsolutePath().normalize();
        fileStates.add(new FileState(normalizedRulesDir.relativize(normalizedFile).toString(),
          Files.size(file), Files.getLastModifiedTime(file).toMillis()));
      }
    }
  }

  /**
   * Indicates whether include target stays within allowed rule directories.
   *
   * @param target resolved include target
   * @return {@code true} if target is under rules directory or shared include directory
   */
  private boolean isAllowedIncludeTarget(Path target)
  {
    Path normalizedRulesDir = rulesDir.toAbsolutePath().normalize();
    if (target.startsWith(normalizedRulesDir))
      return true;
    Path rulesRoot = normalizedRulesDir.getParent();
    if (rulesRoot == null)
      return false;
    Path sharedIncludeDir = rulesRoot.resolve("include").normalize();
    return target.startsWith(sharedIncludeDir);
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
    String frontmatter = FrontmatterUtils.extractFrontmatter(content);
    String body = FrontmatterUtils.stripFrontmatter(content);

    if (frontmatter == null)
      return new RuleFile(path, toContextPath(path), true, null, List.of(), body);

    try
    {
      JsonNode root = yamlMapper.readTree(frontmatter);
      boolean mainAgent = true;
      if (root.has("mainAgent"))
        mainAgent = root.get("mainAgent").asBoolean(true);
      List<String> subAgents = parseListNode(root.get("subAgents"), null);
      List<String> paths = parseListNode(root.get("paths"), List.of());
      return new RuleFile(path, toContextPath(path), mainAgent, subAgents, paths, body);
    }
    catch (Exception e)
    {
      throw new IllegalArgumentException("Malformed YAML frontmatter in " + path.getFileName() + ": " +
        e.getMessage(), e);
    }
  }

  /**
   * Maps rule file path to canonical context path exposed to agents.
   *
   * @param file rule file path
   * @return normalized context path
   */
  private String toContextPath(Path file)
  {
    Path relative = rulesDir.relativize(file);
    for (Path suffix : List.of(
      Path.of(".cat/rules/common"),
      Path.of(".cat/rules/claude"),
      Path.of(".cat/rules/codex"),
      Path.of(".claude/rules"),
      Path.of("rules/common"),
      Path.of("rules/claude"),
      Path.of("rules/codex")))
    {
      if (rulesDir.endsWith(suffix))
        return normalizeContextPath(suffix.resolve(relative));
    }
    return normalizeContextPath(file);
  }

  /**
   * Normalizes path separators for agent-facing context paths.
   *
   * @param path path to normalize
   * @return path using forward slashes
   */
  private String normalizeContextPath(Path path)
  {
    return path.toString().replace('\\', '/');
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
   * Filters rules for an agent, applying audience and paths filtering.
   *
   * @param rules       all discovered rules
   * @param agentType   the type identifier of the agent (e.g. {@code "cat:work-execute"})
   * @param activeFiles the list of files currently being operated on (for paths matching)
   * @return rules where subAgents is {@code null} (all) or contains the agent type, and paths match
   * @throws NullPointerException if any parameter is null
   */
  public static List<RuleFile> filterForAgent(List<RuleFile> rules, String agentType,
    List<String> activeFiles)
  {
    requireThat(rules, "rules").isNotNull();
    requireThat(agentType, "agentType").isNotNull();
    requireThat(activeFiles, "activeFiles").isNotNull();

    List<RuleFile> result = new ArrayList<>();
    for (RuleFile rule : rules)
    {
      List<String> subAgents = rule.subAgents();
      if (subAgents != null && !subAgents.contains(agentType))
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
   * Delegates to {@link #getCatRulesForAudience(List, YAMLMapper, BiFunction, List)} with a
   * single-element list containing {@code rulesDir}.
   *
   * @param rulesDir    the directory containing rule files
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
    return getCatRulesForAudience(List.of(rulesDir), yamlMapper, filterFn, activeFiles);
  }

  /**
   * Discovers, filters, and renders CAT rules from multiple source directories for an audience in
   * one step.
   * <p>
   * Discovers rules from all directories in order, concatenates them (no filename-based
   * deduplication), then applies the audience filter and renders the result. Returns an empty
   * string if all directories are missing, no rules survive the filter, or all content is blank.
   * <p>
   * Directories are processed in order: plugin-bundled rules first, then project-local rules.
   * Within each directory, rules are sorted alphabetically by filename.
   *
   * @param rulesDirs   the ordered list of directories to discover rules from
   * @param yamlMapper  the YAML mapper for parsing frontmatter
   * @param filterFn    function that takes all rules and active files, and returns the filtered rules
   * @param activeFiles the list of files currently active in the session (for paths matching)
   * @return the rendered rule content, or empty string if no rules apply
   * @throws NullPointerException if any parameter is null
   */
  public static String getCatRulesForAudience(List<Path> rulesDirs, YAMLMapper yamlMapper,
    BiFunction<List<RuleFile>, List<String>, List<RuleFile>> filterFn, List<String> activeFiles)
  {
    requireThat(rulesDirs, "rulesDirs").isNotNull();
    requireThat(yamlMapper, "yamlMapper").isNotNull();
    requireThat(filterFn, "filterFn").isNotNull();
    requireThat(activeFiles, "activeFiles").isNotNull();

    // Collect all rules from all directories in order (plugin-bundled first, project-local second).
    // No deduplication — if the same filename exists in both sources, both are included.
    List<RuleFile> allRules = new ArrayList<>();
    for (Path rulesDir : rulesDirs)
      allRules.addAll(new RulesDiscovery(rulesDir, yamlMapper).discoverAll());

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
    StringBuilder sb = new StringBuilder(rules.size() * 256);
    for (RuleFile rule : rules)
    {
      if (!sb.isEmpty())
        sb.append("\n\n");
      sb.append(render(rule));
    }
    return sb.toString();
  }

  /**
   * Renders one rule as XML-like wrapper block.
   *
   * @param rule rule to render
   * @return rendered rule block
   */
  private static String render(RuleFile rule)
  {
    return """
      <rule path="%s">
      %s
      </rule>""".formatted(escapeAttribute(rule.contextPath()), rule.content());
  }

  /**
   * Escapes a string for use in a double-quoted XML-like attribute.
   *
   * @param value the raw attribute value
   * @return the escaped attribute value
   */
  private static String escapeAttribute(String value)
  {
    StringBuilder escaped = new StringBuilder(value.length());
    value.codePoints().forEach(codePoint ->
    {
      switch (codePoint)
      {
        case '&' -> escaped.append("&amp;");
        case '<' -> escaped.append("&lt;");
        case '>' -> escaped.append("&gt;");
        case '"' -> escaped.append("&quot;");
        case '\'' -> escaped.append("&apos;");
        default ->
        {
          if (Character.isISOControl(codePoint))
            escaped.append("&#x").append(Integer.toHexString(codePoint).toUpperCase()).append(';');
          else
            escaped.appendCodePoint(codePoint);
        }
      }
    });
    return escaped.toString();
  }
}
