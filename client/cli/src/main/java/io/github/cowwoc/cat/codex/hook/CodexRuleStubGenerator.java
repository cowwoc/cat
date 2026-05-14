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
import io.github.cowwoc.cat.agent.RulesDiscovery;
import io.github.cowwoc.cat.agent.RulesDiscovery.RuleFile;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates Codex rule-loading stubs for path-scoped common rules.
 */
final class CodexRuleStubGenerator
{
  private static final String GENERATED_STUB_MARKER = "<!-- cat:generated-codex-rule-stub -->";
  private static final String GENERATED_STUB_MANIFEST = ".cat-generated-stubs";
  private static final String RULE_LOADING_DIRECTIVE = "Apply `rules/codex/rule-loading.md`.";

  private CodexRuleStubGenerator()
  {
  }

  /**
   * Generates Codex stubs for the installed plugin and project-local common rule directories.
   *
   * @param scope the active Codex plugin scope
   * @throws WrappedCheckedException if file operations fail
   */
  static void generate(AgentPluginScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    try
    {
      generate(scope.getPluginRoot().resolve("rules/common"),
        scope.getPluginRoot().resolve("rules/codex"), scope.getYamlMapper());
      generate(scope.getProjectPath().resolve(".cat/rules/common"),
        scope.getProjectPath().resolve(".cat/rules/codex"), scope.getYamlMapper());
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Generates Codex stubs for path-scoped Markdown rules in one common-rule directory.
   *
   * @param commonRules the directory containing common rules
   * @param codexRules the directory that should contain generated Codex stubs
   * @param yamlMapper the mapper used to read frontmatter
   * @throws IOException if file operations fail
   */
  private static void generate(Path commonRules, Path codexRules, YAMLMapper yamlMapper) throws IOException
  {
    Set<Path> expectedManagedStubs = new HashSet<>();
    if (!Files.isDirectory(commonRules, LinkOption.NOFOLLOW_LINKS))
    {
      pruneManagedStubs(codexRules, expectedManagedStubs);
      return;
    }
    for (RuleFile rule : new RulesDiscovery(commonRules, yamlMapper).discoverAll())
    {
      Path expectedStub = generate(rule, commonRules, codexRules);
      if (expectedStub != null)
        expectedManagedStubs.add(expectedStub.toAbsolutePath().normalize());
    }
    pruneManagedStubs(codexRules, expectedManagedStubs);
  }

  /**
   * Generates a Codex stub for one common rule if it has {@code paths} frontmatter.
   *
   * @param rule the common rule file
   * @param commonRules the common rule root directory
   * @param codexRules the Codex rule root directory
   * @return the expected stub path, or {@code null} if the rule does not produce a stub
   * @throws IOException if file operations fail
   */
  private static Path generate(RuleFile rule, Path commonRules, Path codexRules) throws IOException
  {
    if (rule.paths().isEmpty())
      return null;

    Path commonRule = rule.path();
    String title = extractTitle(rule.content(), commonRule);
    Path relative = commonRules.relativize(commonRule);
    Path codexStub = codexRules.resolve(relative).toAbsolutePath().normalize();
    Path normalizedCodexRules = codexRules.toAbsolutePath().normalize();
    if (!codexStub.startsWith(normalizedCodexRules))
      throw new IOException("Refusing to write Codex rule stub outside " + codexRules + ": " + codexStub);
    Path include = codexStub.getParent().relativize(commonRule);
    String stub = GENERATED_STUB_MARKER + "\n" +
      title + "\n\n" +
      "`paths` = " + toJsonArray(rule.paths()) + "\n" +
      "`include` = `" + include.toString().replace('\\', '/') + "`\n\n" +
      RULE_LOADING_DIRECTIVE + "\n";
    if (writeManagedStub(codexStub, stub))
      return codexStub;
    return null;
  }

  /**
   * Extracts the first Markdown heading from the common rule body.
   *
   * @param body the common rule body
   * @param source the common rule source path
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
   * Writes a generated stub unless an authored file already exists at the output path.
   *
   * @param path the stub path
   * @param content the generated stub content
   * @return true if the output path is managed by the generator
   * @throws IOException if file operations fail or the output path crosses a symbolic link
   */
  private static boolean writeManagedStub(Path path, String content) throws IOException
  {
    requireNoSymlinkInExistingPath(path.getParent());
    Files.createDirectories(path.getParent());
    requireNoSymlinkInExistingPath(path.getParent());
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
    {
      if (Files.isSymbolicLink(path))
        throw new IOException("Refusing to write Codex rule stub through symbolic link: " + path);
      if (!isManagedStub(path))
        return false;
    }
    FileSystemUtils.writeStringIfChanged(path, content);
    return true;
  }

  /**
   * Deletes generated stubs that no longer correspond to a path-scoped common rule.
   *
   * @param codexRules the Codex rule root
   * @param expectedStubs generated stub paths that should remain
   * @throws IOException if file operations fail or the root crosses a symbolic link
   */
  private static void pruneManagedStubs(Path codexRules, Set<Path> expectedStubs) throws IOException
  {
    if (!Files.exists(codexRules, LinkOption.NOFOLLOW_LINKS))
      return;
    requireNoSymlinkInExistingPath(codexRules);
    if (!Files.isDirectory(codexRules, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Codex rule stub root is not a directory: " + codexRules);
    if (pruneManagedStubsFromManifest(codexRules, expectedStubs))
    {
      writeManifest(codexRules, expectedStubs);
      return;
    }
    Files.walkFileTree(codexRules, new SimpleFileVisitor<>()
    {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
      {
        if (file.getFileName().toString().endsWith(".md") &&
          !expectedStubs.contains(file.toAbsolutePath().normalize()) && isManagedStub(file))
        {
          Files.delete(file);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
      {
        if (exc != null)
          throw exc;
        if (!dir.equals(codexRules))
        {
          try
          {
            Files.delete(dir);
          }
          catch (DirectoryNotEmptyException _)
          {
            // Authored files or expected stubs remain in this directory.
          }
        }
        return FileVisitResult.CONTINUE;
      }
    });
    writeManifest(codexRules, expectedStubs);
  }

  /**
   * Deletes stale generated stubs by reading the manifest from the previous run.
   *
   * @param codexRules the Codex rule root
   * @param expectedStubs generated stub paths that should remain
   * @return true if a valid manifest was used, false if callers should fall back to scanning
   * @throws IOException if file operations fail
   */
  private static boolean pruneManagedStubsFromManifest(Path codexRules, Set<Path> expectedStubs)
    throws IOException
  {
    Path manifest = codexRules.resolve(GENERATED_STUB_MANIFEST);
    if (!Files.exists(manifest, LinkOption.NOFOLLOW_LINKS))
      return false;
    if (Files.isSymbolicLink(manifest) || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Generated Codex rule stub manifest is not a regular file: " + manifest);
    Path normalizedCodexRules = codexRules.toAbsolutePath().normalize();
    List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
    for (String line : lines)
    {
      if (line.isBlank())
        continue;
      Path relative = Path.of(line).normalize();
      if (relative.isAbsolute() || relative.startsWith(".."))
        return false;
      Path staleCandidate = normalizedCodexRules.resolve(relative).normalize();
      if (!staleCandidate.startsWith(normalizedCodexRules))
        return false;
      if (!expectedStubs.contains(staleCandidate) && Files.exists(staleCandidate, LinkOption.NOFOLLOW_LINKS) &&
        isManagedStub(staleCandidate))
      {
        Files.delete(staleCandidate);
      }
    }
    pruneEmptyDirectories(codexRules);
    return true;
  }

  /**
   * Writes the generated-stub manifest for the next SessionStart run.
   *
   * @param codexRules the Codex rule root
   * @param expectedStubs generated stub paths that should remain
   * @throws IOException if file operations fail
   */
  private static void writeManifest(Path codexRules, Set<Path> expectedStubs) throws IOException
  {
    Path manifest = codexRules.resolve(GENERATED_STUB_MANIFEST);
    if (expectedStubs.isEmpty())
    {
      Files.deleteIfExists(manifest);
      return;
    }
    Path normalizedCodexRules = codexRules.toAbsolutePath().normalize();
    List<String> lines = new ArrayList<>(expectedStubs.size());
    for (Path expectedStub : expectedStubs)
      lines.add(normalizedCodexRules.relativize(expectedStub).toString().replace('\\', '/'));
    lines.sort(Comparator.naturalOrder());
    FileSystemUtils.writeStringIfChanged(manifest, String.join("\n", lines) + "\n");
  }

  /**
   * Deletes empty directories below a Codex rule root.
   *
   * @param codexRules the Codex rule root
   * @throws IOException if directory walking fails
   */
  private static void pruneEmptyDirectories(Path codexRules) throws IOException
  {
    Files.walkFileTree(codexRules, new SimpleFileVisitor<>()
    {
      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
      {
        if (exc != null)
          throw exc;
        if (!dir.equals(codexRules))
        {
          try
          {
            Files.delete(dir);
          }
          catch (DirectoryNotEmptyException _)
          {
            // Authored files or expected stubs remain in this directory.
          }
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Returns true if a file is owned by the Codex stub generator.
   *
   * @param path the candidate stub path
   * @return true if the file is a generated stub
   * @throws IOException if the file cannot be read
   */
  private static boolean isManagedStub(Path path) throws IOException
  {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      return false;
    String content = Files.readString(path, StandardCharsets.UTF_8);
    return content.startsWith(GENERATED_STUB_MARKER + "\n") ||
      (content.contains("`include` = `") && content.contains(RULE_LOADING_DIRECTIVE));
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
        throw new IOException("Refusing to write Codex rule stub through symbolic link: " + current);
    }
  }
}
