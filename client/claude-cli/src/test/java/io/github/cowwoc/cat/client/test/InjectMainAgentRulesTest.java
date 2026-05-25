/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.agent.SessionStartHandler;
import io.github.cowwoc.cat.agent.InjectMainAgentRules;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for InjectMainAgentRules.handle() behavior.
 */
public final class InjectMainAgentRulesTest
{
  /**
   * Verifies that handle() returns a non-empty context when the rules directory contains a rule
   * with mainAgent:true.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handleWithMainAgentTrueRuleReturns() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-rules-test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      // Create the rules directory inside the project dir (scope.getProjectPath())
      Path rulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("main-rule.md"), """
        ---
        mainAgent: true
        subAgents: []
        ---
        # Main agent rule content
        Important instruction for the main agent.
        """);

      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      SessionStartHandler.Result result = handler.handle();

      requireThat(result.additionalContext(), "additionalContext").contains("Main agent rule content");
      requireThat(result.additionalContext(), "additionalContext").contains(
        "Important instruction for the main agent.");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns content from the plugin rules directory even when no project
   * rules directory exists.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handleWithPluginRuleDirReturnsContext() throws IOException
  {
    Path projectPath = Files.createTempDirectory("inject-rules-plugin-project-");
    Path pluginDir = Files.createTempDirectory("inject-rules-plugin-root-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, pluginDir, projectPath))
    {
      Path pluginRulesDir = scope.getPluginRoot().resolve("rules").resolve("common");
      Files.createDirectories(pluginRulesDir);
      Files.writeString(pluginRulesDir.resolve("plugin-rule.md"), """
        ---
        mainAgent: true
        subAgents: []
        ---
        # Plugin bundled rule
        Plugin rule content for main agent.
        """);
      // No project rules directory created

      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      SessionStartHandler.Result result = handler.handle();

      requireThat(result.additionalContext(), "additionalContext").contains("Plugin bundled rule");
      requireThat(result.additionalContext(), "additionalContext").contains(
        "Plugin rule content for main agent.");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginDir);
    }
  }

  /**
   * Verifies that injected rule context identifies the source rule path.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handleWrapsInjectedRuleWithPath() throws IOException
  {
    Path projectPath = Files.createTempDirectory("inject-rules-path-project-");
    Path pluginDir = Files.createTempDirectory("inject-rules-path-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, pluginDir, projectPath))
    {
      Path pluginRulesDir = scope.getPluginRoot().resolve("rules").resolve("common");
      Files.createDirectories(pluginRulesDir);
      Files.writeString(pluginRulesDir.resolve("plugin-rule.md"), """
        ---
        mainAgent: true
        subAgents: []
        ---
        # Plugin bundled rule
        Plugin rule content for main agent.
        """);

      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      SessionStartHandler.Result result = handler.handle();

      requireThat(result.additionalContext(), "additionalContext").contains("""
        <rule path="rules/common/plugin-rule.md">
        # Plugin bundled rule
        Plugin rule content for main agent.
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
   * Verifies that Claude rule injection loads portable project rules and Claude-specific project
   * rules, while ignoring Codex-specific rules.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handleLoadsSharedAndClaudeProjectRules() throws IOException
  {
    Path projectPath = Files.createTempDirectory("inject-rules-engine-project-");
    Path pluginDir = Files.createTempDirectory("inject-rules-engine-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, pluginDir, projectPath))
    {
      Path sharedRulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(sharedRulesDir);
      Files.writeString(sharedRulesDir.resolve("shared-rule.md"), """
        ---
        mainAgent: true
        ---
        # Shared project rule
        Portable instruction for all engines.
        """);

      Path claudeRulesDir = scope.getProjectPath().resolve(".claude/rules");
      Files.createDirectories(claudeRulesDir);
      Files.writeString(claudeRulesDir.resolve("claude-rule.md"), """
        ---
        mainAgent: true
        ---
        # Claude project rule
        Claude-only instruction.
        """);

      Path codexRulesDir = scope.getProjectPath().resolve(".cat/rules/codex");
      Files.createDirectories(codexRulesDir);
      Files.writeString(codexRulesDir.resolve("codex-rule.md"), """
        ---
        mainAgent: true
        ---
        # Codex project rule
        Codex-only instruction.
        """);

      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      SessionStartHandler.Result result = handler.handle();

      requireThat(result.additionalContext(), "additionalContext").contains("Shared project rule");
      requireThat(result.additionalContext(), "additionalContext").contains("Portable instruction for all engines.");
      requireThat(result.additionalContext(), "additionalContext").contains("Claude project rule");
      requireThat(result.additionalContext(), "additionalContext").contains("Claude-only instruction.");
      requireThat(result.additionalContext(), "additionalContext").doesNotContain("Codex project rule");
      requireThat(result.additionalContext(), "additionalContext").doesNotContain("Codex-only instruction.");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginDir);
    }
  }

  /**
   * Verifies that when filenames collide, both plugin and project rules are included in the output.
   * Rules are concatenated in order: plugin-bundled first, project-local second.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handleBothPluginAndProjectRulesIncluded() throws IOException
  {
    Path projectPath = Files.createTempDirectory("inject-rules-override-project-");
    Path pluginDir = Files.createTempDirectory("inject-rules-override-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, pluginDir, projectPath))
    {
      Path pluginRulesDir = scope.getPluginRoot().resolve("rules").resolve("common");
      Files.createDirectories(pluginRulesDir);
      Files.writeString(pluginRulesDir.resolve("shared-rule.md"), """
        ---
        mainAgent: true
        ---
        # Plugin version of shared rule
        This is from the plugin.
        """);

      Path projectRulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(projectRulesDir);
      Files.writeString(projectRulesDir.resolve("shared-rule.md"), """
        ---
        mainAgent: true
        ---
        # Project version of shared rule
        This is from the project.
        """);

      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      SessionStartHandler.Result result = handler.handle();

      // Both rules are included (no deduplication)
      requireThat(result.additionalContext(), "additionalContext").contains(
        "Plugin version of shared rule");
      requireThat(result.additionalContext(), "additionalContext").contains("This is from the plugin.");
      requireThat(result.additionalContext(), "additionalContext").contains(
        "Project version of shared rule");
      requireThat(result.additionalContext(), "additionalContext").contains("This is from the project.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginDir);
    }
  }

  /**
   * Verifies that handle() returns an empty result when neither plugin nor project rules directory
   * exists.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handleWithMissingRulesDirReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-rules-empty-test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      // No rules directory created — getCatRulesForAudience will find no rules
      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      SessionStartHandler.Result result = handler.handle();

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle() returns an empty result when the rules directory exists but all rules
   * have mainAgent:false (no rules pass the main-agent filter).
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handleWithAllSubagentOnlyRulesReturns() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-rules-subonly-test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      Path rulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(rulesDir);
      // No subAgents frontmatter → null → targets all subagents; mainAgent: false excludes main agent
      Files.writeString(rulesDir.resolve("subagent-only.md"), """
        ---
        mainAgent: false
        ---
        # Only for subagents
        """);

      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      SessionStartHandler.Result result = handler.handle();

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when filenames collide, the plugin rule (mainAgent:true) is included and the
   * project rule (mainAgent:false) is filtered out by the main-agent audience filter. Both rules
   * exist in the concatenated list but only the mainAgent:true one passes the filter.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handlePluginRuleIncludedProjectRule() throws IOException
  {
    Path projectPath = Files.createTempDirectory("inject-rules-override-main-false-project-");
    Path pluginDir = Files.createTempDirectory("inject-rules-override-main-false-plugin-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, pluginDir, projectPath))
    {
      Path pluginRulesDir = scope.getPluginRoot().resolve("rules").resolve("common");
      Files.createDirectories(pluginRulesDir);
      // Plugin rule: mainAgent=true — passes the main-agent filter
      Files.writeString(pluginRulesDir.resolve("toggled-rule.md"), """
        ---
        mainAgent: true
        ---
        # Plugin main-agent rule
        This plugin content should reach the main agent.
        """);

      Path projectRulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(projectRulesDir);
      // Project rule with same filename: mainAgent=false — filtered out by main-agent filter
      Files.writeString(projectRulesDir.resolve("toggled-rule.md"), """
        ---
        mainAgent: false
        ---
        # Project rule: disabled for main agent
        This content should not appear in main agent context.
        """);

      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      SessionStartHandler.Result result = handler.handle();

      // Plugin rule (mainAgent:true) passes the filter and is included
      requireThat(result.additionalContext(), "additionalContext").contains("Plugin main-agent rule");
      requireThat(result.additionalContext(), "additionalContext").contains(
        "This plugin content should reach the main agent.");
      // Project rule (mainAgent:false) is filtered out
      requireThat(result.additionalContext(), "additionalContext").doesNotContain(
        "Project rule: disabled for main agent");
      requireThat(result.additionalContext(), "additionalContext").doesNotContain(
        "This content should not appear in main agent context.");
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
  public void testHandleWithNullInputThrowsNullPointer()
  {
    new InjectMainAgentRules(null);
  }

  /**
   * Verifies that handle() returns only mainAgent:true rules when both mainAgent:true and
   * mainAgent:false rules are present.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void testHandleWithMixedMainAgentRulesFilters() throws IOException
  {
    Path tempDir = Files.createTempDirectory("inject-rules-mixed-test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      Path rulesDir = scope.getProjectPath().resolve(".cat/rules/common");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("main-only.md"), """
        ---
        mainAgent: true
        ---
        # Main agent exclusive content
        Only the main agent should see this.
        """);
      Files.writeString(rulesDir.resolve("subagent-only.md"), """
        ---
        mainAgent: false
        ---
        # Subagent exclusive content
        Only subagents should see this.
        """);

      InjectMainAgentRules handler = new InjectMainAgentRules(scope);

      SessionStartHandler.Result result = handler.handle();

      requireThat(result.additionalContext(), "additionalContext").contains("Main agent exclusive content");
      requireThat(result.additionalContext(), "additionalContext").contains(
        "Only the main agent should see this.");
      requireThat(result.additionalContext(), "additionalContext").doesNotContain(
        "Subagent exclusive content");
      requireThat(result.additionalContext(), "additionalContext").doesNotContain(
        "Only subagents should see this.");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
