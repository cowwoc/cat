/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.AgentPluginScope;
import io.github.cowwoc.cat.agent.FileSystemUtils;
import io.github.cowwoc.cat.agent.FrontmatterUtils;
import io.github.cowwoc.cat.agent.RulesDiscovery;
import io.github.cowwoc.cat.agent.RulesDiscovery.RuleFile;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates Codex lazy-loading rule stubs and plugin-data body files for path-scoped rules.
 */
final class CodexPathRuleContext
{
  private static final Path GENERATED_ROOT = Path.of("generated/codex-rule-bodies");
  private static final String MANIFEST_FILE = "manifest.json";
  private static final Pattern INCLUDE_DECLARATION =
    Pattern.compile("(?m)^`include` = `([^`]+)`\\s*$");

  private CodexPathRuleContext()
  {
  }

  /**
   * Generates reusable path-scoped rule bodies for all audiences, then returns main-agent lazy-loading stubs.
   *
   * @param scope the active Codex plugin scope
   * @return stubs to inject into the main agent context
   * @throws WrappedCheckedException if file operations fail
   */
  static String generateForMain(AgentPluginScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    try
    {
      List<ManifestEntry> entries = generateManifest(scope);
      return render(entries.stream().filter(ManifestEntry::mainAgent).toList());
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Loads path-scoped rule stubs for a subagent from the main-agent manifest.
   *
   * @param scope     the active Codex plugin scope
   * @param agentName the subagent type
   * @return stubs to inject into the subagent context
   * @throws WrappedCheckedException if file operations fail
   */
  static String loadForAgent(AgentPluginScope scope, String agentName)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(agentName, "agentName").isNotBlank();
    try
    {
      Path manifest = generatedRoot(scope).resolve(MANIFEST_FILE);
      if (!Files.exists(manifest, LinkOption.NOFOLLOW_LINKS))
        return "";
      List<ManifestEntry> entries = readManifest(scope, manifest);
      return render(entries.stream().
        filter(entry -> appliesToAgent(entry, agentName)).
        filter(entry -> Files.isRegularFile(entry.bodyPath(), LinkOption.NOFOLLOW_LINKS)).
        toList());
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Generates the manifest and body files for all path-scoped rules visible to Codex.
   *
   * @param scope the active Codex plugin scope
   * @return the generated manifest entries
   * @throws IOException if file operations fail
   */
  private static List<ManifestEntry> generateManifest(AgentPluginScope scope) throws IOException
  {
    Path outputRoot = generatedRoot(scope);
    requireNoSymlinkInExistingPath(outputRoot);
    List<ManifestEntry> entries = new ArrayList<>();
    for (Path ruleDirectory : scope.getRuleDirectories())
    {
      if (!Files.isDirectory(ruleDirectory, LinkOption.NOFOLLOW_LINKS))
        continue;
      String namespace = namespaceFor(scope, ruleDirectory);
      for (RuleFile rule : new RulesDiscovery(ruleDirectory, scope.getYamlMapper()).discoverAll())
      {
        if (rule.paths().isEmpty())
          continue;
        entries.add(generateEntry(rule, outputRoot, namespace));
      }
    }
    entries.sort(Comparator.comparing(ManifestEntry::contextPath));
    pruneStaleBodies(outputRoot, readManifestIfPresent(scope), entries);
    writeManifest(scope, entries);
    return entries;
  }

  /**
   * Generates a body file for one path-scoped rule.
   *
   * @param rule       the rule source
   * @param outputRoot the generated body root
   * @param namespace  the source namespace
   * @return the manifest entry for the generated body
   * @throws IOException if file operations fail
   */
  private static ManifestEntry generateEntry(RuleFile rule, Path outputRoot, String namespace)
    throws IOException
  {
    Path bodyPath = bodyPath(outputRoot, namespace, rule.contextPath());
    BodySource bodySource = bodyFor(rule);
    Files.createDirectories(bodyPath.getParent());
    requireNoSymlinkInExistingPath(bodyPath.getParent());
    if (Files.exists(bodyPath, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(bodyPath))
      throw new IOException("Refusing to write Codex path-scoped rule body through symbolic link: " +
        bodyPath);
    FileSystemUtils.writeStringIfChanged(bodyPath, ensureTrailingNewline(bodySource.body()));
    return new ManifestEntry(rule.contextPath(), rule.mainAgent(), rule.subAgents(),
      List.copyOf(rule.paths()), extractTitle(rule.content(), rule.path()), bodySource.stubTemplate(),
      bodyPath);
  }

  /**
   * Resolves the generated body for a path-scoped rule.
   *
   * @param rule the rule source
   * @return the body to write into plugin data
   * @throws IOException if an included body cannot be read
   */
  private static BodySource bodyFor(RuleFile rule) throws IOException
  {
    Matcher matcher = INCLUDE_DECLARATION.matcher(rule.content());
    if (!matcher.find())
      return new BodySource(rule.content(), null);
    Path include = rule.path().getParent().resolve(matcher.group(1)).toAbsolutePath().normalize();
    if (!Files.isRegularFile(include, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(include))
    {
      throw new IOException("Codex path-scoped rule include is not a regular file: " + include);
    }
    String body = FrontmatterUtils.stripFrontmatter(Files.readString(include, StandardCharsets.UTF_8));
    return new BodySource(body, rule.content());
  }

  /**
   * Returns the generated body path for a source rule.
   *
   * @param outputRoot  the generated body root
   * @param namespace   the source namespace
   * @param contextPath the source rule context path
   * @return the generated body path
   * @throws IOException if the path would escape the output root
   */
  private static Path bodyPath(Path outputRoot, String namespace, String contextPath) throws IOException
  {
    Path relative = Path.of(contextPath).normalize();
    if (relative.isAbsolute() || relative.startsWith(".."))
      throw new IOException("Refusing to generate Codex rule body for unsafe context path: " +
        contextPath);
    Path output = outputRoot.resolve(namespace).resolve(relative).toAbsolutePath().normalize();
    if (!output.startsWith(outputRoot))
    {
      throw new IOException("Refusing to write Codex path-scoped rule body outside " + outputRoot +
        ": " + output);
    }
    return output;
  }

  /**
   * Returns the namespace for a source rule directory.
   *
   * @param scope         the active Codex plugin scope
   * @param ruleDirectory the source rule directory
   * @return the namespace
   */
  private static String namespaceFor(AgentPluginScope scope, Path ruleDirectory)
  {
    Path normalizedRuleDirectory = ruleDirectory.toAbsolutePath().normalize();
    Path pluginRoot = scope.getPluginRoot().toAbsolutePath().normalize();
    if (normalizedRuleDirectory.startsWith(pluginRoot))
      return "plugin";
    return "project";
  }

  /**
   * Reads the previous manifest if it exists.
   *
   * @param scope the active Codex plugin scope
   * @return previous entries, or an empty list
   * @throws IOException if the manifest exists but cannot be read safely
   */
  private static List<ManifestEntry> readManifestIfPresent(AgentPluginScope scope) throws IOException
  {
    Path manifest = generatedRoot(scope).resolve(MANIFEST_FILE);
    if (!Files.exists(manifest, LinkOption.NOFOLLOW_LINKS))
      return List.of();
    return readManifest(scope, manifest);
  }

  /**
   * Reads a generated-body manifest.
   *
   * @param scope    the active Codex plugin scope
   * @param manifest the manifest path
   * @return manifest entries
   * @throws IOException if the manifest cannot be read safely
   */
  private static List<ManifestEntry> readManifest(AgentPluginScope scope, Path manifest)
    throws IOException
  {
    if (Files.isSymbolicLink(manifest) || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Codex path-scoped rule manifest is not a regular file: " + manifest);
    JsonNode root = scope.getJsonMapper().readTree(Files.readString(manifest, StandardCharsets.UTF_8));
    JsonNode entriesNode = root.path("entries");
    if (!entriesNode.isArray())
      return List.of();
    List<ManifestEntry> result = new ArrayList<>();
    for (JsonNode entry : entriesNode)
    {
      String contextPath = entry.path("contextPath").asString();
      String bodyPathText = entry.path("bodyPath").asString();
      if (contextPath.isBlank() || bodyPathText.isBlank())
        continue;
      List<String> paths = strings(entry.path("paths"));
      if (paths.isEmpty())
        continue;
      List<String> subAgents;
      if (entry.path("subAgents").isNull())
        subAgents = null;
      else
        subAgents = strings(entry.path("subAgents"));
      JsonNode stubTemplate = entry.path("stubTemplate");
      String stubTemplateText = null;
      if (stubTemplate.isString())
        stubTemplateText = stubTemplate.asString();
      result.add(new ManifestEntry(contextPath, entry.path("mainAgent").asBoolean(false), subAgents,
        paths, entry.path("title").asString("# " + Path.of(contextPath).getFileName()),
        stubTemplateText, Path.of(bodyPathText)));
    }
    return result;
  }

  /**
   * Writes the generated-body manifest.
   *
   * @param scope   the active Codex plugin scope
   * @param entries the entries to write
   * @throws IOException if file operations fail
   */
  private static void writeManifest(AgentPluginScope scope, List<ManifestEntry> entries) throws IOException
  {
    Path outputRoot = generatedRoot(scope);
    Files.createDirectories(outputRoot);
    requireNoSymlinkInExistingPath(outputRoot);
    Path manifest = outputRoot.resolve(MANIFEST_FILE);
    if (entries.isEmpty())
    {
      Files.deleteIfExists(manifest);
      return;
    }

    ObjectNode root = scope.getJsonMapper().createObjectNode();
    root.put("version", 1);
    ArrayNode entriesNode = root.putArray("entries");
    for (ManifestEntry entry : entries)
    {
      ObjectNode entryNode = entriesNode.addObject();
      entryNode.put("contextPath", entry.contextPath());
      entryNode.put("mainAgent", entry.mainAgent());
      if (entry.subAgents() == null)
      {
        entryNode.putNull("subAgents");
      }
      else
      {
        ArrayNode subAgents = entryNode.putArray("subAgents");
        for (String subAgent : entry.subAgents())
          subAgents.add(subAgent);
      }
      ArrayNode paths = entryNode.putArray("paths");
      for (String path : entry.paths())
        paths.add(path);
      entryNode.put("title", entry.title());
      if (entry.stubTemplate() == null)
        entryNode.putNull("stubTemplate");
      else
        entryNode.put("stubTemplate", entry.stubTemplate());
      entryNode.put("bodyPath", entry.bodyPath().toString());
    }
    FileSystemUtils.writeStringIfChanged(manifest,
      scope.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
  }

  /**
   * Deletes generated body files no longer present in the new manifest.
   *
   * @param outputRoot      the generated body root
   * @param previousEntries previous manifest entries
   * @param currentEntries  current manifest entries
   * @throws IOException if file operations fail
   */
  private static void pruneStaleBodies(Path outputRoot, List<ManifestEntry> previousEntries,
    List<ManifestEntry> currentEntries) throws IOException
  {
    Set<Path> currentBodies = new HashSet<>();
    for (ManifestEntry entry : currentEntries)
      currentBodies.add(entry.bodyPath().toAbsolutePath().normalize());
    for (ManifestEntry previous : previousEntries)
    {
      Path previousBody = previous.bodyPath().toAbsolutePath().normalize();
      if (!previousBody.startsWith(outputRoot) || currentBodies.contains(previousBody) ||
        !Files.exists(previousBody, LinkOption.NOFOLLOW_LINKS))
      {
        continue;
      }
      Files.delete(previousBody);
    }
    pruneEmptyDirectories(outputRoot);
  }

  /**
   * Deletes empty generated-body directories.
   *
   * @param outputRoot the generated body root
   * @throws IOException if directory walking fails
   */
  private static void pruneEmptyDirectories(Path outputRoot) throws IOException
  {
    if (!Files.isDirectory(outputRoot, LinkOption.NOFOLLOW_LINKS))
      return;
    Files.walkFileTree(outputRoot, new SimpleFileVisitor<>()
    {
      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
      {
        if (exc != null)
          throw exc;
        if (!dir.equals(outputRoot))
        {
          try
          {
            Files.delete(dir);
          }
          catch (DirectoryNotEmptyException _)
          {
            // Generated files remain in this directory.
          }
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Renders manifest entries as agent-facing lazy-loading stubs.
   *
   * @param entries the entries to render
   * @return rendered context
   */
  private static String render(List<ManifestEntry> entries)
  {
    if (entries.isEmpty())
      return "";
    List<String> rendered = new ArrayList<>(entries.size());
    for (ManifestEntry entry : entries)
      rendered.add(renderRule(entry));
    return String.join("\n\n", rendered);
  }

  /**
   * Renders one lazy-loading rule stub.
   *
   * @param entry the manifest entry
   * @return rendered rule context
   */
  private static String renderRule(ManifestEntry entry)
  {
    String bodyPath = entry.bodyPath().toString().replace('\\', '/');
    String stub;
    if (entry.stubTemplate() == null)
    {
      String directive;
      if (entry.contextPath().startsWith(".cat/"))
        directive = "Apply `.cat/rules/codex/rule-loading.md`.";
      else
        directive = "Apply `rules/codex/rule-loading.md`.";
      stub = entry.title() + "\n\n" +
        "`paths` = " + toJsonArray(entry.paths()) + "\n" +
        "`include` = `" + bodyPath + "`\n\n" +
        directive + "\n";
    }
    else
    {
      stub = renderTemplateStub(entry.stubTemplate(), entry.paths(), bodyPath);
    }
    return "<rule path=\"" + escapeXmlAttribute(entry.contextPath()) + "\">\n" + stub + "</rule>";
  }

  /**
   * Renders an authored Codex stub template with generated path metadata.
   *
   * @param template the authored stub template
   * @param paths    the path globs
   * @param bodyPath the generated body path
   * @return the rendered stub
   */
  private static String renderTemplateStub(String template, List<String> paths, String bodyPath)
  {
    String replacement = "`paths` = " + toJsonArray(paths) + "\n" +
      "`include` = `" + bodyPath + "`";
    Matcher matcher = INCLUDE_DECLARATION.matcher(template);
    String rendered;
    if (matcher.find())
      rendered = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    else
      rendered = template + "\n\n" + replacement + "\n";
    return ensureTrailingNewline(rendered);
  }

  /**
   * Returns true if a manifest entry applies to a subagent.
   *
   * @param entry     the manifest entry
   * @param agentName the subagent type
   * @return true if the entry applies
   */
  private static boolean appliesToAgent(ManifestEntry entry, String agentName)
  {
    return entry.subAgents() == null || entry.subAgents().contains(agentName);
  }

  /**
   * Extracts strings from a JSON array.
   *
   * @param node the array node
   * @return strings in the array
   */
  private static List<String> strings(JsonNode node)
  {
    if (!node.isArray())
      return List.of();
    List<String> result = new ArrayList<>();
    for (JsonNode item : node)
    {
      if (item.isString())
        result.add(item.asString());
    }
    return result;
  }

  /**
   * Ensures generated Markdown files end with a newline.
   *
   * @param content the generated content
   * @return the content with one trailing newline
   */
  private static String ensureTrailingNewline(String content)
  {
    if (content.endsWith("\n"))
      return content;
    return content + "\n";
  }

  /**
   * Extracts the first Markdown heading from a rule body.
   *
   * @param body   the rule body
   * @param source the source path
   * @return the heading to use in the generated stub
   */
  private static String extractTitle(String body, Path source)
  {
    for (String line : body.split("\\R"))
    {
      if (line.startsWith("# "))
        return line.strip();
    }
    String fileName = source.getFileName().toString();
    String title = fileName.substring(0, fileName.length() - ".md".length()).
      replace('-', ' ');
    return "# " + title;
  }

  /**
   * Renders path globs as a JSON-style array for Codex frontmatter emulation.
   *
   * @param paths the path globs
   * @return the rendered array
   */
  private static String toJsonArray(List<String> paths)
  {
    StringBuilder result = new StringBuilder(paths.size() * 16 + 2);
    result.append('[');
    for (int i = 0; i < paths.size(); ++i)
    {
      if (i > 0)
        result.append(", ");
      result.append('"').append(escapeJsonString(paths.get(i))).append('"');
    }
    result.append(']');
    return result.toString();
  }

  /**
   * Escapes a string for use inside a JSON double-quoted string.
   *
   * @param value the raw string
   * @return the escaped string
   */
  private static String escapeJsonString(String value)
  {
    StringBuilder result = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); ++i)
    {
      char ch = value.charAt(i);
      switch (ch)
      {
        case '\\' -> result.append("\\\\");
        case '"' -> result.append("\\\"");
        case '\b' -> result.append("\\b");
        case '\f' -> result.append("\\f");
        case '\n' -> result.append("\\n");
        case '\r' -> result.append("\\r");
        case '\t' -> result.append("\\t");
        default ->
        {
          if (ch < 0x20)
            result.append("\\u%04x".formatted((int) ch));
          else
            result.append(ch);
        }
      }
    }
    return result.toString();
  }

  /**
   * Escapes a string for use inside an XML attribute.
   *
   * @param value the raw string
   * @return the escaped string
   */
  private static String escapeXmlAttribute(String value)
  {
    return value.replace("&", "&amp;").replace("\"", "&quot;").
      replace("<", "&lt;").replace(">", "&gt;");
  }

  /**
   * Fails if any existing component of a path is a symbolic link.
   *
   * @param path the path to validate
   * @throws IOException if a symbolic link is present
   */
  private static void requireNoSymlinkInExistingPath(Path path) throws IOException
  {
    Path absolute = path.toAbsolutePath().normalize();
    Path current = absolute.getRoot();
    for (Path part : absolute)
    {
      if (current == null)
        current = part;
      else
        current = current.resolve(part);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
      {
        throw new IOException("Refusing to write Codex path-scoped rule body through symbolic link: " +
          current);
      }
    }
  }

  /**
   * Returns the generated-body root.
   *
   * @param scope the active Codex plugin scope
   * @return the generated-body root
   */
  private static Path generatedRoot(AgentPluginScope scope)
  {
    return scope.getPluginData().resolve(GENERATED_ROOT).toAbsolutePath().normalize();
  }

  /**
   * Manifest entry describing one path-scoped rule body.
   *
   * @param contextPath the source rule context path
   * @param mainAgent   true if the rule applies to the main agent
   * @param subAgents   null for all subagents, empty for none, otherwise explicit agent names
   * @param paths       the path globs that activate the rule
   * @param title       the heading to show in the lazy-loading stub
   * @param stubTemplate the authored stub template, or null for generated title-only stubs
   * @param bodyPath    the generated body file path
   */
  private record ManifestEntry(String contextPath, boolean mainAgent, List<String> subAgents,
                               List<String> paths, String title, String stubTemplate, Path bodyPath)
  {
    /**
     * Creates a manifest entry.
     *
     * @param contextPath  the source rule context path
     * @param mainAgent    true if the rule applies to the main agent
     * @param subAgents    null for all subagents, empty for none, otherwise explicit agent names
     * @param paths        the path globs that activate the rule
     * @param title        the heading to show in the lazy-loading stub
     * @param stubTemplate the authored stub template, or null for generated title-only stubs
     * @param bodyPath     the generated body file path
     */
    private ManifestEntry
    {
      requireThat(contextPath, "contextPath").isNotBlank();
      requireThat(paths, "paths").isNotEmpty();
      requireThat(title, "title").isNotBlank();
      requireThat(bodyPath, "bodyPath").isNotNull();
      if (subAgents != null)
        subAgents = List.copyOf(subAgents);
      paths = List.copyOf(paths);
      bodyPath = bodyPath.toAbsolutePath().normalize();
    }
  }

  /**
   * Path-scoped rule body and optional authored stub template.
   *
   * @param body         the body file content
   * @param stubTemplate the authored stub template, or null for generated title-only stubs
   */
  private record BodySource(String body, String stubTemplate)
  {
    /**
     * Creates a body source.
     *
     * @param body         the body file content
     * @param stubTemplate the authored stub template, or null for generated title-only stubs
     */
    private BodySource
    {
      requireThat(body, "body").isNotNull();
    }
  }
}
