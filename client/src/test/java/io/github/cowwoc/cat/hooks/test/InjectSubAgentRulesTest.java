/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.session.InjectSubAgentRules;
import io.github.cowwoc.cat.hooks.session.SubagentStartHandler;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for InjectSubAgentRules.handle() behavior.
 */
public final class InjectSubAgentRulesTest
{
  private HookInput createInput(JsonMapper mapper, String json)
  {
    InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    return HookInput.readFrom(mapper, stream);
  }

  /**
   * Verifies that handle() returns content from the plugin rules directory even when no project
   * rules directory exists.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesFromPluginRulesDir() throws IOException
  {
    Path projectDir = Files.createTempDirectory("inject-subagent-plugin-project-");
    Path pluginDir = Files.createTempDirectory("inject-subagent-plugin-root-");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginDir))
    {
      Path pluginRulesDir = scope.getClaudePluginRoot().resolve("rules");
      Files.createDirectories(pluginRulesDir);
      // No subAgents frontmatter → null → matches all subagents
      Files.writeString(pluginRulesDir.resolve("plugin-subagent-rule.md"), """
        ---
        mainAgent: false
        ---
        # Plugin subagent rule
        Plugin rule content for subagents.
        """);
      // No project rules directory created

      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").contains("Plugin subagent rule");
      requireThat(result.additionalContext(), "additionalContext").contains(
        "Plugin rule content for subagents.");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginDir);
    }
  }

  /**
   * Verifies that when filenames collide, both plugin and project subagent rules are included.
   * Rules are concatenated in order: plugin-bundled first, project-local second.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesBothPluginAndProjectRulesIncludedOnFilenameCollision() throws IOException
  {
    Path projectDir = Files.createTempDirectory("inject-subagent-override-project-");
    Path pluginDir = Files.createTempDirectory("inject-subagent-override-plugin-");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginDir))
    {
      Path pluginRulesDir = scope.getClaudePluginRoot().resolve("rules");
      Files.createDirectories(pluginRulesDir);
      Files.writeString(pluginRulesDir.resolve("shared-subagent-rule.md"), """
        ---
        mainAgent: false
        ---
        # Plugin version
        This is from the plugin.
        """);

      Path projectRulesDir = scope.getClaudeProjectDir().resolve(".cat/rules");
      Files.createDirectories(projectRulesDir);
      Files.writeString(projectRulesDir.resolve("shared-subagent-rule.md"), """
        ---
        mainAgent: false
        ---
        # Project version
        This is from the project.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      // Both rules are included (no deduplication)
      requireThat(result.additionalContext(), "additionalContext").contains("Plugin version");
      requireThat(result.additionalContext(), "additionalContext").contains("This is from the plugin.");
      requireThat(result.additionalContext(), "additionalContext").contains("Project version");
      requireThat(result.additionalContext(), "additionalContext").contains("This is from the project.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginDir);
    }
  }

  /**
   * Verifies that handle() returns rules with no subAgents restriction when subagent_type is blank.
   * Rules with null subAgents should reach all subagents regardless of type.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesBlankSubagentTypeMatchesAllSubagentRule() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-blank-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path rulesDir = scope.getClaudeProjectDir().resolve(".cat/rules");
      Files.createDirectories(rulesDir);
      // No subAgents frontmatter → null → matches all subagents
      Files.writeString(rulesDir.resolve("universal.md"), """
        ---
        mainAgent: false
        ---
        # Universal subagent content
        Applies to any subagent.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      // Blank subagent_type
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").contains("Universal subagent content");
      requireThat(result.additionalContext(), "additionalContext").contains("Applies to any subagent.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns rules matching the specific subagent_type.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesPopulatedSubagentTypeMatchesSpecificRule() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-specific-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path rulesDir = scope.getClaudeProjectDir().resolve(".cat/rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("typed-rule.md"), """
        ---
        mainAgent: false
        subAgents: ["cat:work-execute"]
        ---
        # Work execute specific content
        Only for cat:work-execute.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").contains("Work execute specific content");
      requireThat(result.additionalContext(), "additionalContext").contains("Only for cat:work-execute.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns an empty result when the rules directory does not exist.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesEmptyRulesDirReturnsEmptyString() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-empty-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // No rules directory created
      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when filenames collide, the plugin rule (null subAgents = all subagents) is
   * included and the project rule (subAgents:[]) is filtered out by the subagent audience filter.
   * Both rules exist in the concatenated list but only the one matching the subagent type passes.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesPluginRuleIncludedProjectRuleFilteredWhenSubagentsEmpty() throws IOException
  {
    Path projectDir = Files.createTempDirectory("inject-subagent-override-empty-project-");
    Path pluginDir = Files.createTempDirectory("inject-subagent-override-empty-plugin-");
    try (JvmScope scope = new TestJvmScope(projectDir, pluginDir))
    {
      Path pluginRulesDir = scope.getClaudePluginRoot().resolve("rules");
      Files.createDirectories(pluginRulesDir);
      // Plugin rule: null subAgents → matches all subagent types — passes filter
      Files.writeString(pluginRulesDir.resolve("toggled-subagent-rule.md"), """
        ---
        mainAgent: false
        ---
        # Plugin universal subagent rule
        This plugin content should reach all subagents.
        """);

      Path projectRulesDir = scope.getClaudeProjectDir().resolve(".cat/rules");
      Files.createDirectories(projectRulesDir);
      // Project rule with same filename: subAgents:[] → matches no subagent type — filtered out
      Files.writeString(projectRulesDir.resolve("toggled-subagent-rule.md"), """
        ---
        mainAgent: false
        subAgents: []
        ---
        # Project rule: restricted to no subagents
        This content should not reach any subagent.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      // Plugin rule (null subAgents) passes the filter and is included
      requireThat(result.additionalContext(), "additionalContext").contains(
        "Plugin universal subagent rule");
      requireThat(result.additionalContext(), "additionalContext").contains(
        "This plugin content should reach all subagents.");
      // Project rule (subAgents:[]) is filtered out
      requireThat(result.additionalContext(), "additionalContext").doesNotContain(
        "Project rule: restricted to no subagents");
      requireThat(result.additionalContext(), "additionalContext").doesNotContain(
        "This content should not reach any subagent.");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
      TestUtils.deleteDirectoryRecursively(pluginDir);
    }
  }

  /**
   * Verifies that the constructor throws NullPointerException when scope is null.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void testConstructorWithNullScopeThrowsNullPointerException()
  {
    new InjectSubAgentRules(null);
  }

  /**
   * Verifies that handle() throws NullPointerException when input is null.
   *
   * @throws IOException if file operations fail
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void testGetRulesWithNullInputThrowsNullPointerException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-null-input-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      handler.handle(null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns an empty result when subAgents is an empty list, regardless
   * of the subagent_type requested.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void testGetRulesEmptySubagentsExcludesAll() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-empty-subagents-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path rulesDir = scope.getClaudeProjectDir().resolve(".cat/rules");
      Files.createDirectories(rulesDir);
      // subAgents: [] means no subagent type matches — exclude all
      Files.writeString(rulesDir.resolve("excluded-rule.md"), """
        ---
        mainAgent: false
        subAgents: []
        ---
        # Excluded content
        This rule should not reach any subagent.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns an empty result when the subagent_type does not match the
   * specific type listed in the rule's subAgents frontmatter.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void testGetRulesTypeDoesNotMatchDifferentSpecificType() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-type-mismatch-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path rulesDir = scope.getClaudeProjectDir().resolve(".cat/rules");
      Files.createDirectories(rulesDir);
      // Rule targets cat:work-execute only
      Files.writeString(rulesDir.resolve("typed-rule.md"), """
        ---
        mainAgent: false
        subAgents: ["cat:work-execute"]
        ---
        # Work execute only content
        Only for cat:work-execute subagents.
        """);

      JsonMapper mapper = scope.getJsonMapper();
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);
      // Request as a different subagent type
      HookInput input = createInput(mapper,
        "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:git-commit\"}");

      SubagentStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
