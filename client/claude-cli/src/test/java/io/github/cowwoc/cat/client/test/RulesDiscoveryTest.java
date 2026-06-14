/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.agent.FrontmatterUtils;
import io.github.cowwoc.cat.agent.GlobMatcher;
import io.github.cowwoc.cat.agent.RulesDiscovery;
import io.github.cowwoc.cat.agent.RulesDiscovery.RuleFile;
import org.testng.annotations.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for RulesDiscovery - frontmatter parsing, audience filtering, and paths matching.
 */
public final class RulesDiscoveryTest
{
  private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

  // ---- Frontmatter parsing ----

  /**
   * Verifies that a file with no frontmatter gets default values:
   * mainAgent=true, subAgents=null (all), paths=[] (always inject).
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void noFrontmatterUsesDefaults() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("plain.md"), "# Plain rule\nSome content.");

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();

      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      RuleFile rule = rules.getFirst();
      requireThat(rule.mainAgent(), "mainAgent").isTrue();
      requireThat(rule.subAgents(), "subAgents").isNull();
      requireThat(rule.paths(), "paths").isEmpty();
      requireThat(rule.content(), "content").contains("Plain rule");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that index files document rule directories without being injected as rules.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void indexFilesAreNotDiscoveredAsRules() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("index.md"), "# Rule index\n");
      Files.writeString(rulesDir.resolve("INDEX.md"), "# Legacy rule index\n");
      Files.writeString(rulesDir.resolve("plain.md"), "# Plain rule\n");

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();

      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      requireThat(rules.getFirst().path().getFileName().toString(), "ruleFile").isEqualTo("plain.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that bundled rule files do not declare frontmatter values that match loader defaults.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void bundledRulesOmitDefaultFrontmatterValues() throws IOException
  {
    Path rulesDir = findRepositoryRoot().resolve("client/plugin/rules");
    List<Path> violations = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(rulesDir))
    {
      for (Path ruleFile : stream.filter(Files::isRegularFile).filter(path ->
        path.getFileName().toString().endsWith(".md")).toList())
      {
        String frontmatter = FrontmatterUtils.extractFrontmatter(Files.readString(ruleFile));
        if (frontmatter == null)
          continue;
        if (frontmatter.lines().anyMatch(line -> line.equals("mainAgent: true") ||
          line.equals("subAgents: []") || line.equals("agents: [\"main\", \"subagents\"]") ||
          line.equals("paths: []")))
          violations.add(rulesDir.relativize(ruleFile));
      }
    }
    requireThat(violations, "violations").isEmpty();
  }

  /**
   * Verifies that rule files can include shared fragments relative to the source file directory.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void catIncludeResolvesRelativeToRuleFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir.resolve("fragments"));
      Files.writeString(rulesDir.resolve("fragments/shared.md"), "Shared fragment.\n");
      Files.writeString(rulesDir.resolve("rule.md"), """
        # Rule
        <!-- cat:include fragments/shared.md -->
        """);

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();

      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      requireThat(rules.getFirst().content(), "content").contains("Shared fragment.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that included rule fragments fail fast when they contain YAML frontmatter.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void catIncludeRejectsIncludedFrontmatter() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir.resolve("fragments"));
      Files.writeString(rulesDir.resolve("fragments/shared.md"), """
        ---
        paths: ["*.java"]
        ---
        Shared fragment.
        """);
      Files.writeString(rulesDir.resolve("rule.md"), """
        # Rule
        <!-- cat:include fragments/shared.md -->
        """);

      try
      {
        new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      }
      catch (IllegalStateException e)
      {
        requireThat(e.getMessage(), "message").contains("cat:include target must not contain YAML frontmatter");
        requireThat(e.getMessage(), "message").contains("shared.md");
        return;
      }
      throw new AssertionError("Expected cat:include frontmatter rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that agents: ["subagents"] excludes a file from main agent injection.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void agentsSubagentsExcludesFromMainAgent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("subagent-only.md"), """
        ---
        agents: ["subagents"]
        ---
        # Subagent only
        """);

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      requireThat(rules.getFirst().mainAgent(), "mainAgent").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that agents: ["main"] means no subagents receive this rule.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void agentsMainExcludesFromAllSubagents() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("main-only.md"), """
        ---
        agents: ["main"]
        ---
        # Main agent only
        """);

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      requireThat(rules.getFirst().subAgents(), "subAgents").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that agents with specific types only includes those types.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void agentsSpecificTypeParsedCorrectly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("targeted.md"), """
        ---
        agents: ["main", "cat:work-execute", "Explore"]
        ---
        # Targeted rule
        """);

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      List<String> subAgents = rules.getFirst().subAgents();
      requireThat(subAgents.size(), "subAgents.size()").isEqualTo(2);
      requireThat(subAgents.contains("cat:work-execute"), "containsWorkExecute").isTrue();
      requireThat(subAgents.contains("Explore"), "containsExplore").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that paths frontmatter is parsed into a list of glob patterns.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void pathsFrontmatterParsedCorrectly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("java-only.md"), """
        ---
        paths: ["*.java", "src/main/**"]
        ---
        # Java conventions
        """);

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      List<String> paths = rules.getFirst().paths();
      requireThat(paths.size(), "paths.size()").isEqualTo(2);
      requireThat(paths.contains("*.java"), "containsJavaGlob").isTrue();
      requireThat(paths.contains("src/main/**"), "containsSrcMainGlob").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Audience filtering ----

  /**
   * Verifies that filterForMainAgent returns only rules where mainAgent=true.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void filterForMainAgentExcludesSubagentOnly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("main-rule.md"), """
        ---
        agents: ["main"]
        ---
        # Main rule
        """);
      Files.writeString(rulesDir.resolve("subagent-rule.md"), """
        ---
        agents: ["subagents"]
        ---
        # Subagent rule
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();
      List<RuleFile> mainRules = RulesDiscovery.filterForMainAgent(allRules, List.of());

      requireThat(mainRules.size(), "mainRules.size()").isEqualTo(1);
      requireThat(mainRules.getFirst().content(), "content").contains("Main rule");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that filterForSubagent returns rules with null subAgents (meaning all subagents).
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void filterForSubagentNullMatchesAnySubagent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      // No agents frontmatter → null → matches all subagents
      Files.writeString(rulesDir.resolve("universal.md"), """
        ---
        agents: ["main", "subagents"]
        ---
        # Universal rule
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();
      List<RuleFile> subagentRules = RulesDiscovery.filterForAgent(allRules,
        "SomeRandomAgent", List.of());

      requireThat(subagentRules.size(), "subagentRules.size()").isEqualTo(1);
      requireThat(subagentRules.getFirst().content(), "content").contains("Universal rule");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that filterForSubagent with empty subAgents excludes all subagents.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void filterForSubagentEmptySubAgentsExcludes() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("main-only.md"), """
        ---
        agents: ["main"]
        ---
        # Main only
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();
      List<RuleFile> subagentRules = RulesDiscovery.filterForAgent(allRules, "AnyAgent",
        List.of());

      requireThat(subagentRules, "subagentRules").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that filterForSubagent with specific types only matches matching subagent type.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void filterForSubagentSpecificTypeMatchesOnly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("targeted.md"), """
        ---
        agents: ["cat:work-execute"]
        ---
        # Only for work-execute
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();

      List<RuleFile> matching = RulesDiscovery.filterForAgent(allRules, "cat:work-execute",
        List.of());
      List<RuleFile> nonMatching = RulesDiscovery.filterForAgent(allRules, "Explore",
        List.of());

      requireThat(matching.size(), "matching.size()").isEqualTo(1);
      requireThat(nonMatching, "nonMatching").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Paths matching ----

  /**
   * Verifies that a rule with no paths is always injected regardless of active paths.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void noPathsAlwaysInjected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("always.md"), """
        ---
        agents: ["main", "subagents"]
        ---
        # Always rule
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();

      // No active paths - should still match
      List<RuleFile> matched = RulesDiscovery.filterForMainAgent(allRules, List.of());
      requireThat(matched.size(), "matched.size()").isEqualTo(1);

      // With active paths - should still match (no paths restriction)
      List<RuleFile> matchedWithPaths = RulesDiscovery.filterForMainAgent(allRules,
        List.of("SomeFile.ts"));
      requireThat(matchedWithPaths.size(), "matchedWithPaths.size()").isEqualTo(1);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a rule with paths: ["*.java"] only matches when a .java file is active.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void pathsGlobMatchesMatchingFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java conventions
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();

      // No active files - should NOT inject paths-restricted rules
      List<RuleFile> noMatch = RulesDiscovery.filterForMainAgent(allRules, List.of());
      requireThat(noMatch, "noMatch").isEmpty();

      // Active java file - should inject
      List<RuleFile> matched = RulesDiscovery.filterForMainAgent(allRules,
        List.of("MyClass.java"));
      requireThat(matched.size(), "matched.size()").isEqualTo(1);

      // Active non-java file - should NOT inject
      List<RuleFile> noMatchTs = RulesDiscovery.filterForMainAgent(allRules,
        List.of("MyScript.ts"));
      requireThat(noMatchTs, "noMatchTs").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a rule with paths matches any file in the active list.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void pathsGlobMatchesAnyFileInActiveList() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java conventions
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();

      // Multiple active files including a java file - should inject
      List<RuleFile> matched = RulesDiscovery.filterForMainAgent(allRules,
        List.of("README.md", "MyClass.java", "build.xml"));
      requireThat(matched.size(), "matched.size()").isEqualTo(1);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Audience combination: agents ["main"] ----

  /**
   * Verifies that a rule with agents ["main"] is included by filterForMainAgent
   * and excluded by filterForSubagent.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void agentsMainIncludedByMainOnly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("main-only.md"), """
        ---
        agents: ["main"]
        ---
        # Main agent only rule
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();

      List<RuleFile> mainRules = RulesDiscovery.filterForMainAgent(allRules, List.of());
      requireThat(mainRules.size(), "mainRules.size()").isEqualTo(1);
      requireThat(mainRules.getFirst().content(), "content").contains("Main agent only rule");

      List<RuleFile> subRules = RulesDiscovery.filterForAgent(allRules, "AnyAgent", List.of());
      requireThat(subRules, "subRules").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Malformed frontmatter ----

  /**
   * Verifies that a file starting with {@code ---} but missing the closing {@code ---}
   * is treated as having no frontmatter (defaults applied).
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void unclosedFrontmatterUsesDefaults() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("unclosed.md"), """
        ---
        agents: ["subagents"]
        # No closing ---
        # Content after
        """);

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      RuleFile rule = rules.getFirst();
      requireThat(rule.mainAgent(), "mainAgent").isTrue();
      requireThat(rule.subAgents(), "subAgents").isNull();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that frontmatter with an unrecognized key is parsed without error, applying defaults
   * for the unrecognized key while correctly parsing known keys.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void unrecognizedFrontmatterKeyIgnored() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("unknown-key.md"), """
        ---
        agents: ["main"]
        unknownKey: somevalue
        ---
        # Content
        """);

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      RuleFile rule = rules.getFirst();
      requireThat(rule.mainAgent(), "mainAgent").isTrue();
      requireThat(rule.subAgents(), "subAgents").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that legacy audience keys are rejected.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void legacyAudienceKeysRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("yes-value.md"), """
        ---
        mainAgent: false
        ---
        # Legacy rule
        """);

      try
      {
        new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Legacy rule audience frontmatter");
        return;
      }
      throw new AssertionError("Expected legacy audience rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid {@code agents} frontmatter fails fast.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void invalidAgentsRejected() throws IOException
  {
    assertInvalidAgents("agents: []", "agents must not be empty");
    assertInvalidAgents("agents: main", "agents must be a non-empty YAML list");
    assertInvalidAgents("agents: [true]", "agents values must be non-blank strings");
    assertInvalidAgents("agents: [\" \"]", "agents values must be non-blank strings");
    assertInvalidAgents("agents: [\"subagents\", \"cat:work-execute\"]",
      "agents must not combine \"subagents\" with specific subagent names");
  }

  // ---- matchesGlob patterns ----

  /**
   * Verifies that {@code **} matches across path separators.
   */
  @Test
  public void matchesGlobDoubleStarMatchesAcrossPath()
  {
    requireThat(GlobMatcher.matches("src/main/**", "src/main/java/MyClass.java"),
      "matchesGlob(**,deep)").isTrue();
    requireThat(GlobMatcher.matches("src/main/**", "src/main/MyFile.java"),
      "matchesGlob(**,shallow)").isTrue();
    requireThat(GlobMatcher.matches("src/main/**", "src/test/MyTest.java"),
      "matchesGlob(**,nonMatch)").isFalse();
  }

  /**
   * Verifies that {@code ?} matches a single non-separator character.
   */
  @Test
  public void matchesGlobQuestionMarkMatchesSingleNon()
  {
    requireThat(GlobMatcher.matches("Foo?.java", "FooA.java"),
      "matchesGlob(?,single)").isTrue();
    requireThat(GlobMatcher.matches("Foo?.java", "Foo.java"),
      "matchesGlob(?,noChar)").isFalse();
    requireThat(GlobMatcher.matches("Foo?.java", "FooAB.java"),
      "matchesGlob(?,twoChars)").isFalse();
    requireThat(GlobMatcher.matches("Foo?.java", "Foo/A.java"),
      "matchesGlob(?,separator)").isFalse();
  }

  /**
   * Verifies that glob patterns containing regex metacharacters are handled correctly.
   */
  @Test
  public void matchesGlobRegexMetacharactersEscaped()
  {
    requireThat(GlobMatcher.matches("pom.xml", "pom.xml"), "matchesGlob(dot,exact)").isTrue();
    requireThat(GlobMatcher.matches("pom.xml", "pomXxml"), "matchesGlob(dot,notWildcard)").isFalse();
    requireThat(GlobMatcher.matches("file+test.java", "file+test.java"),
      "matchesGlob(plus,exact)").isTrue();
  }

  // ---- Full-path active file matching ----

  /**
   * Verifies that paths matching works correctly with full paths as active files.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void pathsMatchingWithFullPathActiveFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java conventions
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();

      // Full path matching by filename component
      List<RuleFile> matched = RulesDiscovery.filterForMainAgent(allRules,
        List.of("src/main/java/MyClass.java"));
      requireThat(matched.size(), "matched.size()").isEqualTo(1);

      // Full path, non-matching file
      List<RuleFile> noMatch = RulesDiscovery.filterForMainAgent(allRules,
        List.of("src/main/resources/config.json"));
      requireThat(noMatch, "noMatch").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a glob like {@code src/main/**} matches full-path active files.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void pathsMatchingWithDeepGlobAndFullPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("src-main.md"), """
        ---
        paths: ["src/main/**"]
        ---
        # Source conventions
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();

      // Full path matching the glob
      List<RuleFile> matched = RulesDiscovery.filterForMainAgent(allRules,
        List.of("src/main/java/io/example/MyClass.java"));
      requireThat(matched.size(), "matched.size()").isEqualTo(1);

      // Path not under src/main/
      List<RuleFile> noMatch = RulesDiscovery.filterForMainAgent(allRules,
        List.of("src/test/java/io/example/MyTest.java"));
      requireThat(noMatch, "noMatch").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Directory not found ----

  /**
   * Verifies that discoverAll returns empty list when rules directory does not exist.
   */
  @Test
  public void rulesDirectoryAbsentReturnsEmpty()
  {
    Path nonExistentDir = Path.of("/tmp/this-dir-does-not-exist-12345/rules");
    List<RuleFile> rules = new RulesDiscovery(nonExistentDir, YAML_MAPPER).discoverAll();
    requireThat(rules, "rules").isEmpty();
  }

  // ---- Content rendering ----

  /**
   * Verifies that renderAll concatenates all rule content with separators.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void renderAllConcatenatesContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("a-rule.md"), """
        # Rule A
        Content A.
        """);
      Files.writeString(rulesDir.resolve("b-rule.md"), """
        # Rule B
        Content B.
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();
      List<RuleFile> filtered = RulesDiscovery.filterForMainAgent(allRules, List.of());

      String rendered = RulesDiscovery.renderAll(filtered);
      requireThat(rendered, "rendered").contains("Rule A");
      requireThat(rendered, "rendered").contains("Rule B");
      requireThat(rendered, "rendered").contains("Content A.");
      requireThat(rendered, "rendered").contains("Content B.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that renderAll returns empty string for empty list.
   */
  @Test
  public void renderAllEmptyListReturnsEmpty()
  {
    String rendered = RulesDiscovery.renderAll(List.of());
    requireThat(rendered, "rendered").isEmpty();
  }

  /**
   * Verifies that rule paths are escaped before being rendered as XML-like attributes.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void renderAllEscapesRulePathAttribute() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-render-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("quote\"amp&lt<gt>apos'ctrl" + '\u0001' + "rule.md"),
        "# Quoted rule\n");

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      String rendered = RulesDiscovery.renderAll(rules);

      requireThat(rendered, "rendered").contains(
        "quote&quot;amp&amp;lt&lt;gt&gt;apos&apos;ctrl&#x1;rule.md");
      requireThat(rendered, "rendered").doesNotContain("quote\"amp&lt<gt>apos'ctrl");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Concern 7: stripFrontmatter with no trailing newline after closing --- ----

  /**
   * Verifies that a rule file whose frontmatter ends exactly at the closing {@code ---} with no
   * trailing newline (i.e. {@code ---\nkey: val\n---}) is parsed correctly, yielding an empty body
   * and the frontmatter values.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void frontmatterNoTrailingNewlineProduces() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-notail-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      // No newline after the closing ---
      Files.writeString(rulesDir.resolve("notail.md"), "---\nagents: [\"subagents\"]\n---");

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      RuleFile rule = rules.getFirst();
      requireThat(rule.mainAgent(), "mainAgent").isFalse();
      requireThat(rule.content(), "content").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Concern 8: alphabetical sort order ----

  /**
   * Verifies that discoverAll returns rule files sorted alphabetically by filename,
   * regardless of filesystem creation order.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void discoverAllReturnsSortedAlphabetically() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-sort-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      // Create in reverse alphabetical order
      Files.writeString(rulesDir.resolve("z-rule.md"), "# Z Rule");
      Files.writeString(rulesDir.resolve("a-rule.md"), "# A Rule");
      Files.writeString(rulesDir.resolve("m-rule.md"), "# M Rule");

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();

      requireThat(rules.size(), "rules.size()").isEqualTo(3);
      requireThat(rules.get(0).path().getFileName().toString(), "first").isEqualTo("a-rule.md");
      requireThat(rules.get(1).path().getFileName().toString(), "second").isEqualTo("m-rule.md");
      requireThat(rules.get(2).path().getFileName().toString(), "third").isEqualTo("z-rule.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Concern 9: unclosed frontmatter treated as no frontmatter ----

  /**
   * Verifies that a rule file starting with {@code ---} that never has a closing {@code ---} line
   * is treated as having no frontmatter: defaults are applied and the full content is used as body.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void unclosedFrontmatterBodyIsFullContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-unclosed-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      // No closing ---
      String rawContent = "---\nagents: [\"subagents\"]\nNo closing delimiter here.";
      Files.writeString(rulesDir.resolve("unclosed.md"), rawContent);

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      RuleFile rule = rules.getFirst();
      // No frontmatter parsed — defaults apply
      requireThat(rule.mainAgent(), "mainAgent").isTrue();
      requireThat(rule.subAgents(), "subAgents").isNull();
      // Full content is used as body (stripped)
      requireThat(rule.content(), "content").contains("agents: [\"subagents\"]");
      requireThat(rule.content(), "content").contains("No closing delimiter here.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Concern 10: glob ? wildcard and trailing slash ----

  /**
   * Verifies that the {@code ?} wildcard matches exactly one non-separator character
   * and does not match a path separator or zero characters.
   */
  @Test
  public void matchesGlobQuestionMarkEdgeCases()
  {
    // Matches exactly one character
    requireThat(GlobMatcher.matches("file?.txt", "fileA.txt"),
      "matchesGlob(?,singleChar)").isTrue();
    // Does NOT match zero characters
    requireThat(GlobMatcher.matches("file?.txt", "file.txt"),
      "matchesGlob(?,zero)").isFalse();
    // Does NOT match two characters
    requireThat(GlobMatcher.matches("file?.txt", "fileAB.txt"),
      "matchesGlob(?,twoChars)").isFalse();
    // Does NOT match a path separator
    requireThat(GlobMatcher.matches("file?.txt", "file/.txt"),
      "matchesGlob(?,separator)").isFalse();
  }

  /**
   * Verifies that a glob pattern {@code src/*} matches a one-level path under {@code src/}
   * and does not match paths under a different root.
   */
  @Test
  public void matchesGlobSingleStarDoesNotCrossPath()
  {
    // Matches one path segment under src/
    requireThat(GlobMatcher.matches("src/*", "src/main"),
      "matchesGlob(src/*,single-segment)").isTrue();
    // Does NOT match multiple segments (single * does not cross separator)
    requireThat(GlobMatcher.matches("src/*", "src/main/sub"),
      "matchesGlob(src/*,multiSegment)").isFalse();
    // Does NOT match a different root
    requireThat(GlobMatcher.matches("src/*", "other/main"),
      "matchesGlob(src/*,noMatch)").isFalse();
  }

  // ---- Concern 11: subagent exact-match vs non-match ----

  /**
   * Verifies that filterForSubagent with {@code agents: ["cat:work-execute"]} matches only
   * that exact type and not other subagent types.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void filterForSubagentExactTypeMatchesOnly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-exact-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("work-only.md"), """
        ---
        agents: ["cat:work-execute"]
        ---
        # Work execute rule
        """);

      RulesDiscovery discovery = new RulesDiscovery(rulesDir, YAML_MAPPER);
      List<RuleFile> allRules = discovery.discoverAll();

      // Should match the exact type
      List<RuleFile> exactMatch = RulesDiscovery.filterForAgent(allRules,
        "cat:work-execute", List.of());
      requireThat(exactMatch.size(), "exactMatch.size()").isEqualTo(1);

      // Should NOT match a different subagent type
      List<RuleFile> noMatch = RulesDiscovery.filterForAgent(allRules,
        "cat:work-prepare", List.of());
      requireThat(noMatch, "noMatch").isEmpty();

      // Should NOT match a different subagent type
      List<RuleFile> noMatchExplore = RulesDiscovery.filterForAgent(allRules,
        "Explore", List.of());
      requireThat(noMatchExplore, "noMatchExplore").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a YAML list item containing a comma inside quotes is parsed as a single item
   * (not split on the inner comma).
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void subAgentsQuotedValueWithCommaIsOneSingle() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-quote-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      // Value with a comma inside a quoted string should produce one item
      Files.writeString(rulesDir.resolve("quoted.md"), """
        ---
        agents: ["cat:work,execute"]
        ---
        # Quoted comma rule
        """);

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      List<String> subAgents = rules.getFirst().subAgents();
      requireThat(subAgents.size(), "subAgents.size()").isEqualTo(1);
      requireThat(subAgents.getFirst(), "subAgents[0]").isEqualTo("cat:work,execute");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Concern 4: splitYamlListItems quote edge cases ----

  /**
   * Verifies that a YAML list with an unclosed quote throws an exception.
   *
   * @throws IOException if file operations fail
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*misquote\\.md.*")
  public void malformedYamlThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-misquote-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      // ["unclosed — mismatched open-quote: invalid YAML
      Files.writeString(rulesDir.resolve("misquote.md"), """
        ---
        agents: ["unclosed]
        ---
        # Misquote rule
        """);

      new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code "a", "b"} (two adjacent quoted items) is split into two items {@code a} and
   * {@code b} with surrounding quotes stripped.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void splitYamlListItemsAdjacentQuotedItems() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-adjacent-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("adjacent.md"), """
        ---
        agents: ["a", "b"]
        ---
        # Adjacent items
        """);

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      List<String> subAgents = rules.getFirst().subAgents();
      requireThat(subAgents.size(), "subAgents.size()").isEqualTo(2);
      requireThat(subAgents.contains("a"), "containsA").isTrue();
      requireThat(subAgents.contains("b"), "containsB").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code "item1" , "item2"} (space before comma) is split into two items
   * {@code item1} and {@code item2}.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void splitYamlListItemsWhitespaceAroundComma() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-whitespace-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("whitespace.md"), """
        ---
        agents: ["item1" , "item2"]
        ---
        # Whitespace around comma
        """);

      List<RuleFile> rules = new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      requireThat(rules.size(), "rules.size()").isEqualTo(1);
      List<String> subAgents = rules.getFirst().subAgents();
      requireThat(subAgents.size(), "subAgents.size()").isEqualTo(2);
      requireThat(subAgents.contains("item1"), "containsItem1").isTrue();
      requireThat(subAgents.contains("item2"), "containsItem2").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Concern 2: getCatRulesForAudience() tests ----

  /**
   * Verifies that getCatRulesForAudience returns empty string when the rules directory does not exist.
   */
  @Test
  public void getCatRulesForAudienceEmptyWhenDir()
  {
    Path nonExistentDir = Path.of("/tmp/does-not-exist-getCatRules-12345/rules");
    String result = RulesDiscovery.getCatRulesForAudience(nonExistentDir, YAML_MAPPER,
      RulesDiscovery::filterForMainAgent, List.of());
    requireThat(result, "result").isEmpty();
  }

  /**
   * Verifies that getCatRulesForAudience returns empty string when rules exist but the filter
   * excludes all of them.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getCatRulesForAudienceEmptyWhenNoRules() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-nofilter-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      // Rule targets only subagents, so filterForMainAgent will exclude it
      Files.writeString(rulesDir.resolve("subagent-only.md"), """
        ---
        agents: ["subagents"]
        ---
        # Only for subagents
        """);

      String result = RulesDiscovery.getCatRulesForAudience(rulesDir, YAML_MAPPER,
        RulesDiscovery::filterForMainAgent, List.of());
      requireThat(result, "result").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCatRulesForAudience returns content for a main-agent rule when
   * using filterForMainAgent.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getCatRulesForAudienceReturnsContentFor() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-main-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("main-rule.md"), """
        ---
        agents: ["main"]
        ---
        # Main agent content
        Some important instruction.
        """);

      String result = RulesDiscovery.getCatRulesForAudience(rulesDir, YAML_MAPPER,
        RulesDiscovery::filterForMainAgent, List.of());
      requireThat(result, "result").contains("Main agent content");
      requireThat(result, "result").contains("Some important instruction.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the engine rule-loading helper expands {@code cat:include} directives before
   * filtering and rendering project-local rules.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getCatRulesForAudienceExpandsIncludes() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-helper-include-");
    try
    {
      Path rulesDir = tempDir.resolve(".cat/rules/common");
      Path fragmentsDir = rulesDir.resolve("fragments");
      Files.createDirectories(fragmentsDir);
      Files.writeString(fragmentsDir.resolve("shared.md"), """
        Shared rule fragment.
        """);
      Files.writeString(rulesDir.resolve("main-rule.md"), """
        ---
        # Main rule
        <!-- cat:include fragments/shared.md -->
        """);

      String result = RulesDiscovery.getCatRulesForAudience(rulesDir, YAML_MAPPER,
        RulesDiscovery::filterForMainAgent, List.of());
      requireThat(result, "result").contains("Main rule");
      requireThat(result, "result").contains("Shared rule fragment.");
      requireThat(result, "result").doesNotContain("cat:include");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that engine-specific rule stubs can include shared bodies from the sibling include
   * directory.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getCatRulesForAudienceExpandsEngine() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-engine-include-");
    try
    {
      Path rulesRoot = tempDir.resolve(".cat/rules");
      Path rulesDir = rulesRoot.resolve("claude");
      Path includeDir = rulesRoot.resolve("include");
      Files.createDirectories(rulesDir);
      Files.createDirectories(includeDir);
      Files.writeString(includeDir.resolve("java.md"), """
        Java rule body.
        """);
      Files.writeString(rulesDir.resolve("java.md"), """
        ---
        paths: ["*.java"]
        ---
        <!-- cat:include ../include/java.md -->
        """);

      String result = RulesDiscovery.getCatRulesForAudience(rulesDir, YAML_MAPPER,
        RulesDiscovery::filterForMainAgent, List.of("Example.java"));
      requireThat(result, "result").contains("Java rule body.");
      requireThat(result, "result").doesNotContain("cat:include");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCatRulesForAudience returns content for a rule targeting all subagents
   * when using filterForSubagent with any subagent type.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getCatRulesForAudienceReturnsContentFor2() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-subagent-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      // agents: ["subagents"] → null → matches all subagents
      Files.writeString(rulesDir.resolve("universal-rule.md"), """
        ---
        agents: ["subagents"]
        ---
        # Subagent universal content
        Applies to all subagents.
        """);

      String subagentType = "SomeSubagentType";
      String result = RulesDiscovery.getCatRulesForAudience(rulesDir, YAML_MAPPER,
        (rules, activeFiles) -> RulesDiscovery.filterForAgent(rules, subagentType, activeFiles),
        List.of());
      requireThat(result, "result").contains("Subagent universal content");
      requireThat(result, "result").contains("Applies to all subagents.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ---- Multi-directory getCatRulesForAudience(List<Path>) ----

  /**
   * Verifies that getCatRulesForAudience(List) merges rules from multiple directories.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getCatRulesForAudienceListMergesMultiple() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-multi-merge-");
    try
    {
      Path pluginRulesDir = tempDir.resolve("plugin/rules/common");
      Files.createDirectories(pluginRulesDir);
      Files.writeString(pluginRulesDir.resolve("plugin-rule.md"), """
        # Plugin rule
        From plugin.
        """);

      Path projectRulesDir = tempDir.resolve("rules");
      Files.createDirectories(projectRulesDir);
      Files.writeString(projectRulesDir.resolve("project-rule.md"), """
        # Project rule
        From project.
        """);

      String result = RulesDiscovery.getCatRulesForAudience(
        List.of(pluginRulesDir, projectRulesDir), YAML_MAPPER,
        RulesDiscovery::filterForMainAgent, List.of());
      requireThat(result, "result").contains("Plugin rule");
      requireThat(result, "result").contains("From plugin.");
      requireThat(result, "result").contains("Project rule");
      requireThat(result, "result").contains("From project.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCatRulesForAudience(List) includes rules from both directories when filenames
   * collide. Both rules are concatenated in order (first directory first, second directory second).
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getCatRulesForAudienceListBothRules() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-multi-override-");
    try
    {
      Path dir1 = tempDir.resolve("dir1");
      Files.createDirectories(dir1);
      Files.writeString(dir1.resolve("shared.md"), """
        # Dir1 version
        Content from dir1.
        """);

      Path dir2 = tempDir.resolve("dir2");
      Files.createDirectories(dir2);
      Files.writeString(dir2.resolve("shared.md"), """
        # Dir2 version
        Content from dir2.
        """);

      String result = RulesDiscovery.getCatRulesForAudience(
        List.of(dir1, dir2), YAML_MAPPER,
        RulesDiscovery::filterForMainAgent, List.of());
      // Both rules are included (no deduplication)
      requireThat(result, "result").contains("Dir1 version");
      requireThat(result, "result").contains("Content from dir1.");
      requireThat(result, "result").contains("Dir2 version");
      requireThat(result, "result").contains("Content from dir2.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getCatRulesForAudience(List) returns empty when all directories are missing.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getCatRulesForAudienceListReturnEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-multi-empty-");
    try
    {
      Path missingDir1 = tempDir.resolve("does-not-exist-1");
      Path missingDir2 = tempDir.resolve("does-not-exist-2");

      String result = RulesDiscovery.getCatRulesForAudience(
        List.of(missingDir1, missingDir2), YAML_MAPPER,
        RulesDiscovery::filterForMainAgent, List.of());
      requireThat(result, "result").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  private static void assertInvalidAgents(String frontmatter, String expectedMessage) throws IOException
  {
    Path tempDir = Files.createTempDirectory("rules-test-invalid-agents-");
    try
    {
      Path rulesDir = tempDir.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("invalid.md"), "---\n" + frontmatter + "\n---\n# Rule\n");

      try
      {
        new RulesDiscovery(rulesDir, YAML_MAPPER).discoverAll();
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains(expectedMessage);
        return;
      }
      throw new AssertionError("Expected invalid agents rejection for " + frontmatter);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  private static Path findRepositoryRoot()
  {
    Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (candidate != null)
    {
      if (Files.isDirectory(candidate.resolve("client/plugin/rules")))
        return candidate;
      candidate = candidate.getParent();
    }
    throw new IllegalStateException("Could not locate repository root from " + System.getProperty("user.dir"));
  }
}
