/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.codex.hook.SessionStartHook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.annotations.Test;

/**
 * Tests for the Codex SessionStart hook.
 */
public final class CodexSessionStartHookTest
{
  private static final String GENERATED_STUB_MARKER = "<!-- cat:generated-codex-rule-stub -->";
  private static final String GENERATED_STUB_MANIFEST = ".cat-generated-stubs";

  /**
   * Verifies that SessionStart generates Codex stubs for path-scoped common rules from both the
   * installed plugin and the end-user project.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartGeneratesCodexStubsForPluginAndProjectCommonRules() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Path projectRoot = tempDir.resolve("project");
      Path pluginData = tempDir.resolve("plugin-data");
      Path codexHome = tempDir.resolve("codex-home");
      Path pluginRoot = codexHome.resolve("plugins/cache/marketplace/cat/2.1");
      Files.createDirectories(pluginRoot.resolve(".codex-plugin"));
      Files.createDirectories(pluginRoot.resolve("rules/common"));
      Files.createDirectories(pluginRoot.resolve("rules/codex"));
      Files.createDirectories(projectRoot.resolve(".cat/rules/common"));
      Files.createDirectories(projectRoot.resolve(".cat/rules/codex"));
      Files.createDirectories(pluginData);
      Files.writeString(pluginRoot.resolve(".codex-plugin/plugin.json"), "{\"version\":\"2.1\"}\n",
        StandardCharsets.UTF_8);
      Files.writeString(pluginRoot.resolve("rules/common/java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java Conventions

        Use Allman braces.
        """, StandardCharsets.UTF_8);
      Files.writeString(projectRoot.resolve(".cat/rules/common/sql.md"), """
        ---
        paths: ["*.sql"]
        ---
        # SQL Conventions

        Use upper-case keywords.
        """, StandardCharsets.UTF_8);

      SessionStartHook.HookResult result = SessionStartHook.run(new String[]{
        projectRoot.toString(), pluginData.toString(), codexHome.toString(),
        "marketplace", "cat", "2.1", "UTC"});
      requireThat(result.output(), "output").contains("\"hookSpecificOutput\"");
      requireThat(result.warnings(), "warnings").isEmpty();

      Path pluginStub = pluginRoot.resolve("rules/codex/java.md");
      requireThat(pluginStub, "pluginStub").isRegularFile();
      requireThat(Files.readString(pluginStub, StandardCharsets.UTF_8), "pluginStubContent").isEqualTo("""
        <!-- cat:generated-codex-rule-stub -->
        # Java Conventions

        `paths` = ["*.java"]
        `include` = `../common/java.md`

        Apply `rules/codex/rule-loading.md`.
        """);
      requireThat(Files.readString(pluginRoot.resolve("rules/codex").resolve(GENERATED_STUB_MANIFEST),
        StandardCharsets.UTF_8), "pluginManifest").isEqualTo("java.md\n");
      Path projectStub = projectRoot.resolve(".cat/rules/codex/sql.md");
      requireThat(projectStub, "projectStub").isRegularFile();
      requireThat(Files.readString(projectStub, StandardCharsets.UTF_8), "projectStubContent").isEqualTo("""
        <!-- cat:generated-codex-rule-stub -->
        # SQL Conventions

        `paths` = ["*.sql"]
        `include` = `../common/sql.md`

        Apply `rules/codex/rule-loading.md`.
        """);
      requireThat(Files.readString(projectRoot.resolve(".cat/rules/codex").resolve(
        GENERATED_STUB_MANIFEST), StandardCharsets.UTF_8), "projectManifest").isEqualTo("sql.md\n");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart removes managed stubs whose source common rule no longer exists.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartPrunesStaleManagedCodexStubs() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Path staleStub = fixture.projectRoot().resolve(".cat/rules/codex/stale.md");
      Files.writeString(staleStub, GENERATED_STUB_MARKER + "\n# Stale\n",
        StandardCharsets.UTF_8);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/codex").resolve(GENERATED_STUB_MANIFEST),
        "stale.md\n", StandardCharsets.UTF_8);

      SessionStartHook.run(fixture.args());

      requireThat(Files.exists(staleStub), "staleStub.exists").isFalse();
      requireThat(Files.exists(fixture.projectRoot().resolve(".cat/rules/codex").resolve(
        GENERATED_STUB_MANIFEST)), "manifest.exists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart uses the generated-stub manifest to prune plugin stubs after a
   * source rule is removed.
   *
   * @throws IOException if file operations fail
   * @throws InterruptedException if interrupted while waiting for cache expiration
   */
  @Test
  public void sessionStartPrunesPluginStubsFromManifestAfterSourceRemoval()
    throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Path pluginRoot = fixture.codexHome().resolve("plugins/cache/marketplace/cat/2.1");
      Path commonRule = pluginRoot.resolve("rules/common/java.md");
      Files.writeString(commonRule, """
        ---
        paths: ["*.java"]
        ---
        # Java Common
        """, StandardCharsets.UTF_8);

      SessionStartHook.run(fixture.args());
      Path stub = pluginRoot.resolve("rules/codex/java.md");
      Path manifest = pluginRoot.resolve("rules/codex").resolve(GENERATED_STUB_MANIFEST);
      requireThat(stub, "stub").isRegularFile();
      requireThat(manifest, "manifest").isRegularFile();
      requireThat(Files.readString(manifest, StandardCharsets.UTF_8), "manifest.content").isEqualTo(
        "java.md\n");
      Files.delete(commonRule);
      Thread.sleep(2_100);

      SessionStartHook.run(fixture.args());

      requireThat(Files.exists(stub), "stub.exists").isFalse();
      requireThat(Files.exists(manifest), "manifest.exists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart falls back to scanning generated stubs when the manifest contains an
   * invalid path.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartFallsBackWhenGeneratedStubManifestIsInvalid() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Path codexRules = fixture.projectRoot().resolve(".cat/rules/codex");
      Path staleStub = codexRules.resolve("stale.md");
      Files.writeString(staleStub, GENERATED_STUB_MARKER + "\n# Stale\n", StandardCharsets.UTF_8);
      Files.writeString(codexRules.resolve(GENERATED_STUB_MANIFEST), "../outside.md\n",
        StandardCharsets.UTF_8);

      SessionStartHook.run(fixture.args());

      requireThat(Files.exists(staleStub), "staleStub.exists").isFalse();
      requireThat(Files.exists(codexRules.resolve(GENERATED_STUB_MANIFEST)), "manifest.exists").
        isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart prunes managed stubs when a common-rule directory is removed.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartPrunesManagedStubsWhenCommonRuleDirectoryIsMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Path commonRules = fixture.projectRoot().resolve(".cat/rules/common");
      TestUtils.deleteDirectoryRecursively(commonRules);
      Path codexRules = fixture.projectRoot().resolve(".cat/rules/codex");
      Path staleStub = codexRules.resolve("stale.md");
      Files.writeString(staleStub, GENERATED_STUB_MARKER + "\n# Stale\n", StandardCharsets.UTF_8);
      Files.writeString(codexRules.resolve(GENERATED_STUB_MANIFEST), "stale.md\n",
        StandardCharsets.UTF_8);

      SessionStartHook.run(fixture.args());

      requireThat(Files.exists(staleStub), "staleStub.exists").isFalse();
      requireThat(Files.exists(codexRules.resolve(GENERATED_STUB_MANIFEST)), "manifest.exists").
        isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generated stubs escape path globs as JSON strings.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartEscapesGeneratedStubPathGlobs() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Path commonRule = fixture.projectRoot().resolve(".cat/rules/common/escaped.md");
      Files.writeString(commonRule, """
        ---
        paths:
          - "quote\\"glob"
          - "slash\\\\glob"
          - "tab\\tglob"
          - "newline\\nglob"
          - "return\\rglob"
          - "backspace\\bglob"
          - "formfeed\\fglob"
          - "control\\u0001glob"
        ---
        # Escaped Paths
        """, StandardCharsets.UTF_8);

      SessionStartHook.run(fixture.args());

      String stub = Files.readString(fixture.projectRoot().resolve(".cat/rules/codex/escaped.md"),
        StandardCharsets.UTF_8);
      requireThat(stub, "stub").contains(
        "`paths` = [\"quote\\\"glob\", \"slash\\\\glob\", \"tab\\tglob\", \"newline\\nglob\", " +
          "\"return\\rglob\", \"backspace\\bglob\", \"formfeed\\fglob\", " +
          "\"control\\u0001glob\"]");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart does not overwrite an authored Codex rule that happens to share a
   * relative path with a generated stub.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartPreservesAuthoredCodexRuleAtGeneratedPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Path commonRule = fixture.projectRoot().resolve(".cat/rules/common/java.md");
      Files.writeString(commonRule, """
        ---
        paths: ["*.java"]
        ---
        # Java Common
        """, StandardCharsets.UTF_8);
      Path authoredRule = fixture.projectRoot().resolve(".cat/rules/codex/java.md");
      String authoredContent = "# Authored Codex Rule\n\nKeep this file.\n";
      Files.writeString(authoredRule, authoredContent, StandardCharsets.UTF_8);

      SessionStartHook.run(fixture.args());

      requireThat(Files.readString(authoredRule, StandardCharsets.UTF_8), "authoredRule").
        isEqualTo(authoredContent);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart refuses to write generated stubs through a symlinked Codex rule
   * directory.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartRejectsSymlinkedCodexRuleDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir, false);
      Path commonRules = fixture.projectRoot().resolve(".cat/rules/common");
      Files.createDirectories(commonRules);
      Files.writeString(commonRules.resolve("java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java Common
        """, StandardCharsets.UTF_8);
      Path outside = tempDir.resolve("outside-codex-rules");
      Files.createDirectories(outside);
      Path rulesRoot = fixture.projectRoot().resolve(".cat/rules");
      Files.createDirectories(rulesRoot);
      Files.createSymbolicLink(rulesRoot.resolve("codex"), outside);

      SessionStartHook.HookResult result = SessionStartHook.run(fixture.args());

      requireThat(String.join("\n", result.warnings()), "warnings").contains("symbolic link");
      requireThat(result.output(), "output").contains("SessionStart Handler Errors");
      requireThat(Files.exists(outside.resolve("java.md")), "outsideStub.exists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart refuses to write generated stubs through a symlinked target file.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartRejectsSymlinkedCodexRuleFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Path commonRule = fixture.projectRoot().resolve(".cat/rules/common/java.md");
      Files.writeString(commonRule, """
        ---
        paths: ["*.java"]
        ---
        # Java Common
        """, StandardCharsets.UTF_8);
      Path outside = tempDir.resolve("outside-java.md");
      Files.writeString(outside, "outside\n", StandardCharsets.UTF_8);
      Files.createSymbolicLink(fixture.projectRoot().resolve(".cat/rules/codex/java.md"), outside);

      SessionStartHook.HookResult result = SessionStartHook.run(fixture.args());

      requireThat(String.join("\n", result.warnings()), "warnings").contains("symbolic link");
      requireThat(Files.readString(outside, StandardCharsets.UTF_8), "outside").isEqualTo("outside\n");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Creates a minimal Codex SessionStart test fixture.
   *
   * @param tempDir the test root directory
   * @return the fixture
   * @throws IOException if file operations fail
   */
  private static Fixture createFixture(Path tempDir) throws IOException
  {
    return createFixture(tempDir, true);
  }

  /**
   * Creates a minimal Codex SessionStart test fixture.
   *
   * @param tempDir the test root directory
   * @param createProjectCodexRules true if the project Codex rule directory should be created
   * @return the fixture
   * @throws IOException if file operations fail
   */
  private static Fixture createFixture(Path tempDir, boolean createProjectCodexRules) throws IOException
  {
    Path projectRoot = tempDir.resolve("project");
    Path pluginData = tempDir.resolve("plugin-data");
    Path codexHome = tempDir.resolve("codex-home");
    Path pluginRoot = codexHome.resolve("plugins/cache/marketplace/cat/2.1");
    Files.createDirectories(pluginRoot.resolve(".codex-plugin"));
    Files.createDirectories(pluginRoot.resolve("rules/common"));
    Files.createDirectories(pluginRoot.resolve("rules/codex"));
    Files.createDirectories(projectRoot.resolve(".cat/rules/common"));
    if (createProjectCodexRules)
      Files.createDirectories(projectRoot.resolve(".cat/rules/codex"));
    Files.createDirectories(pluginData);
    Files.writeString(pluginRoot.resolve(".codex-plugin/plugin.json"), "{\"version\":\"2.1\"}\n",
      StandardCharsets.UTF_8);
    return new Fixture(projectRoot, pluginData, codexHome);
  }

  private record Fixture(Path projectRoot, Path pluginData, Path codexHome)
  {
    /**
     * Returns SessionStart command-line arguments for this fixture.
     *
     * @return SessionStart arguments
     */
    private String[] args()
    {
      return new String[]{projectRoot.toString(), pluginData.toString(), codexHome.toString(),
        "marketplace", "cat", "2.1", "UTC"};
    }
  }
}
