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

import static io.github.cowwoc.cat.claude.hook.skills.GetStatusOutput.NO_CAT_PROJECT_MESSAGE;
import static io.github.cowwoc.cat.claude.hook.skills.GetStatusOutput.NO_PLANNING_STRUCTURE_MESSAGE;
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

  /**
   * Verifies that {@code cat:status} uses static instructions instead of a preprocessor directive.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void statusSkillsDoNotUsePreprocessorDirective() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();

    for (String runtime : List.of("claude", "codex"))
    {
      Path statusSkill = repoRoot.resolve("plugin/skills/" + runtime + "/status/SKILL.md");
      String content = Files.readString(statusSkill);

      requireThat(content, runtime + "StatusContent").contains("client/bin/get-status-output");
      requireThat(content, runtime + "StatusContent").doesNotContain("!`");
      requireThat(content, runtime + "StatusContent").doesNotContain("client/bin/get-output\" status");
    }
  }

  /**
   * Verifies that {@code cat:status} assumes startup-loaded CAT environment guidance.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void statusSkillsAssumeStartupLoadedEnvironmentGuidance() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();

    for (String runtime : List.of("claude", "codex"))
    {
      Path statusSkill = repoRoot.resolve("plugin/skills/" + runtime + "/status/SKILL.md");
      String content = Files.readString(statusSkill);

      requireThat(content, runtime + "StatusContent").contains("CAT_PLUGIN_DATA");
      requireThat(content, runtime + "StatusContent").doesNotContain("CODEX_HOME=\"");
      requireThat(content, runtime + "StatusContent").doesNotContain("CAT_PLUGIN_DATA=\"");
      requireThat(content, runtime + "StatusContent").doesNotContain("CAT Environment Variables");
      requireThat(content, runtime + "StatusContent").doesNotContain("rules/codex/cat-environment.md");
    }
  }

  /**
   * Verifies that the jlink image exposes a direct launcher for status output.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void jlinkRegistersStatusOutputLauncher() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    Path buildScript = repoRoot.resolve("cli/build-jlink.sh");
    String content = Files.readString(buildScript);

    requireThat(content, "content").contains(
      "\"get-status-output:io.github.cowwoc.cat.claude.hook.skills.GetStatusOutput\"");
  }

  /**
   * Verifies that jlink verification executes the generated status output launcher.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void jlinkVerificationRunsStatusOutputLauncher() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    Path buildScript = repoRoot.resolve("cli/build-jlink.sh");
    String content = Files.readString(buildScript);

    requireThat(content, "content").contains("Testing get-status-output launcher");
    requireThat(content, "content").contains("\"${OUTPUT_DIR}/bin/get-status-output\"");
    requireThat(content, "content").contains("CLAUDE_PROJECT_DIR=\"$status_project_dir\"");
  }

  /**
   * Verifies that status enforcement delegates plain setup message recognition to status output code.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void statusEnforcementDelegatesPlainSetupMessageRecognition() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    String enforceStatusOutput = Files.readString(repoRoot.resolve(
      "cli/src/main/java/io/github/cowwoc/cat/claude/hook/EnforceStatusOutput.java"));

    requireThat(enforceStatusOutput, "enforceStatusOutput").contains("isPlainSetupStatusOutput(text)");
    requireThat(enforceStatusOutput, "enforceStatusOutput").doesNotContain(NO_CAT_PROJECT_MESSAGE);
    requireThat(enforceStatusOutput, "enforceStatusOutput").doesNotContain(NO_PLANNING_STRUCTURE_MESSAGE);
  }
}
