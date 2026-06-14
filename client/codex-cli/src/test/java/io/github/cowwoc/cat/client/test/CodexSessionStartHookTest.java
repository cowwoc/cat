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
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests for the Codex SessionStart hook.
 */
public final class CodexSessionStartHookTest
{
  private static final Path GENERATED_BODY_ROOT = Path.of("generated/codex-rule-bodies");
  private static final String GENERATED_BODY_MANIFEST = "manifest.json";

  /**
   * Verifies that SessionStart generates plugin-data bodies and injects lazy-loading stubs for
   * path-scoped rules from both the installed plugin and the end-user project.
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
      requireThat(result.output(), "output").contains("# Java Conventions");
      requireThat(result.output(), "output").contains("# SQL Conventions");
      requireThat(result.warnings(), "warnings").isEmpty();

      Path pluginStub = pluginRoot.resolve("rules/codex/java.md");
      requireThat(Files.exists(pluginStub), "pluginStub.exists").isFalse();
      Path pluginBody = generatedBody(pluginData, "plugin", "rules/common/java.md");
      requireThat(Files.readString(pluginBody,
        StandardCharsets.UTF_8), "pluginBody").isEqualTo("""
        # Java Conventions

        Use Allman braces.
        """);
      Path projectStub = projectRoot.resolve(".cat/rules/codex/sql.md");
      requireThat(Files.exists(projectStub), "projectStub.exists").isFalse();
      Path projectBody = generatedBody(pluginData, "project", ".cat/rules/common/sql.md");
      requireThat(Files.readString(projectBody,
        StandardCharsets.UTF_8), "projectBody").isEqualTo("""
        # SQL Conventions

        Use upper-case keywords.
        """);
      String manifest = Files.readString(generatedManifest(pluginData), StandardCharsets.UTF_8);
      requireThat(manifest, "manifest").contains("\"contextPath\" : \"rules/common/java.md\"");
      requireThat(manifest, "manifest").contains("\"contextPath\" : \".cat/rules/common/sql.md\"");
      requireThat(manifest, "manifest").contains(pluginBody.toString());
      requireThat(manifest, "manifest").contains(projectBody.toString());
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
      requireThat(generatedBody(fixture.pluginData(), "project", ".cat/rules/common/java.md"),
        "projectBody").isRegularFile();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex SessionStart detects agent input and applies {@code agents} frontmatter instead of
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
        agents: ["main"]
        ---
        main-agent-only rule
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/targeted.md"), """
        ---
        agents: ["main", "cat:work-execute"]
        ---
        targeted work-execute rule
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/codex/agent-only.md"), """
        ---
        agents: ["subagents"]
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
   * Verifies that subagents reuse path-scoped body files generated by the main agent.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void subagentReusesMainBodies() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Path agentRule = fixture.pluginRoot().resolve("rules/codex/agent-path.md");
      Files.writeString(agentRule, """
        ---
        agents: ["cat:work-execute"]
        paths: ["*.java"]
        ---
        # Agent Path Rule

        Follow generated body.
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
      Path body = generatedBody(fixture.pluginData(), "plugin", "rules/codex/agent-path.md");
      Files.delete(agentRule);
      SessionStartHook.HookResult subagentResult = runNative(subagentInput, environment);

      requireThat(mainResult.output(), "mainOutput").doesNotContain("Follow generated body.");
      requireThat(body, "body").isRegularFile();
      requireThat(subagentResult.output(), "subagentOutput").contains("# Agent Path Rule");
      requireThat(subagentResult.output(), "subagentOutput").contains(body.toString());
      requireThat(subagentResult.output(), "subagentOutput").doesNotContain("Follow generated body.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies authored lazy-load declarations use the standard compact rule-loading stub.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartRendersAuthoredLazyLoadStub() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.createDirectories(fixture.projectRoot().resolve(".cat/rules/include"));
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/common/hooks.md"), """
        ---
        paths: ["hooks/**"]
        ---
        # Hook Guidance

        When working on hook behavior:
        Lazy load `../include/hooks.md`.

        When working on hook tests:
        Lazy load `../include/hook-tests.md`.
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/include/hooks.md"), """
        # Hook Details

        Detailed hook rule body.
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/include/hook-tests.md"), """
        # Hook Test Details

        Detailed hook test rule body.
        """, StandardCharsets.UTF_8);

      SessionStartHook.HookResult result = run(fixture);

      Path body = generatedBody(fixture.pluginData(), "project",
        ".cat/rules/common/hooks.md.lazy-loads/01-hooks.md");
      requireThat(Files.readString(body, StandardCharsets.UTF_8), "body").isEqualTo("""
        # Hook Details

        Detailed hook rule body.
        """);
      Path testBody = generatedBody(fixture.pluginData(), "project",
        ".cat/rules/common/hooks.md.lazy-loads/02-hook-tests.md");
      requireThat(Files.readString(testBody, StandardCharsets.UTF_8), "testBody").isEqualTo("""
        # Hook Test Details

        Detailed hook test rule body.
        """);
      String additionalContext = additionalContext(result.output());
      requireThat(additionalContext, "additionalContext").contains("# Hook Guidance");
      requireThat(additionalContext, "additionalContext").contains("`paths` = [\"hooks/**\"]");
      requireThat(additionalContext, "additionalContext").contains(
        "Lazy load `" + body.toString().replace('\\', '/') + "`.");
      requireThat(additionalContext, "additionalContext").contains(
        "Lazy load `" + testBody.toString().replace('\\', '/') + "`.");
      requireThat(additionalContext, "additionalContext").contains(
        "Apply `rules/codex/path-filter.md`.");
      requireThat(additionalContext, "additionalContext").doesNotContain("Detailed hook rule body.");
      requireThat(additionalContext, "additionalContext").doesNotContain("Detailed hook test rule body.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies authored lazy-load declarations cannot read outside {@code rules/include}.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartRejectsUnsafeLazyLoad() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/common/hooks.md"), """
        ---
        paths: ["hooks/**"]
        ---
        # Hook Guidance

        When working on hook behavior:
        Lazy load `../secret.md`.
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/secret.md"), "# Secret\n",
        StandardCharsets.UTF_8);

      SessionStartHook.HookResult result = run(fixture);

      requireThat(String.join("\n", result.warnings()), "warnings").contains("escapes rules/include");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies incidental references to path-filter.md do not suppress the required directive.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sessionStartAddsPathFilterDirective() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.createDirectories(fixture.projectRoot().resolve(".cat/rules/include"));
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/common/hooks.md"), """
        ---
        paths: ["hooks/**"]
        ---
        # Hook Guidance

        This prose says Apply `rules/codex/path-filter.md`. but is not the directive.

        When working on hook behavior:
        Lazy load `../include/hooks.md`.
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.projectRoot().resolve(".cat/rules/include/hooks.md"), """
        # Hook Details
        """, StandardCharsets.UTF_8);

      SessionStartHook.HookResult result = run(fixture);

      requireThat(additionalContext(result.output()), "additionalContext").contains(
        "Apply `rules/codex/path-filter.md`.");
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
        agents: ["main", "cat:work-execute"]
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
        agents: ["main", "cat:work-execute"]
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
        agents: ["main", "cat:work-execute"]
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
        agents: ["cat:work-execute"]
        ---
        agent eager rule
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/agent-path-scoped.md"), """
        ---
        agents: ["cat:work-execute"]
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
      requireThat(generatedBody(fixture.pluginData(), "project", ".cat/rules/common/java.md"),
        "projectBody").isRegularFile();
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
      requireThat(generatedBody(fixture.pluginData(), "project", ".cat/rules/common/java.md"),
        "projectBody").isRegularFile();
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
      requireThat(generatedBody(fixture.pluginData(), "project", ".cat/rules/common/java.md"),
        "projectBody").isRegularFile();
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
      requireThat(generatedBody(pluginData, "project", ".cat/rules/common/java.md"),
        "projectBody").isRegularFile();
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
      requireThat(generatedBody(environmentPluginData, "project", ".cat/rules/common/native-project.md"),
        "nativeProjectBody").isRegularFile();
      requireThat(generatedBody(environmentPluginData, "plugin", "rules/common/native-plugin.md"),
        "nativePluginBody").isRegularFile();
      requireThat(Files.exists(generatedBody(environmentPluginData, "project",
        ".cat/rules/common/environment-project.md")), "environmentProjectBody").isFalse();
      requireThat(Files.exists(generatedBody(environmentPluginData, "plugin",
        "rules/common/environment-plugin.md")), "environmentPluginBody").isFalse();
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
    expectedExceptionsMessageRegExp = ".*CAT_PLUGIN_ROOT or Codex PLUGIN_ROOT is required.*")
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
   * Verifies that SessionStart removes managed plugin-data bodies whose source rule no longer exists.
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
      Path staleBody = generatedBody(fixture.pluginData(), "project", ".cat/rules/common/stale.md");
      Files.createDirectories(staleBody.getParent());
      Files.writeString(staleBody, "# Stale Body\n",
        StandardCharsets.UTF_8);
      writeManifest(fixture.pluginData(), """
        {
          "version": 1,
          "entries": [
            {
              "contextPath": ".cat/rules/common/stale.md",
              "mainAgent": true,
              "subAgents": null,
              "paths": ["*.stale"],
              "title": "# Stale",
              "stubTemplate": null,
              "bodyPath": "%s"
            }
          ]
        }
        """.formatted(staleBody.toString().replace("\\", "\\\\")));

      run(fixture);

      requireThat(Files.exists(staleBody), "staleBody.exists").isFalse();
      requireThat(Files.exists(generatedManifest(fixture.pluginData())), "manifest.exists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart uses the generated-body manifest to prune plugin bodies after a
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
      Path body = generatedBody(fixture.pluginData(), "plugin", "rules/common/java.md");
      Path manifest = generatedManifest(fixture.pluginData());
      requireThat(body, "body").isRegularFile();
      requireThat(manifest, "manifest").isRegularFile();
      requireThat(Files.readString(manifest, StandardCharsets.UTF_8), "manifest.content").isEqualTo(
        """
          {
            "version" : 1,
            "entries" : [ {
              "contextPath" : "rules/common/java.md",
              "mainAgent" : true,
              "subAgents" : null,
              "paths" : [ "*.java" ],
              "title" : "# Java Common",
              "stubTemplate" : null,
              "bodyPath" : "%s",
              "lazyLoads" : [ ]
            } ]
          }
          """.formatted(body.toString().replace("\\", "\\\\")));
      Files.delete(commonRule);
      Thread.sleep(2_100);

      run(fixture);

      requireThat(Files.exists(body), "body.exists").isFalse();
      requireThat(Files.exists(manifest), "manifest.exists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart preserves existing files when the generated-file manifest is invalid.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void invalidManifestPreservesFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-session-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Path staleBody = generatedBody(fixture.pluginData(), "project", ".cat/rules/common/stale.md");
      Files.createDirectories(staleBody.getParent());
      Files.writeString(staleBody, "# Stale\n", StandardCharsets.UTF_8);
      writeManifest(fixture.pluginData(), """
        {
          "version": 1,
          "entries": "../outside.md"
        }
        """);

      run(fixture);

      requireThat(staleBody, "staleBody").isRegularFile();
      requireThat(Files.exists(generatedManifest(fixture.pluginData())), "manifest.exists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart prunes managed bodies when a common-rule directory is removed.
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
      Path staleBody = generatedBody(fixture.pluginData(), "project", ".cat/rules/common/stale.md");
      Path staleLazyLoadBody = generatedBody(fixture.pluginData(), "project",
        ".cat/rules/common/stale.md.lazy-loads/02-extra.md");
      Files.createDirectories(staleBody.getParent());
      Files.createDirectories(staleLazyLoadBody.getParent());
      Files.writeString(staleBody, "# Stale\n", StandardCharsets.UTF_8);
      Files.writeString(staleLazyLoadBody, "# Extra Stale\n", StandardCharsets.UTF_8);
      String escapedStaleBody = staleBody.toString().replace("\\", "\\\\");
      String escapedStaleLazyLoadBody = staleLazyLoadBody.toString().replace("\\", "\\\\");
      writeManifest(fixture.pluginData(), """
        {
          "version": 1,
          "entries": [
            {
              "contextPath": ".cat/rules/common/stale.md",
              "mainAgent": true,
              "subAgents": null,
              "paths": ["*.stale"],
              "title": "# Stale",
              "stubTemplate": null,
              "bodyPath": "%s",
              "lazyLoads": [
                {
                  "declarationPath": "../include/stale.md",
                  "bodyPath": "%s"
                },
                {
                  "declarationPath": "../include/extra.md",
                  "bodyPath": "%s"
                }
              ]
            }
          ]
        }
        """.formatted(escapedStaleBody, escapedStaleBody, escapedStaleLazyLoadBody));

      run(fixture);

      requireThat(Files.exists(staleBody), "staleBody.exists").isFalse();
      requireThat(Files.exists(staleLazyLoadBody), "staleLazyLoadBody.exists").isFalse();
      requireThat(Files.exists(generatedManifest(fixture.pluginData())), "manifest.exists").isFalse();
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

      SessionStartHook.HookResult result = run(fixture);

      requireThat(additionalContext(result.output()), "additionalContext").contains(
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
   * Verifies that SessionStart refuses to write generated bodies through a symlinked plugin-data
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
      Path outside = tempDir.resolve("outside-generated");
      Files.createDirectories(outside);
      Files.createSymbolicLink(fixture.pluginData().resolve("generated"), outside);

      SessionStartHook.HookResult result = run(fixture);

      requireThat(String.join("\n", result.warnings()), "warnings").contains("symbolic link");
      requireThat(result.output(), "output").contains("SessionStart Handler Errors");
      requireThat(Files.exists(outside.resolve("codex-rule-bodies/project/.cat/rules/common/java.md")),
        "outsideBody.exists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SessionStart refuses to write generated bodies through a symlinked target file.
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
      Path body = generatedBody(fixture.pluginData(), "project", ".cat/rules/common/java.md");
      Files.createDirectories(body.getParent());
      Files.createSymbolicLink(body, outside);

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

  /**
   * Returns the generated body path for a source rule.
   *
   * @param pluginData  the plugin data directory
   * @param namespace   the source namespace
   * @param contextPath the source rule context path
   * @return the generated body path
   */
  private static Path generatedBody(Path pluginData, String namespace, String contextPath)
  {
    return pluginData.resolve(GENERATED_BODY_ROOT).resolve(namespace).resolve(contextPath).
      toAbsolutePath().normalize();
  }

  /**
   * Returns the generated-body manifest path.
   *
   * @param pluginData the plugin data directory
   * @return the manifest path
   */
  private static Path generatedManifest(Path pluginData)
  {
    return pluginData.resolve(GENERATED_BODY_ROOT).resolve(GENERATED_BODY_MANIFEST).
      toAbsolutePath().normalize();
  }

  /**
   * Writes a generated-body manifest fixture.
   *
   * @param pluginData the plugin data directory
   * @param content    the manifest content
   * @throws IOException if file operations fail
   */
  private static void writeManifest(Path pluginData, String content) throws IOException
  {
    Path manifest = generatedManifest(pluginData);
    Files.createDirectories(manifest.getParent());
    Files.writeString(manifest, content, StandardCharsets.UTF_8);
  }

  /**
   * Extracts the hook additional context from a JSON hook result.
   *
   * @param output the hook output
   * @return the decoded additional context
   * @throws IOException if the output cannot be parsed
   */
  private static String additionalContext(String output) throws IOException
  {
    return JsonMapper.shared().readTree(output).path("hookSpecificOutput").path("additionalContext").
      asString();
  }

  private record Fixture(Path projectRoot, Path pluginRoot, Path pluginData)
  {
  }
}
