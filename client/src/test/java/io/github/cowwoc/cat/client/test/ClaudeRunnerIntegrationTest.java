/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.skills.ClaudeRunner;
import io.github.cowwoc.cat.claude.hook.skills.ClaudeRunner.ProcessResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Integration tests for {@link ClaudeRunner} that launch actual Claude CLI processes.
 * <p>
 * Skipped by default. Run with {@code -Dintegration=true}:
 * <pre>
 * mvn -f client/pom.xml test -Dintegration=true -Dtest=ClaudeRunnerIntegrationTest
 * </pre>
 */
public final class ClaudeRunnerIntegrationTest
{
  /**
   * Skips the test unless {@code -Dintegration=true} is set.
   */
  private static void requireIntegrationMode()
  {
    if (!"true".equals(System.getProperty("integration")))
      throw new SkipException("Integration tests disabled. Run with -Dintegration=true");
  }

  /**
   * Verifies that a nested Claude instance sees updated plugin cache files.
   * <p>
   * Creates an isolated config with a custom plugin file, launches a nested Claude instance,
   * and asks it to read the plugin file. The response should contain the custom content,
   * proving the nested instance loaded the updated cache.
   */
  @Test(timeOut = 120_000)
  public void nestedInstanceSeesUpdatedPluginCache() throws IOException
  {
    requireIntegrationMode();
    Path tempDir = Files.createTempDirectory("integration-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create a source config directory (minimal — just needs to exist)
      Path sourceConfig = tempDir.resolve("source-config");
      Files.createDirectories(sourceConfig);

      // Create a plugin source with a recognizable marker file
      Path pluginSource = tempDir.resolve("plugin");
      Files.createDirectories(pluginSource.resolve("skills").resolve("test-marker"));
      Files.writeString(pluginSource.resolve("skills").resolve("test-marker").resolve("SKILL.md"),
        """
        ---
        description: "Marker skill for integration test"
        user-invocable: false
        ---
        # INTEGRATION_TEST_MARKER_7f3a9b2e
        """);

      // Use the real jlink binaries from the project
      Path jlinkBin = findJlinkBinDir();

      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        runner.createIsolatedConfig(sourceConfig, pluginSource, jlinkBin, "2.1");

        // Ask the nested instance to list files in its plugin cache
        List<String> command = runner.buildCommand("haiku", "");
        String input = runner.buildInput(List.of(),
          List.of("Use the Bash tool to run: find $CLAUDE_PLUGIN_ROOT/skills -name 'SKILL.md' " +
            "| head -5. Then read the contents of any SKILL.md file you find that contains " +
            "'test-marker' in its path. Report what you find."),
          List.of());

        ProcessResult result = runner.executeProcess(command, input, tempDir);

        requireThat(result.error(), "error").isEmpty();

        String fullText = String.join("\n", result.parsed().texts());

        // The nested instance should have found and read our marker file
        requireThat(fullText, "output").contains("INTEGRATION_TEST_MARKER_7f3a9b2e");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a nested Claude instance can spawn subagents via the Agent tool.
   * <p>
   * Launches a nested Claude instance and asks it to spawn an Agent subagent. The response
   * should show that the Agent tool was used, proving the nested instance supports full
   * tool access including subagent spawning.
   */
  @Test(timeOut = 120_000)
  public void nestedInstanceCanSpawnSubagent() throws IOException
  {
    requireIntegrationMode();
    Path tempDir = Files.createTempDirectory("integration-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try (ClaudeRunner runner = new ClaudeRunner(scope))
      {
        List<String> command = runner.buildCommand("haiku", "");
        String input = runner.buildInput(List.of(),
          List.of("Use the Agent tool to spawn a subagent with this prompt: " +
            "'Reply with exactly the text: SUBAGENT_SPAWNED_OK_c4e8f1a3'. " +
            "Report the subagent's response."),
          List.of());

        ProcessResult result = runner.executeProcess(command, input, tempDir);

        requireThat(result.error(), "error").isEmpty();

        // The Agent tool should appear in the tool uses
        requireThat(result.parsed().toolUses(), "toolUses").contains("Agent");

        // The subagent's marker text should appear in the output
        String fullText = String.join("\n", result.parsed().texts());
        requireThat(fullText, "output").contains("SUBAGENT_SPAWNED_OK_c4e8f1a3");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Finds the jlink binary directory, checking the worktree first then the main project.
   *
   * @return the path to the jlink bin directory
   * @throws IOException if no jlink bin directory is found
   */
  private static Path findJlinkBinDir() throws IOException
  {
    // Check worktree build output first
    Path worktreeBin = Path.of("client/target/jlink/bin");
    if (Files.isDirectory(worktreeBin))
      return worktreeBin.toAbsolutePath();

    // Check main project build output
    String projectDir = System.getenv("CLAUDE_PROJECT_DIR");
    if (projectDir != null)
    {
      Path mainBin = Path.of(projectDir, "client/target/jlink/bin");
      if (Files.isDirectory(mainBin))
        return mainBin;
    }

    // Check plugin cache (where jlink binaries are deployed)
    String pluginRoot = System.getenv("CLAUDE_PLUGIN_ROOT");
    if (pluginRoot != null)
    {
      Path cacheBin = Path.of(pluginRoot, "client/bin");
      if (Files.isDirectory(cacheBin))
        return cacheBin;
    }

    throw new IOException("Cannot find jlink bin directory. Run 'client/build-jlink.sh' first.");
  }
}
