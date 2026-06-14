/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.codex;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.client.test.TestUtils;
import io.github.cowwoc.cat.codex.hook.CodexHookScope;
import io.github.cowwoc.cat.codex.hook.SessionStartHook;
import io.github.cowwoc.cat.codex.hook.SubagentStartHook;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Tests for the Codex SubagentStart hook.
 */
public final class SubagentStartHookTest
{
  /**
   * Verifies that SubagentStart injects agent-targeted rules using native Codex 0.134.0
   * {@code agent_type} metadata.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void subagentStartInjectsAgentRules() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-subagent-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/main.md"), """
        ---
        agents: ["main"]
        ---
        main-only rule
        """, StandardCharsets.UTF_8);
      Files.writeString(fixture.pluginRoot().resolve("rules/codex/targeted.md"), """
        ---
        agents: ["main", "cat:work-execute"]
        ---
        targeted subagent-start rule
        """, StandardCharsets.UTF_8);
      String nativeInput = """
        {
          "cwd": "%s",
          "hook_event_name": "SubagentStart",
          "agent_id": "agent-1",
          "agent_type": "cat:work-execute"
        }
        """.formatted(fixture.projectRoot().toString().replace("\\", "\\\\"));

      SessionStartHook.HookResult result = runNative(nativeInput, fixture.environment());

      requireThat(result.output(), "output").contains("\"hookEventName\" : \"SubagentStart\"");
      requireThat(result.output(), "output").contains("targeted subagent-start rule");
      requireThat(result.output(), "output").doesNotContain("main-only rule");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SubagentStart does not run SessionStart-only migration or critical-thinking
   * handlers.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void subagentStartSkipsSessionHandlers() throws IOException
  {
    Path tempDir = Files.createTempDirectory("codex-subagent-start-test-");
    try
    {
      Fixture fixture = createFixture(tempDir);
      String nativeInput = """
        {
          "cwd": "%s",
          "hook_event_name": "SubagentStart",
          "agent_type": "cat:work-execute"
        }
        """.formatted(fixture.projectRoot().toString().replace("\\", "\\\\"));

      SessionStartHook.HookResult result = runNative(nativeInput, fixture.environment());

      requireThat(result.output(), "output").contains("\"hookEventName\" : \"SubagentStart\"");
      requireThat(result.output(), "output").doesNotContain("critical thinking");
      requireThat(Files.exists(fixture.pluginData().resolve("migrations")), "migrationDirectoryExists").
        isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Creates a minimal Codex hook fixture.
   *
   * @param tempDir the temporary root directory
   * @return the fixture
   * @throws IOException if directory creation fails
   */
  private static Fixture createFixture(Path tempDir) throws IOException
  {
    Path projectRoot = tempDir.resolve("project");
    Path pluginRoot = tempDir.resolve("plugin");
    Path pluginData = tempDir.resolve("plugin-data");
    Files.createDirectories(projectRoot.resolve(".cat/rules/codex"));
    Files.createDirectories(pluginRoot.resolve(".codex-plugin"));
    Files.createDirectories(pluginRoot.resolve("rules/common"));
    Files.createDirectories(pluginRoot.resolve("rules/codex"));
    Files.createDirectories(pluginData);
    Files.writeString(pluginRoot.resolve(".codex-plugin/plugin.json"), "{\"version\":\"2.1\"}\n",
      StandardCharsets.UTF_8);
    return new Fixture(projectRoot, pluginRoot, pluginData);
  }

  /**
   * Runs the Codex SubagentStart hook from native input using a production scope.
   *
   * @param nativeInput the native Codex hook payload
   * @param environment the process environment
   * @return the hook result
   */
  private static SessionStartHook.HookResult runNative(String nativeInput,
    Map<String, String> environment)
  {
    SubagentStartHook hook = new SubagentStartHook();
    try (CodexHookScope scope = hook.createScope(
      new ByteArrayInputStream(nativeInput.getBytes(StandardCharsets.UTF_8)), environment,
      Path.of(System.getProperty("user.dir"))))
    {
      return hook.run(scope);
    }
  }

  private record Fixture(Path projectRoot, Path pluginRoot, Path pluginData)
  {
    /**
     * Returns the hook environment for the fixture.
     *
     * @return the hook environment
     */
    private Map<String, String> environment()
    {
      return Map.of(
        "CAT_PLUGIN_ROOT", pluginRoot.toString(),
        "CAT_PLUGIN_DATA", pluginData.toString(),
        "TZ", "UTC");
    }
  }
}
