/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.agent.AgentPluginScope;
import io.github.cowwoc.cat.agent.AbstractAgentScope;
import io.github.cowwoc.cat.agent.MainAgentRules;
import io.github.cowwoc.cat.agent.TerminalType;
import org.testng.annotations.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for runtime-aware main-agent rule loading.
 */
public final class MainAgentRulesTest
{
  private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

  /**
   * Verifies that Claude loads shared and Claude-specific rules, but not Codex-specific rules.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void claudeLoadsSharedAndClaudeRulesOnly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("main-agent-rules-test-");
    try
    {
      Path pluginRoot = tempDir.resolve("plugin");
      Path projectRoot = tempDir.resolve("project");
      writeRule(pluginRoot.resolve("rules/common/shared.md"), "shared rule");
      writeRule(pluginRoot.resolve("rules/claude/claude.md"), "claude rule");
      writeRule(pluginRoot.resolve("rules/codex/codex.md"), "codex rule");

      try (AgentPluginScope scope = new TestAgentPluginScope(projectRoot, pluginRoot,
        List.of(
          pluginRoot.resolve("rules/common"),
          pluginRoot.resolve("rules/claude"),
          projectRoot.resolve(".cat/rules/common"),
          projectRoot.resolve(".cat/rules/claude"),
          projectRoot.resolve(".claude/rules"))))
      {
        String result = MainAgentRules.load(scope, YAML_MAPPER);

        requireThat(result, "result").contains("shared rule");
        requireThat(result, "result").contains("claude rule");
        requireThat(result, "result").doesNotContain("codex rule");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex loads shared and Codex-specific rules, but not Claude-specific rules.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void codexLoadsSharedAndCodexRulesOnly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("main-agent-rules-test-");
    try
    {
      Path pluginRoot = tempDir.resolve("plugin");
      Path projectRoot = tempDir.resolve("project");
      writeRule(pluginRoot.resolve("rules/common/shared.md"), "shared rule");
      writeRule(pluginRoot.resolve("rules/claude/claude.md"), "claude rule");
      writeRule(pluginRoot.resolve("rules/codex/codex.md"), "codex rule");

      try (AgentPluginScope scope = new TestAgentPluginScope(projectRoot, pluginRoot,
        List.of(
          pluginRoot.resolve("rules/common"),
          pluginRoot.resolve("rules/codex"),
          projectRoot.resolve(".cat/rules/common"),
          projectRoot.resolve(".cat/rules/codex"))))
      {
        String result = MainAgentRules.load(scope, YAML_MAPPER);

        requireThat(result, "result").contains("shared rule");
        requireThat(result, "result").doesNotContain("claude rule");
        requireThat(result, "result").contains("codex rule");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Claude appends same-named rules from every configured rule directory in order,
   * without deduplicating by filename.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void claudeAppendsSameNamedRulesInDirectoryOrder() throws IOException
  {
    Path tempDir = Files.createTempDirectory("main-agent-rules-test-");
    try
    {
      Path pluginRoot = tempDir.resolve("plugin");
      Path projectRoot = tempDir.resolve("project");
      writeRule(pluginRoot.resolve("rules/common/shared.md"), "1 plugin common");
      writeRule(pluginRoot.resolve("rules/claude/shared.md"), "2 plugin claude");
      writeRule(projectRoot.resolve(".cat/rules/common/shared.md"), "3 project common");
      writeRule(projectRoot.resolve(".cat/rules/claude/shared.md"), "4 project cat claude");
      writeRule(projectRoot.resolve(".claude/rules/shared.md"), "5 project claude");

      try (AgentPluginScope scope = new TestAgentPluginScope(projectRoot, pluginRoot,
        List.of(
          pluginRoot.resolve("rules/common"),
          pluginRoot.resolve("rules/claude"),
          projectRoot.resolve(".cat/rules/common"),
          projectRoot.resolve(".cat/rules/claude"),
          projectRoot.resolve(".claude/rules"))))
      {
        String result = MainAgentRules.load(scope, YAML_MAPPER);

        requireThat(result.indexOf("1 plugin common"), "pluginCommonIndex").isGreaterThanOrEqualTo(0);
        requireThat(result.indexOf("2 plugin claude"), "pluginClaudeIndex").
          isGreaterThan(result.indexOf("1 plugin common"));
        requireThat(result.indexOf("3 project common"), "projectCommonIndex").
          isGreaterThan(result.indexOf("2 plugin claude"));
        requireThat(result.indexOf("4 project cat claude"), "projectCatClaudeIndex").
          isGreaterThan(result.indexOf("3 project common"));
        requireThat(result.indexOf("5 project claude"), "projectClaudeIndex").
          isGreaterThan(result.indexOf("4 project cat claude"));
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex appends same-named rules from every configured rule directory in order,
   * without deduplicating by filename.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void codexAppendsSameNamedRulesInDirectoryOrder() throws IOException
  {
    Path tempDir = Files.createTempDirectory("main-agent-rules-test-");
    try
    {
      Path pluginRoot = tempDir.resolve("plugin");
      Path projectRoot = tempDir.resolve("project");
      writeRule(pluginRoot.resolve("rules/common/shared.md"), "1 plugin common");
      writeRule(pluginRoot.resolve("rules/codex/shared.md"), "2 plugin codex");
      writeRule(projectRoot.resolve(".cat/rules/common/shared.md"), "3 project common");
      writeRule(projectRoot.resolve(".cat/rules/codex/shared.md"), "4 project codex");

      try (AgentPluginScope scope = new TestAgentPluginScope(projectRoot, pluginRoot,
        List.of(
          pluginRoot.resolve("rules/common"),
          pluginRoot.resolve("rules/codex"),
          projectRoot.resolve(".cat/rules/common"),
          projectRoot.resolve(".cat/rules/codex"))))
      {
        String result = MainAgentRules.load(scope, YAML_MAPPER);

        requireThat(result.indexOf("1 plugin common"), "pluginCommonIndex").isGreaterThanOrEqualTo(0);
        requireThat(result.indexOf("2 plugin codex"), "pluginCodexIndex").
          isGreaterThan(result.indexOf("1 plugin common"));
        requireThat(result.indexOf("3 project common"), "projectCommonIndex").
          isGreaterThan(result.indexOf("2 plugin codex"));
        requireThat(result.indexOf("4 project codex"), "projectCodexIndex").
          isGreaterThan(result.indexOf("3 project common"));
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  private static void writeRule(Path path, String content) throws IOException
  {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
  }

  private static final class TestAgentPluginScope extends AbstractAgentScope implements AgentPluginScope
  {
    private final Path pluginRoot;
    private final List<Path> ruleDirectories;

    private TestAgentPluginScope(Path projectRoot, Path pluginRoot, List<Path> ruleDirectories)
    {
      super(projectRoot);
      this.pluginRoot = pluginRoot;
      this.ruleDirectories = List.copyOf(ruleDirectories);
    }

    @Override
    public Path getWorkDir()
    {
      ensureOpen();
      return getProjectPath();
    }

    @Override
    public TerminalType getTerminalType()
    {
      ensureOpen();
      return TerminalType.UNKNOWN;
    }

    @Override
    public String getTimezone()
    {
      ensureOpen();
      return "UTC";
    }

    @Override
    public Path getPluginRoot()
    {
      ensureOpen();
      return pluginRoot;
    }

    @Override
    public List<Path> getRuleDirectories()
    {
      ensureOpen();
      return ruleDirectories;
    }

    @Override
    public Path getPluginData()
    {
      ensureOpen();
      return pluginRoot;
    }

    @Override
    public String getPluginPrefix()
    {
      ensureOpen();
      return "cat";
    }

    @Override
    public Path getPluginDescriptor()
    {
      ensureOpen();
      return Path.of("plugin.json");
    }

    @Override
    public Path getPluginCacheDescriptor()
    {
      ensureOpen();
      return null;
    }
  }
}
