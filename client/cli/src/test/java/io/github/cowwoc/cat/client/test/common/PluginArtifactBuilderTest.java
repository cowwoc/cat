/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.common;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.client.test.TestUtils;
import io.github.cowwoc.cat.agent.PluginArtifactBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Tests for {@link PluginArtifactBuilder}.
 */
public final class PluginArtifactBuilderTest
{
  private static final String MARKDOWN_LICENSE = """
    <!--
    Copyright (c) 2026 Gili Tzabari. All rights reserved.
    Licensed under the CAT Commercial License.
    See LICENSE.md in the project root for license terms.
    -->
    """;

  /**
   * Verifies that release artifacts contain only runtime-specific files and copied license terms.
   */
  @Test
  public void buildFlattensRuntimeSpecificArtifacts() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/runtime");
      createPluginSource(repoRoot, clientDir, pluginDir);

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      Path claudeRoot = targetDir.resolve("claude");
      Path codexRoot = targetDir.resolve("codex");
      requireThat(Files.isRegularFile(claudeRoot.resolve("LICENSE.md")), "claudeLicense").isTrue();
      requireThat(Files.isRegularFile(codexRoot.resolve("LICENSE.md")), "codexLicense").isTrue();
      requireThat(Files.readString(claudeRoot.resolve("client/VERSION"), StandardCharsets.UTF_8),
        "claudeVersion").isEqualTo("1.2.3\n");
      requireThat(Files.readString(codexRoot.resolve("client/VERSION"), StandardCharsets.UTF_8),
        "codexVersion").isEqualTo("1.2.3\n");
      requireThat(Files.isRegularFile(claudeRoot.resolve("skills/common-skill/SKILL.md")),
        "claudeCommonSkill").isTrue();
      requireThat(Files.isRegularFile(claudeRoot.resolve("skills/install/SKILL.md")),
        "claudeInstallSkill").isTrue();
      requireThat(Files.exists(claudeRoot.resolve("skills/uninstall/SKILL.md")),
        "claudeDoesNotShipUninstallSkill").isFalse();
      requireThat(Files.readString(claudeRoot.resolve("skills/common-skill/first-use.md"),
        StandardCharsets.UTF_8), "claudeFirstUse").contains("Read helper.md");
      requireThat(Files.readString(claudeRoot.resolve("skills/common-skill/first-use.md"),
        StandardCharsets.UTF_8), "claudeFirstUse").doesNotContain("Copyright (c) 2026");
      requireThat(Files.isRegularFile(claudeRoot.resolve("skills/common-skill/helper.md")),
        "claudeReferencedCompanion").isTrue();
      requireThat(Files.exists(claudeRoot.resolve("skills/common-skill/testing.md")),
        "claudeDoesNotShipUnreferencedCompanion").isFalse();
      requireThat(Files.isRegularFile(claudeRoot.resolve("skills/claude-skill/SKILL.md")),
        "claudeSkill").isTrue();
      requireThat(Files.exists(claudeRoot.resolve("skills/codex-skill/SKILL.md")),
        "claudeDoesNotSeeCodexSkill").isFalse();
      requireThat(Files.isRegularFile(codexRoot.resolve("skills/common-skill/SKILL.md")),
        "codexCommonSkill").isTrue();
      requireThat(Files.isRegularFile(codexRoot.resolve("skills/install/SKILL.md")),
        "codexInstallSkill").isTrue();
      requireThat(Files.isRegularFile(codexRoot.resolve("skills/uninstall/SKILL.md")),
        "codexUninstallSkill").isTrue();
      requireThat(Files.readString(codexRoot.resolve("skills/common-skill/first-use.md"),
        StandardCharsets.UTF_8), "codexFirstUse").contains("Read helper.md");
      requireThat(Files.readString(codexRoot.resolve("skills/common-skill/first-use.md"),
        StandardCharsets.UTF_8), "codexFirstUse").doesNotContain("Copyright (c) 2026");
      requireThat(Files.isRegularFile(codexRoot.resolve("skills/codex-skill/SKILL.md")),
        "codexSkill").isTrue();
      requireThat(Files.exists(codexRoot.resolve("skills/claude-skill/SKILL.md")),
        "codexDoesNotSeeClaudeSkill").isFalse();
      requireThat(Files.exists(claudeRoot.resolve("agents/common/agent.md")),
        "claudeDoesNotShipCommonAgentSources").isFalse();
      requireThat(Files.exists(codexRoot.resolve("agents/common/agent.md")),
        "codexDoesNotShipCommonAgentSources").isFalse();
      requireThat(Files.isRegularFile(codexRoot.resolve("agents/agent.toml")),
        "codexAgentToml").isTrue();
      String codexAgent = Files.readString(codexRoot.resolve("agents/agent.toml"), StandardCharsets.UTF_8);
      requireThat(codexAgent, "codexAgent").contains("name = \"agent\"");
      requireThat(codexAgent, "codexAgent").doesNotContain("Copyright (c) 2026");
      requireThat(Files.exists(claudeRoot.resolve("skills/common-skill/tests/test.bats")),
        "claudeDoesNotShipSkillTests").isFalse();
      requireThat(Files.exists(claudeRoot.resolve("skills/common-skill/instruction-test/case.md")),
        "claudeDoesNotShipInstructionTests").isFalse();
      requireThat(Files.exists(codexRoot.resolve("skills/common-skill/tests/test.bats")),
        "codexDoesNotShipSkillTests").isFalse();
      requireThat(Files.exists(codexRoot.resolve("skills/common-skill/instruction-test/case.md")),
        "codexDoesNotShipInstructionTests").isFalse();
      requireThat(Files.isRegularFile(claudeRoot.resolve("hooks/hooks.json")),
        "claudeHookRegistration").isTrue();
      requireThat(Files.isRegularFile(codexRoot.resolve("hooks/hooks.json")),
        "codexHookRegistration").isTrue();
      requireThat(Files.isRegularFile(claudeRoot.resolve("hooks/common/shared.sh")),
        "claudeCommonHook").isTrue();
      requireThat(Files.isRegularFile(codexRoot.resolve("hooks/common/shared.sh")),
        "codexCommonHook").isTrue();
      requireThat(Files.isRegularFile(claudeRoot.resolve("hooks/claude/session-start.sh")),
        "claudeSessionStart").isTrue();
      requireThat(Files.isExecutable(claudeRoot.resolve("hooks/claude/session-start.sh")),
        "claudeSessionStartExecutable").isTrue();
      requireThat(Files.isRegularFile(codexRoot.resolve("hooks/codex/session-start.sh")),
        "codexSessionStart").isTrue();
      requireThat(Files.isExecutable(codexRoot.resolve("hooks/codex/session-start.sh")),
        "codexSessionStartExecutable").isTrue();

      String skill = Files.readString(claudeRoot.resolve("skills/claude-skill/SKILL.md"),
        StandardCharsets.UTF_8);
      requireThat(skill, "skill").contains("shared body");
      requireThat(skill, "skill").doesNotContain("cat:include");
      requireThat(skill, "skill").doesNotContain("Copyright (c) 2026");

      Files.createDirectories(claudeRoot.resolve("skills/stale-skill"));
      Files.writeString(claudeRoot.resolve("skills/stale-skill/SKILL.md"), "stale\n", StandardCharsets.UTF_8);
      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      requireThat(Files.exists(claudeRoot.resolve("skills/stale-skill/SKILL.md")),
        "staleSkillRemoved").isFalse();
      requireThat(Files.isRegularFile(claudeRoot.resolve("skills/common-skill/SKILL.md")),
        "claudeCommonSkillAfterRebuild").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that symlinks are rejected instead of preserved in release artifacts.
   */
  @Test
  public void buildRejectsSourceSymlinks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/runtime");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path link = pluginDir.resolve("concepts/link.md");
      try
      {
        Files.createSymbolicLink(link, Path.of("shared-fragment.md"));
      }
      catch (UnsupportedOperationException | IOException e)
      {
        throw new SkipException("Symbolic links are not supported by this filesystem", e);
      }

      try
      {
        new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("symbolic link");
        return;
      }
      throw new AssertionError("Expected symbolic link rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that jlink symlinks are dereferenced when they point at files inside the jlink image.
   */
  @Test
  public void buildDereferencesSafeJlinkSymlinks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/runtime");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path libDir = clientDir.resolve("cli/target/jlink/lib");
      Files.createDirectories(libDir);
      Files.writeString(libDir.resolve("real.txt"), "safe\n", StandardCharsets.UTF_8);
      try
      {
        Files.createSymbolicLink(libDir.resolve("alias.txt"), Path.of("real.txt"));
      }
      catch (UnsupportedOperationException | IOException e)
      {
        throw new SkipException("Symbolic links are not supported by this filesystem", e);
      }

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      requireThat(Files.readString(targetDir.resolve("claude/client/lib/alias.txt"), StandardCharsets.UTF_8),
        "claudeDereferencedLink").isEqualTo("safe\n");
      requireThat(Files.readString(targetDir.resolve("codex/client/lib/alias.txt"), StandardCharsets.UTF_8),
        "codexDereferencedLink").isEqualTo("safe\n");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that jlink symlinks are rejected when they escape the jlink image.
   */
  @Test
  public void buildRejectsUnsafeJlinkSymlinks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/runtime");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path libDir = clientDir.resolve("cli/target/jlink/lib");
      Files.createDirectories(libDir);
      Files.writeString(tempDir.resolve("outside.txt"), "unsafe\n", StandardCharsets.UTF_8);
      try
      {
        Files.createSymbolicLink(libDir.resolve("alias.txt"), tempDir.resolve("outside.txt"));
      }
      catch (UnsupportedOperationException | IOException e)
      {
        throw new SkipException("Symbolic links are not supported by this filesystem", e);
      }

      try
      {
        new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("unsafe jlink symbolic link");
        return;
      }
      throw new AssertionError("Expected unsafe jlink symbolic link rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that include expansion cannot cross runtime boundaries.
   */
  @Test
  public void buildRejectsCrossRuntimeIncludeTargets() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/runtime");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Files.writeString(pluginDir.resolve("agents/claude/agent.md"),
        MARKDOWN_LICENSE + "claude agent\n<!-- cat:include ../codex/agent.toml -->",
        StandardCharsets.UTF_8);

      try
      {
        new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      }
      catch (IllegalStateException e)
      {
        requireThat(e.getMessage(), "message").contains("not allowed");
        return;
      }
      throw new AssertionError("Expected cross-runtime cat:include rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that include expansion cannot pull source-only skill fixtures into the runtime artifact.
   */
  @Test
  public void buildRejectsSourceOnlyIncludeTargets() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/runtime");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Files.writeString(pluginDir.resolve("agents/claude/agent.md"),
        MARKDOWN_LICENSE + "claude agent\n<!-- cat:include ../../skills/common/common-skill/tests/test.bats -->",
        StandardCharsets.UTF_8);

      try
      {
        new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      }
      catch (IllegalStateException e)
      {
        requireThat(e.getMessage(), "message").contains("not allowed");
        return;
      }
      throw new AssertionError("Expected source-only cat:include rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that duplicate runtime companion basenames are rejected instead of guessed.
   */
  @Test
  public void buildRejectsDuplicateRuntimeSkillCompanionNames() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/runtime");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path nested = pluginDir.resolve("skills/common/common-skill/nested");
      Files.createDirectories(nested);
      Files.writeString(nested.resolve("helper.md"), MARKDOWN_LICENSE + "ambiguous helper\n",
        StandardCharsets.UTF_8);

      try
      {
        new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      }
      catch (IllegalStateException e)
      {
        requireThat(e.getMessage(), "message").contains("Duplicate runtime skill companion filename");
        return;
      }
      throw new AssertionError("Expected duplicate runtime skill companion filename rejection");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  private static void createPluginSource(Path repoRoot, Path clientDir, Path pluginDir)
    throws IOException
  {
    Files.createDirectories(clientDir);
    Files.writeString(repoRoot.resolve("LICENSE.md"), "license\n", StandardCharsets.UTF_8);
    for (String directory : new String[]{
      ".git-filter-repo-config", "concepts", "config", "lang", "migrations", "scripts",
      "templates", ".claude-plugin", ".codex-plugin", "rules/common", "rules/claude",
      "rules/codex", "hooks/common", "hooks/claude", "hooks/codex",
      "skills/common/common-skill", "skills/common/install", "skills/claude/claude-skill",
      "skills/codex/codex-skill", "skills/codex/uninstall", "agents/common", "agents/claude", "agents/codex"})
    {
      Files.createDirectories(pluginDir.resolve(directory));
    }
    Files.createDirectories(clientDir.resolve("cli/target/jlink/bin"));

    Files.writeString(pluginDir.resolve(".claude-plugin/plugin.json"), "{\"version\":\"1.2.3\"}\n",
      StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve(".codex-plugin/plugin.json"), "{\"version\":\"1.2.3\"}\n",
      StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("emoji-widths.json"), "{}\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("package.json"), "{}\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("package-lock.json"), "{}\n", StandardCharsets.UTF_8);
    Files.writeString(clientDir.resolve("cli/target/jlink/bin/tool"), "tool\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("hooks/claude/hooks.json"), "{}\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("hooks/codex/hooks.json"), "{}\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("hooks/common/shared.sh"), """
      #!/usr/bin/env bash
      # Copyright (c) 2026 Gili Tzabari. All rights reserved.
      #
      # Licensed under the CAT Commercial License.
      # See LICENSE.md in the project root for license terms.
      exit 0
      """, StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("hooks/claude/session-start.sh"), """
      #!/usr/bin/env bash
      # Copyright (c) 2026 Gili Tzabari. All rights reserved.
      #
      # Licensed under the CAT Commercial License.
      # See LICENSE.md in the project root for license terms.
      exit 0
      """, StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("hooks/codex/session-start.sh"), """
      #!/usr/bin/env bash
      # Copyright (c) 2026 Gili Tzabari. All rights reserved.
      #
      # Licensed under the CAT Commercial License.
      # See LICENSE.md in the project root for license terms.
      exit 0
      """, StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("concepts/shared-fragment.md"), MARKDOWN_LICENSE +
      "shared body\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/common/common-skill/SKILL.md"),
      MARKDOWN_LICENSE + "common body\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/common/common-skill/first-use.md"),
      MARKDOWN_LICENSE + "Read helper.md\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/common/common-skill/helper.md"),
      MARKDOWN_LICENSE + "helper body\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/common/install/SKILL.md"),
      MARKDOWN_LICENSE + "install release artifact\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/common/common-skill/testing.md"),
      MARKDOWN_LICENSE + "authoring-only test guidance\n", StandardCharsets.UTF_8);
    Files.createDirectories(pluginDir.resolve("skills/common/common-skill/tests"));
    Files.writeString(pluginDir.resolve("skills/common/common-skill/tests/test.bats"),
      "#!/usr/bin/env bats\n", StandardCharsets.UTF_8);
    Files.createDirectories(pluginDir.resolve("skills/common/common-skill/instruction-test"));
    Files.writeString(pluginDir.resolve("skills/common/common-skill/instruction-test/case.md"),
      "source-only test case\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/claude/claude-skill/SKILL.md"),
      MARKDOWN_LICENSE + "claude body\n<!-- cat:include ../../../concepts/shared-fragment.md -->",
      StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/codex/codex-skill/SKILL.md"),
      MARKDOWN_LICENSE + "codex body\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("skills/codex/uninstall/SKILL.md"),
      MARKDOWN_LICENSE + "codex uninstall body\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("agents/common/agent.md"), MARKDOWN_LICENSE +
      "common agent\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("agents/claude/agent.md"), MARKDOWN_LICENSE +
      "claude agent\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("agents/codex/agent.toml"),
      "# Copyright (c) 2026 Gili Tzabari. All rights reserved.\n" +
        "#\n" +
        "# Licensed under the CAT Commercial License.\n" +
        "# See LICENSE.md in the project root for license terms.\n" +
        "name = \"agent\"\n", StandardCharsets.UTF_8);
  }
}
