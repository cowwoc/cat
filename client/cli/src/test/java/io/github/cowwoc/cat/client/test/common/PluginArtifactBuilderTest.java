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
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
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
  public void buildFlattensRuntimeSpecificArtifacts() throws IOException, InterruptedException
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
      requireThat(claudeRoot.resolve("LICENSE.md"), "claudeLicense").isRegularFile();
      requireThat(codexRoot.resolve("LICENSE.md"), "codexLicense").isRegularFile();
      requireThat(Files.readString(claudeRoot.resolve("client/VERSION"), StandardCharsets.UTF_8),
        "claudeVersion").isEqualTo("1.2.3\n");
      requireThat(Files.readString(codexRoot.resolve("client/VERSION"), StandardCharsets.UTF_8),
        "codexVersion").isEqualTo("1.2.3\n");
      requireThat(Files.readString(claudeRoot.resolve(".claude-plugin/plugin.json"), StandardCharsets.UTF_8),
        "claudePluginJson").doesNotContain("\"commands\"");
      requireThat(Files.readString(codexRoot.resolve(".codex-plugin/plugin.json"), StandardCharsets.UTF_8),
        "codexPluginJson").doesNotContain("\"commands\"");
      requireThat(countSkillDirectories(claudeRoot), "claudeSkillDirectoryCount").isEqualTo(2L);
      requireThat(countSkillDirectories(codexRoot), "codexSkillDirectoryCount").isEqualTo(3L);
      requireThat(claudeRoot.resolve("skills/common-skill/SKILL.md"), "claudeCommonSkill").isRegularFile();
      requireThat(Files.exists(claudeRoot.resolve("skills/uninstall/SKILL.md")),
        "claudeDoesNotShipUninstallSkill").isFalse();
      requireThat(claudeRoot.resolve("skills/common-skill/first-use.md"), "claudeFirstUse").isRegularFile();
      requireThat(claudeRoot.resolve("skills/common-skill/helper.md"), "claudeReferencedCompanion").isRegularFile();
      requireThat(Files.exists(claudeRoot.resolve("skills/common-skill/testing.md")),
        "claudeDoesNotShipUnreferencedCompanion").isFalse();
      requireThat(claudeRoot.resolve("skills/claude-skill/SKILL.md"), "claudeSkill").isRegularFile();
      requireThat(Files.exists(claudeRoot.resolve("skills/codex-skill/SKILL.md")),
        "claudeDoesNotSeeCodexSkill").isFalse();
      requireThat(codexRoot.resolve("skills/common-skill/SKILL.md"), "codexCommonSkill").isRegularFile();
      requireThat(codexRoot.resolve("skills/uninstall/SKILL.md"), "codexUninstallSkill").isRegularFile();
      requireThat(codexRoot.resolve("skills/common-skill/first-use.md"), "codexFirstUse").isRegularFile();
      requireThat(codexRoot.resolve("skills/codex-skill/SKILL.md"), "codexSkill").isRegularFile();
      requireThat(Files.exists(codexRoot.resolve("skills/claude-skill/SKILL.md")),
        "codexDoesNotSeeClaudeSkill").isFalse();
      requireThat(Files.exists(claudeRoot.resolve("agents/common/agent.md")),
        "claudeDoesNotShipCommonAgentSources").isFalse();
      requireThat(Files.exists(codexRoot.resolve("agents/common/agent.md")),
        "codexDoesNotShipCommonAgentSources").isFalse();
      requireThat(codexRoot.resolve("agents/agent.toml"), "codexAgentToml").isRegularFile();
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
      requireThat(claudeRoot.resolve("hooks/hooks.json"), "claudeHookRegistration").isRegularFile();
      requireThat(codexRoot.resolve("hooks/hooks.json"), "codexHookRegistration").isRegularFile();
      requireThat(Files.readString(claudeRoot.resolve("client/bin/pre-read"), StandardCharsets.UTF_8),
        "claudePreReadLauncher").contains("io.github.cowwoc.cat.claude.hook.PreReadHook");
      requireThat(Files.exists(codexRoot.resolve("client/bin/pre-read")),
        "codexDoesNotShipClaudePreReadLauncher").isFalse();
      requireThat(Files.readString(codexRoot.resolve("client/bin/pre-bash"), StandardCharsets.UTF_8),
        "codexPreBashLauncher").contains("io.github.cowwoc.cat.codex.hook.PreBashHook");
      requireThat(Files.exists(codexRoot.resolve("client/bin/pre-write")),
        "codexDoesNotShipUnsupportedPreWriteLauncher").isFalse();
      String preBashOutput = runLauncher(codexRoot.resolve("client/bin/pre-bash"));
      requireThat(preBashOutput, "preBashOutput").contains(
        codexRoot.resolve("client/bin/../lib/server/aot-cache.aot").toString());
      requireThat(preBashOutput, "preBashOutput").contains(
        "io.github.cowwoc.cat.client/io.github.cowwoc.cat.codex.hook.PreBashHook");
      String sessionStartOutput = runLauncher(codexRoot.resolve("client/bin/session-start"));
      requireThat(sessionStartOutput, "sessionStartOutput").contains(
        codexRoot.resolve("client/bin/../lib/server/aot-cache.aot").toString());
      requireThat(sessionStartOutput, "sessionStartOutput").contains(
        "io.github.cowwoc.cat.client/io.github.cowwoc.cat.codex.hook.SessionStartHook");
      requireThat(Files.exists(claudeRoot.resolve("client/bin/claude")), "claudeLauncherVariantsRemoved").isFalse();
      requireThat(Files.exists(codexRoot.resolve("client/bin/codex")), "codexLauncherVariantsRemoved").isFalse();
      requireThat(claudeRoot.resolve("hooks/common/shared.sh"), "claudeCommonHook").isRegularFile();
      requireThat(codexRoot.resolve("hooks/common/shared.sh"), "codexCommonHook").isRegularFile();
      requireThat(claudeRoot.resolve("hooks/claude/session-start.sh"), "claudeSessionStart").isRegularFile();
      requireThat(Files.isExecutable(claudeRoot.resolve("hooks/claude/session-start.sh")),
        "claudeSessionStartExecutable").isTrue();
      requireThat(codexRoot.resolve("hooks/codex/session-start.sh"), "codexSessionStart").isRegularFile();
      requireThat(Files.isExecutable(codexRoot.resolve("hooks/codex/session-start.sh")),
        "codexSessionStartExecutable").isTrue();
      requireThat(Files.exists(claudeRoot.resolve("commands")), "claudeDoesNotShipCommands").isFalse();
      requireThat(Files.exists(codexRoot.resolve("commands")), "codexDoesNotShipCommands").isFalse();

      requireThat(claudeRoot.resolve("skills/claude-skill/SKILL.md"), "claudeSkillAfterGeneration").
        isRegularFile();

      Files.createDirectories(claudeRoot.resolve("skills/stale-skill"));
      Files.writeString(claudeRoot.resolve("skills/stale-skill/SKILL.md"), "stale\n", StandardCharsets.UTF_8);
      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();
      requireThat(Files.exists(claudeRoot.resolve("skills/stale-skill/SKILL.md")),
        "staleSkillRemoved").isFalse();
      requireThat(claudeRoot.resolve("skills/common-skill/SKILL.md"), "claudeCommonSkillAfterRebuild").
        isRegularFile();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that runtime-specific skill wrappers can share common skill bodies and companion files.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void buildRuntimeSkillWrappersIncludeCommonCompanions() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/runtime");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Path commonSkill = pluginDir.resolve("skills/common/common-skill");
      Files.delete(commonSkill.resolve("SKILL.md"));
      Files.createDirectories(pluginDir.resolve("skills/include"));
      Files.writeString(pluginDir.resolve("skills/include/common-skill.md"), MARKDOWN_LICENSE + "shared wrapper body\n",
        StandardCharsets.UTF_8);
      Files.createDirectories(pluginDir.resolve("skills/claude/common-skill"));
      Files.writeString(pluginDir.resolve("skills/claude/common-skill/SKILL.md"),
        MARKDOWN_LICENSE + "---\ndescription: Shared Claude skill\nmodel: haiku\neffort: low\n---\n" +
          "<!-- cat:include ../../include/common-skill.md -->\n",
        StandardCharsets.UTF_8);
      Files.createDirectories(pluginDir.resolve("skills/codex/common-skill"));
      Files.writeString(pluginDir.resolve("skills/codex/common-skill/SKILL.md"),
        MARKDOWN_LICENSE + "---\ndescription: Shared Codex skill\n---\n" +
          "<!-- cat:include ../../include/common-skill.md -->\n",
        StandardCharsets.UTF_8);

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      Path claudeSkill = targetDir.resolve("claude/skills/common-skill");
      Path codexSkill = targetDir.resolve("codex/skills/common-skill");
      requireThat(Files.readString(claudeSkill.resolve("SKILL.md"), StandardCharsets.UTF_8),
        "claudeSharedSkill").contains("shared wrapper body");
      requireThat(Files.readString(codexSkill.resolve("SKILL.md"), StandardCharsets.UTF_8),
        "codexSharedSkill").contains("shared wrapper body");
      requireThat(claudeSkill.resolve("first-use.md"), "claudeFirstUse").isRegularFile();
      requireThat(codexSkill.resolve("first-use.md"), "codexFirstUse").isRegularFile();
      requireThat(claudeSkill.resolve("helper.md"), "claudeHelper").isRegularFile();
      requireThat(codexSkill.resolve("helper.md"), "codexHelper").isRegularFile();
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
      Path claudeLibDir = clientDir.resolve("cli/target/jlink/claude/lib");
      Path codexLibDir = clientDir.resolve("cli/target/jlink/codex/lib");
      Files.createDirectories(claudeLibDir);
      Files.createDirectories(codexLibDir);
      Files.writeString(claudeLibDir.resolve("real.txt"), "safe\n", StandardCharsets.UTF_8);
      Files.writeString(codexLibDir.resolve("real.txt"), "safe\n", StandardCharsets.UTF_8);
      try
      {
        Files.createSymbolicLink(claudeLibDir.resolve("alias.txt"), Path.of("real.txt"));
        Files.createSymbolicLink(codexLibDir.resolve("alias.txt"), Path.of("real.txt"));
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
      Path claudeLibDir = clientDir.resolve("cli/target/jlink/claude/lib");
      Path codexLibDir = clientDir.resolve("cli/target/jlink/codex/lib");
      Files.createDirectories(claudeLibDir);
      Files.createDirectories(codexLibDir);
      Files.writeString(tempDir.resolve("outside.txt"), "unsafe\n", StandardCharsets.UTF_8);
      try
      {
        Files.createSymbolicLink(claudeLibDir.resolve("alias.txt"), tempDir.resolve("outside.txt"));
        Files.createSymbolicLink(codexLibDir.resolve("alias.txt"), tempDir.resolve("outside.txt"));
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

  /**
   * Verifies that Codex artifacts preserve path-scoped common rules without generating session stubs.
   */
  @Test
  public void buildDoesNotGenerateCodexStubsForPathScopedCommonRules() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try
    {
      Path repoRoot = tempDir.resolve("repo");
      Path clientDir = repoRoot.resolve("client");
      Path pluginDir = clientDir.resolve("plugin");
      Path targetDir = clientDir.resolve("distribution/target/runtime");
      createPluginSource(repoRoot, clientDir, pluginDir);
      Files.writeString(pluginDir.resolve("rules/common/java.md"), """
        ---
        paths: ["*.java"]
        ---
        # Java Conventions

        Use Allman braces.
        """, StandardCharsets.UTF_8);

      new PluginArtifactBuilder(pluginDir, clientDir, targetDir).build();

      requireThat(Files.exists(targetDir.resolve("codex/rules/codex/java.md")),
        "codexStubExists").isFalse();
      requireThat(Files.readString(targetDir.resolve("codex/rules/common/java.md"), StandardCharsets.UTF_8),
        "commonRuleContent").contains("paths: [\"*.java\"]");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Creates a minimal plugin source tree for runtime artifact tests.
   *
   * @param repoRoot the temporary repository root
   * @param clientDir the temporary client directory
   * @param pluginDir the temporary plugin directory
   * @throws IOException if file operations fail
   */
  private static void createPluginSource(Path repoRoot, Path clientDir, Path pluginDir)
    throws IOException
  {
    Files.createDirectories(clientDir);
    Files.writeString(repoRoot.resolve("LICENSE.md"), "license\n", StandardCharsets.UTF_8);
    for (String directory : new String[]{
      ".git-filter-repo-config", "concepts", "config", "lang", "migrations", "scripts",
      "templates", ".claude-plugin", ".codex-plugin", "rules/common", "rules/claude",
      "rules/codex", "hooks/common", "hooks/claude", "hooks/codex",
      "skills/common/common-skill", "skills/claude/claude-skill",
      "skills/codex/codex-skill", "skills/codex/uninstall", "agents/common", "agents/claude", "agents/codex"})
    {
      Files.createDirectories(pluginDir.resolve(directory));
    }
    Files.createDirectories(clientDir.resolve("cli/target/jlink/claude/bin"));
    Files.createDirectories(clientDir.resolve("cli/target/jlink/claude/lib/server"));
    Files.createDirectories(clientDir.resolve("cli/target/jlink/codex/bin"));
    Files.createDirectories(clientDir.resolve("cli/target/jlink/codex/lib/server"));

    Files.writeString(pluginDir.resolve(".claude-plugin/plugin.json"), "{\"version\":\"1.2.3\"}\n",
      StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve(".codex-plugin/plugin.json"),
      "{\"version\":\"1.2.3\"}\n",
      StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("emoji-widths.json"), "{}\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("package.json"), "{}\n", StandardCharsets.UTF_8);
    Files.writeString(pluginDir.resolve("package-lock.json"), "{}\n", StandardCharsets.UTF_8);
    Path javaLauncher = clientDir.resolve("cli/target/jlink/claude/bin/java");
    Files.writeString(javaLauncher, """
      #!/bin/sh
      printf '%s\\n' "$@"
      """, StandardCharsets.UTF_8);
    javaLauncher.toFile().setExecutable(true, false);
    Path codexJavaLauncher = clientDir.resolve("cli/target/jlink/codex/bin/java");
    Files.writeString(codexJavaLauncher, """
      #!/bin/sh
      printf '%s\\n' "$@"
      """, StandardCharsets.UTF_8);
    codexJavaLauncher.toFile().setExecutable(true, false);
    Files.writeString(clientDir.resolve("cli/target/jlink/claude/bin/tool"), "tool\n", StandardCharsets.UTF_8);
    Files.writeString(clientDir.resolve("cli/target/jlink/codex/bin/tool"), "tool\n", StandardCharsets.UTF_8);
    Files.writeString(clientDir.resolve("cli/target/jlink/claude/bin/pre-read"),
      "io.github.cowwoc.cat.claude.hook.PreReadHook\n", StandardCharsets.UTF_8);
    writeRuntimeLauncher(clientDir.resolve("cli/target/jlink/codex/bin/pre-bash"),
      "io.github.cowwoc.cat.codex.hook.PreBashHook");
    writeRuntimeLauncher(clientDir.resolve("cli/target/jlink/codex/bin/session-start"),
      "io.github.cowwoc.cat.codex.hook.SessionStartHook");
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

  /**
   * Counts the skill directories in a runtime artifact.
   *
   * @param runtimeRoot the runtime artifact root
   * @return the number of skill directories
   * @throws IOException if file operations fail
   */
  private static long countSkillDirectories(Path runtimeRoot) throws IOException
  {
    try (Stream<Path> skills = Files.list(runtimeRoot.resolve("skills")))
    {
      return skills.filter(Files::isDirectory).count();
    }
  }

  /**
   * Writes a launcher script that mimics a jlink-generated runtime launcher.
   *
   * @param launcher the launcher path
   * @param className the module main class
   * @throws IOException if the launcher cannot be written
   */
  private static void writeRuntimeLauncher(Path launcher, String className) throws IOException
  {
    Files.writeString(launcher, """
      #!/bin/sh
      DIR=`dirname $0`
      exec "$DIR/java" \\
        -XX:AOTCache="$DIR/../lib/server/aot-cache.aot" \\
        -m io.github.cowwoc.cat.client/%s "$@"
      """.formatted(className), StandardCharsets.UTF_8);
    launcher.toFile().setExecutable(true, false);
  }

  /**
   * Runs a generated launcher and returns its output.
   *
   * @param launcher the launcher to run
   * @return the launcher output
   * @throws IOException if the launcher cannot be started or read
   * @throws InterruptedException if interrupted while waiting for the launcher
   */
  private static String runLauncher(Path launcher) throws IOException, InterruptedException
  {
    try (Process process = new ProcessBuilder(launcher.toString()).redirectErrorStream(true).start())
    {
      boolean completed = process.waitFor(10, TimeUnit.SECONDS);
      if (!completed)
      {
        process.destroyForcibly();
        throw new AssertionError("Timed out running launcher: " + launcher);
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      requireThat(process.exitValue(), "launcherExitCode").isEqualTo(0);
      return output;
    }
  }
}
