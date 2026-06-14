/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.session.InjectSubAgentRules;
import io.github.cowwoc.cat.claude.hook.session.SubagentStartHandler;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for InjectSubAgentRules.handle() behavior.
 */
public final class InjectSubAgentRulesTest
{
  /**
   * Verifies that handle() returns content from the plugin rules directory even when no project
   * rules directory exists.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesFromPluginRulesDir() throws IOException
  {
    Path projectPath = Files.createTempDirectory("inject-subagent-plugin-project-");
    Path pluginDir = Files.createTempDirectory("inject-subagent-plugin-root-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}",
      projectPath, pluginDir, projectPath))
    {
      Path pluginRulesDir = scope.getPluginRoot().resolve("rules").resolve("common");
      Files.createDirectories(pluginRulesDir);
      // agents: ["subagents"] matches all subagents.
      Files.writeString(pluginRulesDir.resolve("plugin-subagent-rule.md"), """
        ---
        agents: ["subagents"]
        ---
        # Plugin subagent rule
        Plugin rule content for subagents.
        """);
      // No project rules directory created

      InjectSubAgentRules handler = new InjectSubAgentRules(scope);

      SubagentStartHandler.Result result = handler.handle();

      requireThat(result.additionalContext(), "additionalContext").contains("Plugin subagent rule");
      requireThat(result.additionalContext(), "additionalContext").contains(
        "Plugin rule content for subagents.");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginDir);
    }
  }

  /**
   * Verifies that injected subagent rule context identifies the source rule path.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesWrapsInjectedRuleWithPath() throws IOException
  {
    Path projectPath = Files.createTempDirectory("inject-subagent-path-project-");
    Path pluginDir = Files.createTempDirectory("inject-subagent-path-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}",
      projectPath, pluginDir, projectPath))
    {
      Path pluginRulesDir = scope.getPluginRoot().resolve("rules").resolve("common");
      Files.createDirectories(pluginRulesDir);
      Files.writeString(pluginRulesDir.resolve("plugin-subagent-rule.md"), """
        ---
        agents: ["subagents"]
        ---
        # Plugin subagent rule
        Plugin rule content for subagents.
        """);

      InjectSubAgentRules handler = new InjectSubAgentRules(scope);

      SubagentStartHandler.Result result = handler.handle();

      requireThat(result.additionalContext(), "additionalContext").contains("""
        <rule path="rules/common/plugin-subagent-rule.md">
        # Plugin subagent rule
        Plugin rule content for subagents.
        </rule>""");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
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
  public void getRulesBothPluginAndProjectRules() throws IOException
  {
    Path projectPath = Files.createTempDirectory("inject-subagent-override-project-");
    Path pluginDir = Files.createTempDirectory("inject-subagent-override-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\"}",
      projectPath, pluginDir, projectPath))
    {
      Path pluginRulesDir = scope.getPluginRoot().resolve("rules").resolve("common");
      Files.createDirectories(pluginRulesDir);
      Files.writeString(pluginRulesDir.resolve("shared-subagent-rule.md"), """
        ---
        agents: ["subagents"]
        ---
        # Plugin version
        This is from the plugin.
        """);

      Path projectRulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(projectRulesDir);
      Files.writeString(projectRulesDir.resolve("shared-subagent-rule.md"), """
        ---
        agents: ["subagents"]
        ---
        # Project version
        This is from the project.
        """);

      InjectSubAgentRules handler = new InjectSubAgentRules(scope);

      SubagentStartHandler.Result result = handler.handle();

      // Both rules are included (no deduplication)
      requireThat(result.additionalContext(), "additionalContext").contains("Plugin version");
      requireThat(result.additionalContext(), "additionalContext").contains("This is from the plugin.");
      requireThat(result.additionalContext(), "additionalContext").contains("Project version");
      requireThat(result.additionalContext(), "additionalContext").contains("This is from the project.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginDir);
    }
  }

  /**
   * Verifies that handle() returns all-subagent rules when subagent_type is blank.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesBlankSubagentTypeMatchesAll() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-blank-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\"}",
      tempDir, tempDir, tempDir))
    {
      Path rulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(rulesDir);
      // agents: ["subagents"] matches all subagents.
      Files.writeString(rulesDir.resolve("universal.md"), """
        ---
        agents: ["subagents"]
        ---
        # Universal subagent content
        Applies to any subagent.
        """);

      InjectSubAgentRules handler = new InjectSubAgentRules(scope);

      SubagentStartHandler.Result result = handler.handle();

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
  public void getRulesPopulatedSubagentTypeMatches() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-specific-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}",
      tempDir, tempDir, tempDir))
    {
      Path rulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("typed-rule.md"), """
        ---
        agents: ["cat:work-execute"]
        ---
        # Work execute specific content
        Only for cat:work-execute.
        """);

      InjectSubAgentRules handler = new InjectSubAgentRules(scope);

      SubagentStartHandler.Result result = handler.handle();

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
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}",
      tempDir, tempDir, tempDir))
    {
      // No rules directory created
      InjectSubAgentRules handler = new InjectSubAgentRules(scope);

      SubagentStartHandler.Result result = handler.handle();

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when filenames collide, the plugin all-subagent rule is included and the project
   * main-only rule is filtered out by the subagent audience filter.
   * Both rules exist in the concatenated list but only the one matching the subagent type passes.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void getRulesPluginRuleIncludedProjectRule() throws IOException
  {
    Path projectPath = Files.createTempDirectory("inject-subagent-override-empty-project-");
    Path pluginDir = Files.createTempDirectory("inject-subagent-override-empty-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}",
      projectPath, pluginDir, projectPath))
    {
      Path pluginRulesDir = scope.getPluginRoot().resolve("rules").resolve("common");
      Files.createDirectories(pluginRulesDir);
      // Plugin rule targets all subagent types and passes the filter.
      Files.writeString(pluginRulesDir.resolve("toggled-subagent-rule.md"), """
        ---
        agents: ["subagents"]
        ---
        # Plugin universal subagent rule
        This plugin content should reach all subagents.
        """);

      Path projectRulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(projectRulesDir);
      // Project rule with same filename targets only the main agent and is filtered out.
      Files.writeString(projectRulesDir.resolve("toggled-subagent-rule.md"), """
        ---
        agents: ["main"]
        ---
        # Project rule: restricted to no subagents
        This content should not reach any subagent.
        """);

      InjectSubAgentRules handler = new InjectSubAgentRules(scope);

      SubagentStartHandler.Result result = handler.handle();

      // Plugin all-subagent rule passes the filter and is included.
      requireThat(result.additionalContext(), "additionalContext").contains(
        "Plugin universal subagent rule");
      requireThat(result.additionalContext(), "additionalContext").contains(
        "This plugin content should reach all subagents.");
      // Project main-only rule is filtered out.
      requireThat(result.additionalContext(), "additionalContext").doesNotContain(
        "Project rule: restricted to no subagents");
      requireThat(result.additionalContext(), "additionalContext").doesNotContain(
        "This content should not reach any subagent.");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginDir);
    }
  }

  /**
   * Verifies that the constructor throws NullPointerException when input is null.
   *
   * @throws IOException if file operations fail
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void testGetRulesWithNullInputThrowsNull()
  {
    new InjectSubAgentRules(null);
  }

  /**
   * Verifies that handle() returns an empty result when the rule targets only the main agent.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void testGetRulesMainOnlyExcludesAllSubagents() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-empty-subagents-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:work-execute\"}",
      tempDir, tempDir, tempDir))
    {
      Path rulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(rulesDir);
      // agents: ["main"] means no subagent type matches.
      Files.writeString(rulesDir.resolve("excluded-rule.md"), """
        ---
        agents: ["main"]
        ---
        # Excluded content
        This rule should not reach any subagent.
        """);

      InjectSubAgentRules handler = new InjectSubAgentRules(scope);

      SubagentStartHandler.Result result = handler.handle();

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
   * specific type listed in the rule's agents frontmatter.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void testGetRulesTypeDoesNotMatchDifferent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-subagent-rules-type-mismatch-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"session_id\": \"test-session\", \"agent_id\": \"agent-1\", \"subagent_type\": \"cat:git-commit\"}",
      tempDir, tempDir, tempDir))
    {
      Path rulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(rulesDir);
      // Rule targets cat:work-execute only
      Files.writeString(rulesDir.resolve("typed-rule.md"), """
        ---
        agents: ["cat:work-execute"]
        ---
        # Work execute only content
        Only for cat:work-execute subagents.
        """);

      InjectSubAgentRules handler = new InjectSubAgentRules(scope);

      SubagentStartHandler.Result result = handler.handle();

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
