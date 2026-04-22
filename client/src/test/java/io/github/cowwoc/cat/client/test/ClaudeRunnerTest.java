/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.skills.ClaudeRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Tests for {@link ClaudeRunner}.
 */
public final class ClaudeRunnerTest
{
  /**
   * Verifies that createIsolatedConfig copies plugin source files into the cache directory.
   */
  @Test
  public void isolatedConfigCopiesPluginSource() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create a mock source config directory
      Path sourceConfig = tempDir.resolve("source-config");
      Files.createDirectories(sourceConfig);
      Files.writeString(sourceConfig.resolve("settings.json"), "{}");

      // Create a mock plugin source directory with a skill file
      Path pluginSource = tempDir.resolve("plugin");
      Path skillDir = pluginSource.resolve("skills").resolve("test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), "# Test Skill");
      Files.writeString(skillDir.resolve("first-use.md"), "# Instructions");

      // Create a mock jlink binary directory
      Path jlinkBin = tempDir.resolve("jlink-bin");
      Files.createDirectories(jlinkBin);
      Files.writeString(jlinkBin.resolve("test-tool"), "#!/bin/bash\necho hello");

      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        runner.createIsolatedConfig(sourceConfig, pluginSource, jlinkBin, "2.1");

        String isolatedDir = runner.getIsolatedConfigDir();
        requireThat(isolatedDir, "isolatedDir").isNotBlank();

        Path isolatedPath = Path.of(isolatedDir);

        // Verify original config file was copied
        requireThat(Files.exists(isolatedPath.resolve("settings.json")),
          "settingsCopied").isTrue();

        // Verify plugin source files were copied into the cache
        Path cacheSkillDir = isolatedPath.resolve("plugins").resolve("cache").
          resolve("cat").resolve("cat").resolve("2.1").
          resolve("skills").resolve("test-skill");
        requireThat(Files.exists(cacheSkillDir.resolve("SKILL.md")),
          "skillMdInCache").isTrue();
        requireThat(Files.exists(cacheSkillDir.resolve("first-use.md")),
          "firstUseMdInCache").isTrue();
        requireThat(Files.readString(cacheSkillDir.resolve("SKILL.md")),
          "skillMdContent").isEqualTo("# Test Skill");

        // Verify jlink binaries were copied
        Path cacheBinDir = isolatedPath.resolve("plugins").resolve("cache").
          resolve("cat").resolve("cat").resolve("2.1").
          resolve("client").resolve("bin");
        requireThat(Files.exists(cacheBinDir.resolve("test-tool")),
          "testToolInCache").isTrue();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that close() deletes the isolated config directory.
   */
  @Test
  public void closeDeletesIsolatedConfig() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sourceConfig = tempDir.resolve("source-config");
      Files.createDirectories(sourceConfig);
      Path pluginSource = tempDir.resolve("plugin");
      Files.createDirectories(pluginSource);
      Path jlinkBin = tempDir.resolve("jlink-bin");
      Files.createDirectories(jlinkBin);

      Path isolatedPath;
      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        runner.createIsolatedConfig(sourceConfig, pluginSource, jlinkBin, "2.1");
        isolatedPath = Path.of(runner.getIsolatedConfigDir());
        requireThat(Files.exists(isolatedPath), "existsBeforeClose").isTrue();
      }
      // After close(), the directory should be deleted
      requireThat(Files.exists(isolatedPath), "existsAfterClose").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that createIsolatedConfig replaces existing cache contents rather than merging.
   */
  @Test
  public void isolatedConfigReplacesExistingCache() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create source config with pre-existing cache
      Path sourceConfig = tempDir.resolve("source-config");
      Path existingCache = sourceConfig.resolve("plugins").resolve("cache").
        resolve("cat").resolve("cat").resolve("2.1");
      Files.createDirectories(existingCache);
      Files.writeString(existingCache.resolve("old-file.txt"), "stale content");

      // Create new plugin source (should replace the old cache)
      Path pluginSource = tempDir.resolve("plugin");
      Files.createDirectories(pluginSource);
      Files.writeString(pluginSource.resolve("new-file.txt"), "fresh content");

      Path jlinkBin = tempDir.resolve("jlink-bin");
      Files.createDirectories(jlinkBin);

      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        runner.createIsolatedConfig(sourceConfig, pluginSource, jlinkBin, "2.1");

        Path isolatedCache = Path.of(runner.getIsolatedConfigDir()).resolve("plugins").
          resolve("cache").resolve("cat").resolve("cat").resolve("2.1");

        // Old file should be gone (cache was replaced, not merged)
        requireThat(Files.exists(isolatedCache.resolve("old-file.txt")),
          "oldFileRemoved").isFalse();
        // New file should be present
        requireThat(Files.exists(isolatedCache.resolve("new-file.txt")),
          "newFilePresent").isTrue();
        requireThat(Files.readString(isolatedCache.resolve("new-file.txt")),
          "newFileContent").isEqualTo("fresh content");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws IOException when the --prompt-file file does not exist.
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = "(?s).*--prompt-file file not found.*")
  public void runThrowsWhenPromptFileNotFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      PrintStream out = new PrintStream(new ByteArrayOutputStream(), false, UTF_8);
      ClaudeRunner.run(scope,
        new String[]{"--prompt-file", tempDir.resolve("nonexistent-prompt.txt").toString()},
        out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() reads prompt content from the file specified by --prompt-file.
   * <p>
   * Confirms the file is read (no IOException) when it exists; the process launch itself
   * may fail if the Claude CLI binary is not available in the test environment,
   * but the file-read step succeeds.
   */
  @Test
  public void runReadsPromptFromFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path promptFile = tempDir.resolve("prompt.txt");
      Files.writeString(promptFile, "Hello from file", UTF_8);

      PrintStream out = new PrintStream(new ByteArrayOutputStream(), false, UTF_8);
      try
      {
        // The file exists and is readable — no IOException from file reading.
        // Process launch may fail if the Claude CLI binary is unavailable in the test environment.
        ClaudeRunner.run(scope, new String[]{"--prompt-file", promptFile.toString()}, out);
      }
      catch (IOException e)
      {
        // Process launch failures are acceptable; file-read failures are not.
        // Reject any IOException that originates from the --prompt-file file-read step.
        requireThat(e.getMessage(), "errorMessage").doesNotContain("--prompt-file file not found");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that getIsolatedConfigDir returns empty string when no isolation is configured.
   */
  @Test
  public void noIsolationReturnsEmptyConfigDir() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        requireThat(runner.getIsolatedConfigDir(), "configDir").isEmpty();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProcessBuilder sets CLAUDE_CONFIG_DIR when isolation is active.
   */
  @Test
  public void buildProcessBuilderSetsConfigDirWhenIsolated() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sourceConfig = tempDir.resolve("source-config");
      Files.createDirectories(sourceConfig);
      Path pluginSource = tempDir.resolve("plugin");
      Files.createDirectories(pluginSource);
      Path jlinkBin = tempDir.resolve("jlink-bin");
      Files.createDirectories(jlinkBin);

      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        runner.createIsolatedConfig(sourceConfig, pluginSource, jlinkBin, "2.1");
        String expectedConfigDir = runner.getIsolatedConfigDir();

        ProcessBuilder pb = runner.buildProcessBuilder(List.of("claude"), tempDir);
        requireThat(pb.environment().get("CLAUDE_CONFIG_DIR"), "CLAUDE_CONFIG_DIR").
          isEqualTo(expectedConfigDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProcessBuilder does NOT override CLAUDE_CONFIG_DIR when no isolation is active.
   * <p>
   * The env is inherited from the parent process unchanged; no isolation-specific value is injected.
   */
  @Test
  public void buildProcessBuilderDoesNotSetConfigDirWithoutIsolation() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        String parentValue = System.getenv("CLAUDE_CONFIG_DIR");

        ProcessBuilder pb = runner.buildProcessBuilder(List.of("claude"), tempDir);

        // The ProcessBuilder inherits the parent env; without isolation no override is injected.
        requireThat(pb.environment().get("CLAUDE_CONFIG_DIR"), "CLAUDE_CONFIG_DIR").
          isEqualTo(parentValue);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProcessBuilder sets CLAUDE_PLUGIN_ROOT to the isolated plugin cache path
   * when isolation is active.
   */
  @Test
  public void buildProcessBuilderSetsPluginRootWhenIsolated() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sourceConfig = tempDir.resolve("source-config");
      Files.createDirectories(sourceConfig);
      Path pluginSource = tempDir.resolve("plugin");
      Files.createDirectories(pluginSource);
      Path jlinkBin = tempDir.resolve("jlink-bin");
      Files.createDirectories(jlinkBin);

      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        runner.createIsolatedConfig(sourceConfig, pluginSource, jlinkBin, "2.1");
        String isolatedConfigDir = runner.getIsolatedConfigDir();

        ProcessBuilder pb = runner.buildProcessBuilder(List.of("claude"), tempDir);

        String expectedPluginRoot = Path.of(isolatedConfigDir).resolve("plugins").resolve("cache").
          resolve("cat").resolve("cat").resolve("2.1").toString();
        requireThat(pb.environment().get("CLAUDE_PLUGIN_ROOT"), "CLAUDE_PLUGIN_ROOT").
          isEqualTo(expectedPluginRoot);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProcessBuilder does NOT override CLAUDE_PLUGIN_ROOT when no isolation is active.
   * <p>
   * The env is inherited from the parent process unchanged; no isolation-specific value is injected.
   */
  @Test
  public void buildProcessBuilderDoesNotSetPluginRootWithoutIsolation() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        String parentValue = System.getenv("CLAUDE_PLUGIN_ROOT");

        ProcessBuilder pb = runner.buildProcessBuilder(List.of("claude"), tempDir);

        // The ProcessBuilder inherits the parent env; without isolation no override is injected.
        requireThat(pb.environment().get("CLAUDE_PLUGIN_ROOT"), "CLAUDE_PLUGIN_ROOT").
          isEqualTo(parentValue);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProcessBuilder always sets CLAUDE_PROJECT_DIR to the absolute cwd path,
   * even when no isolation is active. This ensures the runner process and its subagents resolve
   * relative file paths against the runner worktree rather than the parent's project directory.
   */
  @Test
  public void buildProcessBuilderSetsProjectDirToCwd() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        Path cwd = tempDir.resolve("runner-worktree");
        Files.createDirectories(cwd);

        ProcessBuilder pb = runner.buildProcessBuilder(List.of("claude"), cwd);

        requireThat(pb.environment().get("CLAUDE_PROJECT_DIR"), "CLAUDE_PROJECT_DIR").
          isEqualTo(cwd.toAbsolutePath().toString());
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that isCacheFixDetected returns false when NODE_OPTIONS is null.
   */
  @Test
  public void isCacheFixDetectedReturnsFalseWhenNodeOptionsIsNull()
  {
    requireThat(ClaudeRunner.isCacheFixDetected(null), "detected").isFalse();
  }

  /**
   * Verifies that isCacheFixDetected returns false when NODE_OPTIONS is empty.
   */
  @Test
  public void isCacheFixDetectedReturnsFalseWhenNodeOptionsIsEmpty()
  {
    requireThat(ClaudeRunner.isCacheFixDetected(""), "detected").isFalse();
  }

  /**
   * Verifies that isCacheFixDetected returns false when NODE_OPTIONS does not contain the
   * cache-fix import.
   */
  @Test
  public void isCacheFixDetectedReturnsFalseWhenImportAbsent()
  {
    String nodeOptions = "--experimental-modules --no-warnings";
    requireThat(ClaudeRunner.isCacheFixDetected(nodeOptions), "detected").isFalse();
  }

  /**
   * Verifies that isCacheFixDetected returns true when NODE_OPTIONS contains the cache-fix import.
   */
  @Test
  public void isCacheFixDetectedReturnsTrueWhenImportPresent()
  {
    String nodeOptions = "--import claude-code-cache-fix";
    requireThat(ClaudeRunner.isCacheFixDetected(nodeOptions), "detected").isTrue();
  }

  /**
   * Verifies that isCacheFixDetected returns true when NODE_OPTIONS contains the cache-fix import
   * along with other options.
   */
  @Test
  public void isCacheFixDetectedReturnsTrueWhenImportPresentWithOtherOptions()
  {
    String nodeOptions = "--experimental-modules --import claude-code-cache-fix --no-warnings";
    requireThat(ClaudeRunner.isCacheFixDetected(nodeOptions), "detected").isTrue();
  }

  /**
   * Verifies that isCacheFixDetected returns true when NODE_OPTIONS uses the full path form
   * of the import.
   */
  @Test
  public void isCacheFixDetectedReturnsTrueWhenImportUsesFullPath()
  {
    String nodeOptions = "--import /usr/local/lib/node_modules/claude-code-cache-fix/preload.mjs";
    requireThat(ClaudeRunner.isCacheFixDetected(nodeOptions), "detected").isTrue();
  }
}
