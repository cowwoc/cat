/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.codex.hook.CodexHookScope;
import io.github.cowwoc.cat.codex.hook.SessionStartHook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
  public void sessionStartGeneratesCodexStubsForPlugin() throws IOException
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

      SessionStartHook.HookResult result = run(projectRoot, pluginRoot, pluginData);
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
   * Verifies that the registered no-argument Codex launcher shape consumes native Codex input.
   *
   * @throws Exception if the hook cannot be invoked
   */
  @Test
  public void sessionStartParsesNativeCodexInput() throws Exception
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/common/java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java Common
        """, StandardCharsets.UTF_8);
      String nativeInput = """
        {
          "cwd": "%s",
          "hook_event_name": "SessionStart"
        }
        """.formatted(fixture.projectRoot().toString().replace("\\", "\\\\"));
      Map<String, String> environment = Map.of(
        "CAT_PLUGIN_ROOT", fixture.pluginRoot().toString(),
        "CAT_PLUGIN_DATA", fixture.pluginData().toString(),
        "TZ", "UTC");

      SessionStartHook.HookResult result = runNative(nativeInput, environment);

      requireThat(result.output(), "output").contains("\"hookSpecificOutput\"");
      requireThat(result.output(), "output").contains("Java Common");
      requireThat(fixture.projectRoot().resolve(".cat/rules/codex/java.md"), "projectStub").
        isRegularFile();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex SessionStart detects agent input and applies {@code subAgents} frontmatter instead of
   * main-agent filtering.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void subagentSessionStartInjectsSubagentRules() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.writeString(fixture.pluginRoot().resolve("rules/common/shared.md"), "shared agent rule",
        StandardCharsets.UTF_8);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/main-only.md"), """
        ---
        subAgents: []
        ---
        main-agent-only rule
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/targeted.md"), """
        ---
        subAgents: ["cat:work-execute"]
        ---
        targeted work-execute rule
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/codex/agent-only.md"), """
        ---
        mainAgent: false
        ---
        codex agent-only rule
        """, StandardCharsets.UTF_8);
      String nativeInput = """
        {
          "cwd": "%s",
          "thread_source": "subagent",
          "agent_type": "cat:work-execute"
        }
        """.formatted(fixture.projectRoot().toString().replace("\\", "\\\\"));
      Map<String, String> environment = Map.of(
        "CAT_PLUGIN_ROOT", fixture.pluginRoot().toString(),
        "CAT_PLUGIN_DATA", fixture.pluginData().toString(),
        "TZ", "UTC");

      SessionStartHook.HookResult result = runNative(nativeInput, environment);

      requireThat(result.output(), "output").contains("shared agent rule");
      requireThat(result.output(), "output").contains("targeted work-execute rule");
      requireThat(result.output(), "output").contains("codex agent-only rule");
      requireThat(result.output(), "output").doesNotContain("main-agent-only rule");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex SessionStart fails fast when agent input omits the top-level agent identity.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void subagentSessionStartRequiresIdentity() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/targeted.md"), """
        ---
        subAgents: ["cat:work-execute"]
        ---
        targeted nested-role rule
        """, StandardCharsets.UTF_8);
      String nativeInput = """
        {
          "cwd": "%s",
          "source": {
            "subagent": {
              "thread_spawn": {}
            }
          }
        }
        """.formatted(fixture.projectRoot().toString().replace("\\", "\\\\"));
      Map<String, String> environment = Map.of(
        "CAT_PLUGIN_ROOT", fixture.pluginRoot().toString(),
        "CAT_PLUGIN_DATA", fixture.pluginData().toString(),
        "TZ", "UTC");

      SessionStartHook.HookResult result = runNative(nativeInput, environment);

      requireThat(result.output(), "output").contains("SessionStart Handler Errors");
      requireThat(result.output(), "output").contains("Codex agent SessionStart payload is missing top-level " +
        "agent_type");
      requireThat(result.output(), "output").doesNotContain("targeted nested-role rule");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex SessionStart accepts the native 0.134.0 {@code agent_type} field for
   * subagent-scoped rule injection.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void subagentSessionStartAcceptsAgentType() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/targeted.md"), """
        ---
        subAgents: ["cat:work-execute"]
        ---
        targeted agent-type rule
        """, StandardCharsets.UTF_8);
      String nativeInput = """
        {
          "cwd": "%s",
          "hook_event_name": "SessionStart",
          "agent_id": "agent-1",
          "agent_type": "cat:work-execute"
        }
        """.formatted(fixture.projectRoot().toString().replace("\\", "\\\\"));
      Map<String, String> environment = Map.of(
        "CAT_PLUGIN_ROOT", fixture.pluginRoot().toString(),
        "CAT_PLUGIN_DATA", fixture.pluginData().toString(),
        "TZ", "UTC");

      SessionStartHook.HookResult result = runNative(nativeInput, environment);

      requireThat(result.output(), "output").contains("targeted agent-type rule");
      requireThat(result.output(), "output").doesNotContain("SessionStart Handler Errors");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that nested-session detection via source.subagent succeeds when top-level agent_type
   * is present.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sourceSubagentSessionStartWithAgentType() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/targeted.md"), """
        ---
        subAgents: ["cat:work-execute"]
        ---
        targeted nested-role rule
        """, StandardCharsets.UTF_8);
      String nativeInput = """
        {
          "cwd": "%s",
          "agent_type": "cat:work-execute",
          "source": {
            "subagent": {
              "thread_spawn": {
                "agent_type": "cat:work-execute"
              }
            }
          }
        }
        """.formatted(fixture.projectRoot().toString().replace("\\", "\\\\"));
      Map<String, String> environment = Map.of(
        "CAT_PLUGIN_ROOT", fixture.pluginRoot().toString(),
        "CAT_PLUGIN_DATA", fixture.pluginData().toString(),
        "TZ", "UTC");

      SessionStartHook.HookResult result = runNative(nativeInput, environment);

      requireThat(result.output(), "output").contains("targeted nested-role rule");
      requireThat(result.output(), "output").doesNotContain("SessionStart Handler Errors");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that authored path-scoped Codex rules are not eagerly injected at SessionStart.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void pathScopedRulesAreNotEager() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/main-eager.md"),
        "main eager rule", StandardCharsets.UTF_8);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/main-path-scoped.md"), """
        ---
        paths: ["*.java"]
        ---
        main path-scoped rule
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/agent-eager.md"), """
        ---
        mainAgent: false
        subAgents: ["cat:work-execute"]
        ---
        agent eager rule
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/agent-path-scoped.md"), """
        ---
        mainAgent: false
        subAgents: ["cat:work-execute"]
        paths: ["*.java"]
        ---
        agent path-scoped rule
        """, StandardCharsets.UTF_8);
      Map<String, String> environment = Map.of(
        "CAT_PLUGIN_ROOT", fixture.pluginRoot().toString(),
        "CAT_PLUGIN_DATA", fixture.pluginData().toString(),
        "TZ", "UTC");
      String mainInput = """
        {
          "cwd": "%s",
          "hook_event_name": "SessionStart"
        }
        """.formatted(fixture.projectRoot().toString().replace("\\", "\\\\"));
      String subagentInput = """
        {
          "cwd": "%s",
          "thread_source": "subagent",
          "agent_type": "cat:work-execute"
        }
        """.formatted(fixture.projectRoot().toString().replace("\\", "\\\\"));

      SessionStartHook.HookResult mainResult = runNative(mainInput, environment);
      SessionStartHook.HookResult subagentResult = runNative(subagentInput, environment);

      requireThat(mainResult.output(), "mainOutput").contains("main eager rule");
      requireThat(mainResult.output(), "mainOutput").doesNotContain("main path-scoped rule");
      requireThat(subagentResult.output(), "subagentOutput").contains("agent eager rule");
      requireThat(subagentResult.output(), "subagentOutput").doesNotContain("agent path-scoped rule");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a blank native payload falls back to environment and working-directory hints.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartFallsBackWhenNativeInputIs() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/common/java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java Common
        """, StandardCharsets.UTF_8);
      Map<String, String> environment = Map.of(
        "CAT_PROJECT_DIR", fixture.projectRoot().toString(),
        "CAT_PLUGIN_ROOT", fixture.pluginRoot().toString(),
        "CAT_PLUGIN_DATA", fixture.pluginData().toString(),
        "TZ", "UTC");

      SessionStartHook.HookResult result = runNative("", environment, tempDir);

      requireThat(result.output(), "output").contains("\"hookSpecificOutput\"");
      requireThat(result.output(), "output").contains("Java Common");
      requireThat(fixture.projectRoot().resolve(".cat/rules/codex/java.md"), "projectStub").
        isRegularFile();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that blank native input can use the process working directory as the project root.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartFallsBackToWorkingDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/common/java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java Common
        """, StandardCharsets.UTF_8);
      Map<String, String> environment = Map.of(
        "CAT_PLUGIN_ROOT", fixture.pluginRoot().toString(),
        "CAT_PLUGIN_DATA", fixture.pluginData().toString(),
        "TZ", "UTC");

      SessionStartHook.HookResult result = runNative("", environment, fixture.projectRoot());

      requireThat(result.output(), "output").contains("\"hookSpecificOutput\"");
      requireThat(result.output(), "output").contains("Java Common");
      requireThat(fixture.projectRoot().resolve(".cat/rules/codex/java.md"), "projectStub").
        isRegularFile();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that native plugin data takes precedence over a conflicting environment value.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartNativePluginDataOverrides() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/common/java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java Common
        """, StandardCharsets.UTF_8);
      String nativeInput = """
        {
          "cwd": "%s",
          "plugin_root": "%s",
          "plugin_data": "%s"
        }
        """.formatted(fixture.projectRoot().toString().replace("\\", "\\\\"),
        fixture.pluginRoot().toString().replace("\\", "\\\\"),
        fixture.pluginData().toString().replace("\\", "\\\\"));
      Map<String, String> environment = Map.of(
        "CAT_PLUGIN_DATA", "invalid\u0000environment-plugin-data",
        "TZ", "UTC");

      SessionStartHook.HookResult result = runNative(nativeInput, environment, tempDir);

      requireThat(result.output(), "output").contains("\"hookSpecificOutput\"");
      requireThat(result.output(), "output").contains("Java Common");
      requireThat(fixture.projectRoot().resolve(".cat/rules/codex/java.md"), "projectStub").
        isRegularFile();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the native Codex SessionStart entrypoint rejects unexpected launcher arguments.
   *
   * @throws IOException if file operations fail
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unexpected arguments.*")
  public void sessionStartRejectsUnexpectedLauncher() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      new SessionStartHook().runFromSystem(new String[]{"unexpected"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart can discover plugin paths when Codex path variables are absent.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartDiscoversPathsWhen() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path projectRoot = repoRoot.resolve("project");
      Path pluginRoot = repoRoot.resolve("plugin");
      Path codexHome = tempDir.resolve("codex-home");
      Path pluginData = codexHome.resolve("plugins/data/cat-cat");
      Files.createDirectories(projectRoot.resolve(".cat/rules/common"));
      Files.createDirectories(projectRoot.resolve(".cat/rules/codex"));
      Files.createDirectories(pluginRoot.resolve(".codex-plugin"));
      Files.createDirectories(pluginRoot.resolve("rules/common"));
      Files.createDirectories(pluginRoot.resolve("rules/codex"));
      Files.createDirectories(pluginData);
      Files.writeString(pluginRoot.resolve(".codex-plugin/plugin.json"), "{\"version\":\"2.1\"}\n",
        StandardCharsets.UTF_8);
      Files.writeString(projectRoot.resolve(".cat/rules/common/java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java Common
        """, StandardCharsets.UTF_8);
      String nativeInput = """
        {
          "cwd": "%s",
          "hook_event_name": "SessionStart"
        }
        """.formatted(projectRoot.toString().replace("\\", "\\\\"));

      SessionStartHook.HookResult result = runNative(nativeInput,
        Map.of("CODEX_HOME", codexHome.toString(), "TZ", "UTC"), repoRoot);

      requireThat(result.output(), "output").contains("\"hookSpecificOutput\"");
      requireThat(result.output(), "output").contains("Java Common");
      requireThat(projectRoot.resolve(".cat/rules/codex/java.md"), "projectStub").isRegularFile();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that native Codex payload paths take precedence over conflicting environment paths.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartNativePayloadPathsOverride() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Path environmentProjectRoot = tempDir.resolve("environment-project");
      Path environmentPluginRoot = tempDir.resolve("environment-plugin");
      Path environmentPluginData = tempDir.resolve("environment-plugin-data");
      Files.createDirectories(environmentProjectRoot.resolve(".cat/rules/common"));
      Files.createDirectories(environmentProjectRoot.resolve(".cat/rules/codex"));
      Files.createDirectories(environmentPluginRoot.resolve(".codex-plugin"));
      Files.createDirectories(environmentPluginRoot.resolve("rules/common"));
      Files.createDirectories(environmentPluginRoot.resolve("rules/codex"));
      Files.createDirectories(environmentPluginData);
      Files.writeString(environmentPluginRoot.resolve(".codex-plugin/plugin.json"), "{\"version\":\"2.1\"}\n",
        StandardCharsets.UTF_8);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/common/native-project.md"), """
        ---
        paths: ["*.native-project"]
        ---
        # Native Project Rule
        """, StandardCharsets.UTF_8);
      Files.writeString(environmentProjectRoot.resolve(".cat/rules/common/environment-project.md"), """
        ---
        paths: ["*.environment-project"]
        ---
        # Environment Project Rule
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.pluginRoot().resolve("rules/common/native-plugin.md"), """
        ---
        paths: ["*.native-plugin"]
        ---
        # Native Plugin Rule
        """, StandardCharsets.UTF_8);
      Files.writeString(environmentPluginRoot.resolve("rules/common/environment-plugin.md"), """
        ---
        paths: ["*.environment-plugin"]
        ---
        # Environment Plugin Rule
        """, StandardCharsets.UTF_8);
      String nativeInput = """
        {
          "cwd": "%s",
          "plugin_root": "%s"
        }
        """.formatted(fixture.projectRoot().toString().replace("\\", "\\\\"),
        fixture.pluginRoot().toString().replace("\\", "\\\\"));
      Map<String, String> environment = Map.of(
        "CAT_PROJECT_DIR", environmentProjectRoot.toString(),
        "CAT_PLUGIN_ROOT", environmentPluginRoot.toString(),
        "CAT_PLUGIN_DATA", environmentPluginData.toString(),
        "TZ", "UTC");

      SessionStartHook.HookResult result = runNative(nativeInput, environment, tempDir);

      requireThat(result.output(), "output").contains("Native Project Rule");
      requireThat(result.output(), "output").contains("Native Plugin Rule");
      requireThat(result.output(), "output").doesNotContain("Environment Project Rule");
      requireThat(result.output(), "output").doesNotContain("Environment Plugin Rule");
      requireThat(fixture.projectRoot().resolve(".cat/rules/codex/native-project.md"),
        "nativeProjectStub").isRegularFile();
      requireThat(fixture.pluginRoot().resolve("rules/codex/native-plugin.md"),
        "nativePluginStub").isRegularFile();
      requireThat(Files.exists(environmentProjectRoot.resolve(".cat/rules/codex/environment-project.md")),
        "environmentProjectStub").isFalse();
      requireThat(Files.exists(environmentPluginRoot.resolve("rules/codex/environment-plugin.md")),
        "environmentPluginStub").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart fails when no plugin root hint is present and discovery fails.
   *
   * @throws IOException if file operations fail
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*CAT_PLUGIN_ROOT is required.*")
  public void sessionStartFailsWhenPluginRootCannotBe() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Path projectRoot = tempDir.resolve("project");
      Path pluginData = tempDir.resolve("plugin-data");
      Files.createDirectories(projectRoot.resolve(".cat/rules/common"));
      Files.createDirectories(projectRoot.resolve(".cat/rules/codex"));
      Files.createDirectories(pluginData);
      String nativeInput = """
        {
          "cwd": "%s"
        }
        """.formatted(projectRoot.toString().replace("\\", "\\\\"));

      runNative(nativeInput, Map.of("CAT_PLUGIN_DATA", pluginData.toString(), "TZ", "UTC"),
        projectRoot);
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

      run(fixture);

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
  public void sessionStartPrunesPluginStubsFrom()
    throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Path pluginRoot = fixture.pluginRoot();
      Path commonRule = pluginRoot.resolve("rules/common/java.md");
      Files.writeString(commonRule, """
        ---
        paths: ["*.java"]
        ---
        # Java Common
        """, StandardCharsets.UTF_8);

      run(fixture);
      Path stub = pluginRoot.resolve("rules/codex/java.md");
      Path manifest = pluginRoot.resolve("rules/codex").resolve(GENERATED_STUB_MANIFEST);
      requireThat(stub, "stub").isRegularFile();
      requireThat(manifest, "manifest").isRegularFile();
      requireThat(Files.readString(manifest, StandardCharsets.UTF_8), "manifest.content").isEqualTo(
        "java.md\n");
      Files.delete(commonRule);
      Thread.sleep(2_100);

      run(fixture);

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
  public void sessionStartFallsBackWhenGeneratedStub() throws IOException
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

      run(fixture);

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
  public void sessionStartPrunesManagedStubsWhenCommon() throws IOException
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

      run(fixture);

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
  public void sessionStartEscapesGeneratedStubPath() throws IOException
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

      run(fixture);

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
  public void sessionStartPreservesAuthoredCodexRuleAt() throws IOException
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

      run(fixture);

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
  public void sessionStartRejectsSymlinkedCodexRule() throws IOException
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

      SessionStartHook.HookResult result = run(fixture);

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
  public void sessionStartRejectsSymlinkedCodexRule2() throws IOException
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

      SessionStartHook.HookResult result = run(fixture);

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
    return new Fixture(projectRoot, pluginRoot, pluginData);
  }

  /**
   * Runs the Codex SessionStart hook using a test-specific scope.
   *
   * @param fixture the SessionStart fixture
   * @return the hook result
   */
  private static SessionStartHook.HookResult run(Fixture fixture)
  {
    return run(fixture.projectRoot(), fixture.pluginRoot(), fixture.pluginData());
  }

  /**
   * Runs the Codex SessionStart hook from native input using a production scope.
   *
   * @param nativeInput the native Codex hook payload
   * @param environment the process environment
   * @return the hook result
   */
  private static SessionStartHook.HookResult runNative(String nativeInput,
    Map<String, String> environment)
  {
    return runNative(nativeInput, environment, Path.of(System.getProperty("user.dir")));
  }

  /**
   * Runs the Codex SessionStart hook from native input using a production scope.
   *
   * @param nativeInput the native Codex hook payload
   * @param environment the process environment
   * @param workingDirectory the process working directory
   * @return the hook result
   */
  private static SessionStartHook.HookResult runNative(String nativeInput,
    Map<String, String> environment, Path workingDirectory)
  {
    SessionStartHook hook = new SessionStartHook();
    try (CodexHookScope scope = hook.createScope(
      new ByteArrayInputStream(nativeInput.getBytes(StandardCharsets.UTF_8)), environment,
      workingDirectory))
    {
      return hook.run(scope);
    }
  }

  /**
   * Runs the Codex SessionStart hook using a test-specific scope.
   *
   * @param projectRoot the project root directory
   * @param pluginRoot the plugin root directory
   * @param pluginData the plugin data directory
   * @return the hook result
   */
  private static SessionStartHook.HookResult run(Path projectRoot, Path pluginRoot, Path pluginData)
  {
    try (TestCodexHook scope = new TestCodexHook(projectRoot, pluginRoot, pluginData))
    {
      return new SessionStartHook().run(scope);
    }
  }

  private record Fixture(Path projectRoot, Path pluginRoot, Path pluginData)
  {
  }
}
