/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.common;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for runtime-specific agent wrapper layout.
 */
public final class RuntimeAgentLayoutTest
{
  /**
   * Verifies that every shared agent body has both Claude and Codex runtime definitions.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sharedAgentBodiesHaveRuntimeWrappers() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    Path sharedAgentsDir = repoRoot.resolve("plugin/agents/common");
    Path claudeAgentsDir = repoRoot.resolve("plugin/agents/claude");
    Path codexAgentsDir = repoRoot.resolve("plugin/agents/codex");

    try (Stream<Path> files = Files.list(sharedAgentsDir))
    {
      List<String> missing = files.
        filter(path -> path.getFileName().toString().endsWith(".md")).
        filter(path -> !path.getFileName().toString().equals("README.md")).
        map(path -> path.getFileName().toString()).
        filter(fileName ->
        {
          String tomlFileName = fileName.substring(0, fileName.length() - ".md".length()) + ".toml";
          return !Files.isRegularFile(claudeAgentsDir.resolve(fileName)) ||
            !Files.isRegularFile(codexAgentsDir.resolve(tomlFileName));
        }).
        toList();

      requireThat(missing, "missingRuntimeAgentWrappers").isEmpty();
    }
  }

  /**
   * Verifies that shared agent bodies do not carry runtime-specific frontmatter.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sharedAgentBodiesDoNotUseFrontmatter() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    Path sharedAgentsDir = repoRoot.resolve("plugin/agents/common");

    try (Stream<Path> files = Files.list(sharedAgentsDir))
    {
      List<Path> frontmatterFiles = files.
        filter(path -> path.getFileName().toString().endsWith(".md")).
        filter(path -> !path.getFileName().toString().equals("README.md")).
        filter(path ->
        {
          try
          {
            return Files.readString(path).startsWith("---\n");
          }
          catch (IOException e)
          {
            throw new AssertionError("Unable to read agent file: " + path, e);
          }
        }).
        toList();

      requireThat(frontmatterFiles, "frontmatterFiles").isEmpty();
    }
  }

  /**
   * Verifies that Codex custom agents use native TOML files and Codex model names.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void codexAgentDefinitionsUseTomlAndCodexModels() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    Path codexAgentsDir = repoRoot.resolve("plugin/agents/codex");

    try (Stream<Path> files = Files.list(codexAgentsDir))
    {
      List<Path> invalidAgentFiles = files.
        filter(path -> !path.getFileName().toString().equals("README.md")).
        filter(path ->
        {
          try
          {
            String content = Files.readString(path);
            return !path.getFileName().toString().endsWith(".toml") ||
              !content.contains("name = \"cat-") ||
              !content.contains("developer_instructions = '''\n") ||
              !content.contains("model = \"gpt-5.5\"\n") &&
                !content.contains("model = \"gpt-5.3-codex\"\n") &&
                !content.contains("model = \"gpt-5.4-mini\"\n") ||
              content.contains("model = \"opus\"\n") ||
              content.contains("model = \"sonnet\"\n") ||
              content.contains("model = \"haiku\"\n") ||
              content.contains("model = \"claude-");
          }
          catch (IOException e)
          {
            throw new AssertionError("Unable to read Codex agent file: " + path, e);
          }
        }).
        toList();

      requireThat(invalidAgentFiles, "invalidAgentFiles").isEmpty();
    }
  }

  /**
   * Verifies that Codex-specific skills use Codex model names, not Claude model aliases.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void codexSpecificSkillsUseCodexModels() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    Path codexSkillsDir = repoRoot.resolve("plugin/skills/codex");

    try (Stream<Path> files = Files.walk(codexSkillsDir))
    {
      List<Path> filesWithClaudeModels = files.
        filter(path -> path.getFileName().toString().equals("SKILL.md")).
        filter(path ->
        {
          try
          {
            String content = Files.readString(path);
            return !content.contains("model: gpt-5.5\n") &&
              !content.contains("model: gpt-5.3-codex\n") &&
              !content.contains("model: gpt-5.4-mini\n") ||
              content.contains("model: opus\n") ||
              content.contains("model: sonnet\n") ||
              content.contains("model: haiku\n") ||
              content.contains("model: claude-");
          }
          catch (IOException e)
          {
            throw new AssertionError("Unable to read Codex skill file: " + path, e);
          }
        }).
        toList();

      requireThat(filesWithClaudeModels, "filesWithClaudeModels").isEmpty();
    }
  }

  /**
   * Verifies that {@code /cat:init} does not duplicate Codex custom agent installation.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void initDoesNotInstallCodexAgents() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    Path initInstructions = repoRoot.resolve("plugin/skills/codex/init/first-use.md");
    String content = Files.readString(initInstructions);

    requireThat(content, "content").doesNotContain("install_codex_subagents");
    requireThat(content, "content").doesNotContain("rm -f .codex/agents/cat-*.toml");
    requireThat(content, "content").doesNotContain(
      "Non-Codex runtime detected; skipping Codex custom agent installation");
  }

  /**
   * Verifies that Codex custom agent copy installation lives in the migration script, not {@code /cat:init}.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void migrationCopiesCodexAgents() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    Path migrationScript = repoRoot.resolve("plugin/migrations/2.1.sh");
    String content = Files.readString(migrationScript);

    requireThat(content, "content").contains("Phase 29: Install/update Codex custom agent copies");
    requireThat(content, "content").contains("source_agents=\"${CLAUDE_PLUGIN_ROOT}/agents\"");
    requireThat(content, "content").contains("target_agents=\".codex/agents\"");
    requireThat(content, "content").contains("cp \"$source_agent\" \"$target_agent\"");
    requireThat(content, "content").doesNotContain("ln -s \"$source_agent\" \"$target_agent\"");
  }

  /**
   * Verifies that jlink AOT training receives the hook environment required by {@code MainClaudeHook}.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void jlinkAotTrainingSetsRequiredHookEnvironment() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    Path buildScript = repoRoot.resolve("cli/build-jlink.sh");
    String content = Files.readString(buildScript);

    requireThat(content, "content").contains("CLAUDE_PROJECT_DIR=\"$WORKSPACE_DIR\"");
    requireThat(content, "content").contains("CLAUDE_PLUGIN_ROOT=\"${REACTOR_DIR}/plugin\"");
    requireThat(content, "content").contains("CLAUDE_PLUGIN_DATA=\"$aot_plugin_data\"");
    requireThat(content, "content").contains("CLAUDE_CONFIG_DIR=\"$aot_config_dir\"");
  }
}
