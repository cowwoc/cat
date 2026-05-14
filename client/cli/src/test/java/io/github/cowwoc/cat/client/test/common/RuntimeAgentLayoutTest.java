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
