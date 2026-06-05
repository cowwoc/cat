/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.MainCliTool;
import io.github.cowwoc.cat.tool.skills.SharedSecrets;
import io.github.cowwoc.cat.tool.skills.SprtRunner;
import io.github.cowwoc.cat.agent.AgentEngine;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests SPRT trial preparation, multi-turn session handling, and engine-specific argument shapes.
 * <p>
 * Each test is self-contained with no shared state.
 */
public final class SprtRunnerTrialEngineTest
{
  /**
   * Verifies that prepare-trial writes a prompt file containing the turn content from the
   * isolation branch and returns key=value output with prompt_file, jlink_bin, and output_json.
   */
  @Test
  public void prepareTrialReadsTurnContent() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    Path runnerWorktree = Files.createTempDirectory("runner-");
    Path outputDir = Files.createTempDirectory("test-output-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Files.createDirectories(runnerWorktree.resolve("client/distribution/target/jlink/claude/bin"));
      Files.createDirectories(tempDir.resolve(".claude-plugin"));
      Files.writeString(tempDir.resolve(".claude-plugin/plugin.json"),
        "{\"version\":\"2.1.87\"}", StandardCharsets.UTF_8);
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/sample-test_turn1.md"),
        "test turn content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-isolation");

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-isolation", "plugin/tests/myskill",
        "sample-test", runnerWorktree.toString(), outputDir.toString(), "1"});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      requireThat(pairs.get("prompt_file"), "prompt_file").isNotBlank();
      String promptContent = Files.readString(Path.of(pairs.get("prompt_file")),
        StandardCharsets.UTF_8);
      requireThat(promptContent, "promptContent").contains("test turn content");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(runnerWorktree);
      TestUtils.deleteDirectoryRecursively(outputDir);
    }
  }

  /**
   * Verifies that prepare-trial returns all extracted prompt files for a multi-turn testcase.
   */
  @Test
  public void prepareTrialReturnsMultiTurnPrompts() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    Path runnerWorktree = Files.createTempDirectory("runner-");
    Path outputDir = Files.createTempDirectory("test-output-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Files.createDirectories(runnerWorktree.resolve("client/distribution/target/jlink/claude/bin"));
      Files.createDirectories(tempDir.resolve(".claude-plugin"));
      Files.writeString(tempDir.resolve(".claude-plugin/plugin.json"),
        "{\"version\":\"2.1.87\"}", StandardCharsets.UTF_8);
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/sample-test_turn1.md"),
        "turn one content", StandardCharsets.UTF_8);
      Files.writeString(repoDir.resolve("plugin/tests/myskill/sample-test_turn2.md"),
        "turn two content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-isolation");

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-isolation", "plugin/tests/myskill",
        "sample-test", runnerWorktree.toString(), outputDir.toString(), "2"});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      JsonNode promptFiles = JsonMapper.builder().build().readTree(pairs.get("prompt_files_json"));
      requireThat(promptFiles.size(), "promptFiles.size").isEqualTo(2);
      String prompt1 = Files.readString(Path.of(promptFiles.get(0).stringValue()),
        StandardCharsets.UTF_8);
      String prompt2 = Files.readString(Path.of(promptFiles.get(1).stringValue()),
        StandardCharsets.UTF_8);
      requireThat(prompt1, "prompt1").contains("turn one content");
      requireThat(prompt2, "prompt2").contains("turn two content");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(runnerWorktree);
      TestUtils.deleteDirectoryRecursively(outputDir);
    }
  }

  /**
   * Verifies that prepare-trial fails fast when the runner worktree does not have a jlink directory.
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*jlink directory not found in runner worktree.*")
  public void prepareTrialFailsWhenJlinkMissing() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    Path runnerWorktree = Files.createTempDirectory("runner-");
    Path outputDir = Files.createTempDirectory("test-output-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/sample-test_turn1.md"),
        "content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-isolation");

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-isolation", "plugin/tests/myskill",
        "sample-test", runnerWorktree.toString(), outputDir.toString(), "1"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(runnerWorktree);
      TestUtils.deleteDirectoryRecursively(outputDir);
    }
  }

  /**
   * Verifies that prepare-trial uses the runner worktree jlink bin when it exists.
   */
  @Test
  public void prepareTrialUsesRunnerJlinkBin() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    Path runnerWorktree = Files.createTempDirectory("runner-");
    Path claudeProjectDir = Files.createTempDirectory("project-");
    Path outputDir = Files.createTempDirectory("test-output-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/sample-test_turn1.md"),
        "content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-isolation");

      // Create jlink/bin dir in runner worktree
      Files.createDirectories(runnerWorktree.resolve("client/distribution/target/jlink/claude/bin"));

      // prepareTrial writes a VERSION file to the jlink dir using the plugin version from plugin.json
      Files.createDirectories(tempDir.resolve(".claude-plugin"));
      Files.writeString(tempDir.resolve(".claude-plugin/plugin.json"),
        "{\"version\":\"2.1.87\"}", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-isolation", "plugin/tests/myskill",
        "sample-test", runnerWorktree.toString(), outputDir.toString(), "1"});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      requireThat(pairs.get("jlink_bin"), "jlink_bin").startsWith(runnerWorktree.toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(runnerWorktree);
      TestUtils.deleteDirectoryRecursively(claudeProjectDir);
      TestUtils.deleteDirectoryRecursively(outputDir);
    }
  }

  /**
   * Verifies that prepare-trial writes a prompt file whose content contains the preamble with
   * the CWD tag, the positive path mandate, a concrete example, and the mandatory execution
   * instruction.
   */
  @Test
  public void prepareTrialConstructsPreamble() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    Path runnerWorktree = Files.createTempDirectory("runner-");
    Path outputDir = Files.createTempDirectory("test-output-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Files.createDirectories(runnerWorktree.resolve("client/distribution/target/jlink/claude/bin"));
      Files.createDirectories(tempDir.resolve(".claude-plugin"));
      Files.writeString(tempDir.resolve(".claude-plugin/plugin.json"),
        "{\"version\":\"2.1.87\"}", StandardCharsets.UTF_8);
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/sample-test_turn1.md"),
        "content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-isolation");

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-isolation", "plugin/tests/myskill",
        "sample-test", runnerWorktree.toString(), outputDir.toString(), "1"});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      String promptContent = Files.readString(Path.of(pairs.get("prompt_file")),
        StandardCharsets.UTF_8);
      requireThat(promptContent, "promptContent").contains("[CWD: " + runnerWorktree + "]");
      // Positive mandate: every path MUST begin with the CWD value
      requireThat(promptContent, "promptContent").contains(
        "Every path argument passed to Write, Edit, or Bash MUST begin with the exact CWD value above");
      // Concrete example anchors the correct construction pattern
      requireThat(promptContent, "promptContent").contains(runnerWorktree + "/");
      // Mandatory execution instruction must still be present
      requireThat(promptContent, "promptContent").contains("Execute the task below immediately");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(runnerWorktree);
      TestUtils.deleteDirectoryRecursively(outputDir);
    }
  }

  /**
   * Verifies that prepare-trial constructs the output_json path from output_dir, tc_id, and
   * trial_num and returns it via key=value output.
   */
  @Test
  public void prepareTrialConstructsOutputJson() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    Path runnerWorktree = Files.createTempDirectory("runner-");
    Path outputDir = Files.createTempDirectory("test-output-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Files.createDirectories(runnerWorktree.resolve("client/distribution/target/jlink/claude/bin"));
      Files.createDirectories(tempDir.resolve(".claude-plugin"));
      Files.writeString(tempDir.resolve(".claude-plugin/plugin.json"),
        "{\"version\":\"2.1.87\"}", StandardCharsets.UTF_8);
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/sample-test_turn1.md"),
        "content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-isolation");

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-isolation", "plugin/tests/myskill",
        "sample-test", runnerWorktree.toString(), outputDir.toString(), "3"});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      requireThat(pairs.get("output_json"), "output_json").
        isEqualTo(outputDir + "/sample-test_run3.json");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(runnerWorktree);
      TestUtils.deleteDirectoryRecursively(outputDir);
    }
  }

  /**
   * Verifies that prepare-trial returns plugin_source pointing to the runner worktree's plugin
   * directory so that claude-runner uses the committed plugin version from the isolation branch
   * instead of the globally installed plugin cache.
   */
  @Test
  public void prepareTrialReturnsPluginSource() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    Path runnerWorktree = Files.createTempDirectory("runner-");
    Path outputDir = Files.createTempDirectory("test-output-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Files.createDirectories(runnerWorktree.resolve("client/distribution/target/jlink/claude/bin"));
      Files.createDirectories(tempDir.resolve(".claude-plugin"));
      Files.writeString(tempDir.resolve(".claude-plugin/plugin.json"),
        "{\"version\":\"2.1.87\"}", StandardCharsets.UTF_8);
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/sample-test_turn1.md"),
        "content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-isolation");

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-isolation", "plugin/tests/myskill",
        "sample-test", runnerWorktree.toString(), outputDir.toString(), "1"});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      requireThat(pairs.get("plugin_source"), "plugin_source").
        isEqualTo(runnerWorktree + "/plugin/");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(runnerWorktree);
      TestUtils.deleteDirectoryRecursively(outputDir);
    }
  }

  /**
   * Verifies that Claude trial arguments include the session file for multi-turn execution.
   */
  @Test
  public void buildClaudeArgsIncludeSessionFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try
    {
      Path promptFile = tempDir.resolve("turn1.txt");
      Files.writeString(promptFile, "turn one", StandardCharsets.UTF_8);
      Path jlinkBin = tempDir.resolve("jlink-bin");
      Files.createDirectories(jlinkBin);
      Path outputJson = tempDir.resolve("output.json");
      Path sessionFile = tempDir.resolve("session.json");

      String[] args = SharedSecrets.buildClaudeSessionTrialArgs(promptFile, "claude-sonnet-4-5",
        "high", "/tmp/worktree", outputJson.toString(), jlinkBin, sessionFile);

      requireThat(List.of(args), "args").contains("--session-file", sessionFile.toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex trial arguments include the session file for multi-turn execution.
   */
  @Test
  public void buildCodexArgsIncludeSessionFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try
    {
      Path promptFile = tempDir.resolve("turn1.txt");
      Files.writeString(promptFile, "turn one", StandardCharsets.UTF_8);
      Path outputJson = tempDir.resolve("output.json");
      Path sessionFile = tempDir.resolve("output-session.json");

      String[] args = SharedSecrets.buildCodexSessionTrialArgs(promptFile, "gpt-5.5", "high",
        "/tmp/worktree", outputJson.toString(), sessionFile);

      requireThat(List.of(args), "args").contains("--session-file", sessionFile.toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multi-turn trial sessions delete stale state before starting and clean up after completion.
   */
  @Test
  public void multiTurnTrialCleansSessionFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path runnerWorktree = Files.createDirectories(tempDir.resolve("runner-worktree"));
      Path promptOne = tempDir.resolve("turn1.md");
      Path promptTwo = tempDir.resolve("turn2.md");
      Files.writeString(promptOne, "turn one", StandardCharsets.UTF_8);
      Files.writeString(promptTwo, "turn two", StandardCharsets.UTF_8);
      Path outputJson = tempDir.resolve("output.json");
      Path expectedSessionFile = runnerWorktree.resolve(".cat/work/output-session.json");
      Path launcher = runnerWorktree.resolve("client/distribution/target/jlink/claude/bin/claude-runner");
      Path launcherLog = tempDir.resolve("launcher.log");
      Files.createDirectories(launcher.getParent());
      Files.writeString(launcher, """
        #!/usr/bin/env bash
        set -euo pipefail
        if [[ "${1:-}" == "--help" ]]; then
          printf '%%s\n' "--plugin-source" "--jlink-bin" "--output" "--session-file"
          exit 0
        fi
        session_file=""
        for ((i=1; i<=$#; ++i)); do
          arg="${!i}"
          if [[ "$arg" == "--session-file" ]]; then
            next=$((i + 1))
            session_file="${!next}"
          fi
        done
        mkdir -p "$(dirname "$session_file")"
        if [[ -f "$session_file" ]]; then
          echo "exists=true:$session_file" >> "%s"
        else
          echo "exists=false:$session_file" >> "%s"
        fi
        printf 'active' > "$session_file"
        """.formatted(launcherLog, launcherLog), StandardCharsets.UTF_8);
      requireThat(launcher.toFile().setExecutable(true), "launcherExecutable").isTrue();
      Files.createDirectories(expectedSessionFile.getParent());
      Files.writeString(expectedSessionFile, "stale", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      int exitCode = SharedSecrets.runTrial(runner, List.of(promptOne, promptTwo),
        "claude-sonnet-4-5", "high", runnerWorktree.toString(), outputJson.toString(),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
      List<String> launcherRuns = Files.readAllLines(launcherLog, StandardCharsets.UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(0);
      requireThat(launcherRuns, "launcherRuns").isEqualTo(List.of(
        "exists=false:" + expectedSessionFile,
        "exists=true:" + expectedSessionFile));
      requireThat(Files.exists(expectedSessionFile), "sessionFileExistsAfter").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that later multi-turn runner launches actually observe session state from prior turns.
   */
  @Test
  public void multiTurnTrialReusesSessionState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path runnerWorktree = Files.createDirectories(tempDir.resolve("runner-worktree"));
      Path promptOne = tempDir.resolve("turn1.md");
      Path promptTwo = tempDir.resolve("turn2.md");
      Files.writeString(promptOne, "turn one", StandardCharsets.UTF_8);
      Files.writeString(promptTwo, "turn two", StandardCharsets.UTF_8);
      Path outputJson = tempDir.resolve("output.json");
      Path launcher = runnerWorktree.resolve("client/distribution/target/jlink/claude/bin/claude-runner");
      Files.createDirectories(launcher.getParent());
      Files.writeString(launcher, """
        #!/usr/bin/env bash
        set -euo pipefail
        if [[ "${1:-}" == "--help" ]]; then
          echo "--plugin-source --jlink-bin --output --session-file"
          exit 0
        fi
        prompt_file=""
        output_file=""
        session_file=""
        for ((i=1; i<=$#; ++i)); do
          arg="${!i}"
          if [[ "$arg" == "--prompt-file" ]]; then
            next=$((i + 1))
            prompt_file="${!next}"
          elif [[ "$arg" == "--output" ]]; then
            next=$((i + 1))
            output_file="${!next}"
          elif [[ "$arg" == "--session-file" ]]; then
            next=$((i + 1))
            session_file="${!next}"
          fi
        done
        if [[ "$prompt_file" == *"turn1.md" ]]; then
          mkdir -p "$(dirname "$session_file")"
          printf 'first-turn-state' > "$session_file"
          if [[ -n "$output_file" ]]; then
            exit 8
          fi
          exit 0
        fi
        if [[ ! -f "$session_file" ]] || [[ "$(<"$session_file")" != "first-turn-state" ]]; then
          exit 9
        fi
        printf '%s' \
          '{"texts":["second turn"],"toolUses":[],"writeContents":[],"turns":[],' \
          '"sessionId":"session-123"}' > "$output_file"
        exit 0
        """, StandardCharsets.UTF_8);
      requireThat(launcher.toFile().setExecutable(true), "launcherExecutable").isTrue();

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      int exitCode = SharedSecrets.runTrial(runner, List.of(promptOne, promptTwo),
        "claude-sonnet-4-5", "high", runnerWorktree.toString(), outputJson.toString(),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

      requireThat(exitCode, "exitCode").isEqualTo(0);
      JsonNode root = scope.getJsonMapper().readTree(outputJson.toFile());
      requireThat(root.path("texts").get(0).asString(), "finalText").isEqualTo("second turn");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex multi-turn trial sessions delete stale state before starting and clean up after completion.
   */
  @Test
  public void codexMultiTurnTrialCleansSessionFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new MainCliTool(name -> switch (name)
    {
      case "CAT_SESSION_ID" -> "test-session-id";
      case "CAT_PROJECT_DIR", "CAT_PLUGIN_ROOT", "CAT_PLUGIN_DATA", "CAT_CONFIG_DIR" ->
        tempDir.toString();
      case "CAT_ENGINE" -> "codex";
      default -> null;
    }, tempDir))
    {
      Path runnerWorktree = Files.createDirectories(tempDir.resolve("runner-worktree"));
      Path promptOne = tempDir.resolve("turn1.md");
      Path promptTwo = tempDir.resolve("turn2.md");
      Files.writeString(promptOne, "turn one", StandardCharsets.UTF_8);
      Files.writeString(promptTwo, "turn two", StandardCharsets.UTF_8);
      Path outputJson = tempDir.resolve("output.json");
      Path expectedSessionFile = runnerWorktree.resolve(".cat/work/output-session.json");
      Path launcher = runnerWorktree.resolve("client/distribution/target/jlink/codex/bin/codex-runner");
      Path launcherLog = tempDir.resolve("codex-launcher.log");
      Files.createDirectories(launcher.getParent());
      Files.writeString(launcher, """
        #!/usr/bin/env bash
        set -euo pipefail
        if [[ "${1:-}" == "--help" ]]; then
          echo "--plugin-source --jlink-bin --output --session-file"
          exit 0
        fi
        session_file=""
        for ((i=1; i<=$#; ++i)); do
          arg="${!i}"
          if [[ "$arg" == "--session-file" ]]; then
            next=$((i + 1))
            session_file="${!next}"
          fi
        done
        mkdir -p "$(dirname "$session_file")"
        if [[ -f "$session_file" ]]; then
          echo "exists=true:$session_file" >> "%s"
        else
          echo "exists=false:$session_file" >> "%s"
        fi
        printf 'active' > "$session_file"
        """.formatted(launcherLog, launcherLog), StandardCharsets.UTF_8);
      requireThat(launcher.toFile().setExecutable(true), "launcherExecutable").isTrue();
      Files.createDirectories(expectedSessionFile.getParent());
      Files.writeString(expectedSessionFile, "stale", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      int exitCode = SharedSecrets.runTrial(runner, List.of(promptOne, promptTwo), "gpt-5.5",
        "high", runnerWorktree.toString(), outputJson.toString(),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
      List<String> launcherRuns = Files.readAllLines(launcherLog, StandardCharsets.UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(0);
      requireThat(launcherRuns, "launcherRuns").isEqualTo(List.of(
        "exists=false:" + expectedSessionFile,
        "exists=true:" + expectedSessionFile));
      requireThat(Files.exists(expectedSessionFile), "sessionFileExistsAfter").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that later multi-turn Codex runner launches observe session state from prior turns.
   */
  @Test
  public void codexMultiTurnTrialReusesSessionState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new MainCliTool(name -> switch (name)
    {
      case "CAT_SESSION_ID" -> "test-session-id";
      case "CAT_PROJECT_DIR", "CAT_PLUGIN_ROOT", "CAT_PLUGIN_DATA", "CAT_CONFIG_DIR" ->
        tempDir.toString();
      case "CAT_ENGINE" -> "codex";
      default -> null;
    }, tempDir))
    {
      Path runnerWorktree = Files.createDirectories(tempDir.resolve("runner-worktree"));
      Path promptOne = tempDir.resolve("turn1.md");
      Path promptTwo = tempDir.resolve("turn2.md");
      Files.writeString(promptOne, "turn one", StandardCharsets.UTF_8);
      Files.writeString(promptTwo, "turn two", StandardCharsets.UTF_8);
      Path outputJson = tempDir.resolve("output.json");
      Path launcher = runnerWorktree.resolve("client/distribution/target/jlink/codex/bin/codex-runner");
      Files.createDirectories(launcher.getParent());
      Files.writeString(launcher, """
        #!/usr/bin/env bash
        set -euo pipefail
        if [[ "${1:-}" == "--help" ]]; then
          echo "--output --session-file"
          exit 0
        fi
        prompt_file=""
        output_file=""
        session_file=""
        for ((i=1; i<=$#; ++i)); do
          arg="${!i}"
          if [[ "$arg" == "--prompt-file" ]]; then
            next=$((i + 1))
            prompt_file="${!next}"
          elif [[ "$arg" == "--output" ]]; then
            next=$((i + 1))
            output_file="${!next}"
          elif [[ "$arg" == "--session-file" ]]; then
            next=$((i + 1))
            session_file="${!next}"
          fi
        done
        if [[ "$prompt_file" == *"turn1.md" ]]; then
          mkdir -p "$(dirname "$session_file")"
          printf 'first-turn-state' > "$session_file"
          if [[ -n "$output_file" ]]; then
            exit 8
          fi
          exit 0
        fi
        if [[ ! -f "$session_file" ]] || [[ "$(<"$session_file")" != "first-turn-state" ]]; then
          exit 9
        fi
        printf '%s' \
          '{"texts":["second turn"],"toolUses":[],"writeContents":[],"turns":[],' \
          '"sessionId":"session-123"}' > "$output_file"
        exit 0
        """, StandardCharsets.UTF_8);
      requireThat(launcher.toFile().setExecutable(true), "launcherExecutable").isTrue();

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      int exitCode = SharedSecrets.runTrial(runner, List.of(promptOne, promptTwo), "gpt-5.5",
        "high", runnerWorktree.toString(), outputJson.toString(),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

      requireThat(exitCode, "exitCode").isEqualTo(0);
      JsonNode root = scope.getJsonMapper().readTree(outputJson.toFile());
      requireThat(root.path("texts").get(0).asString(), "finalText").isEqualTo("second turn");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multi-turn trial execution fails fast when the runner does not support session files.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*does not support --session-file.*")
  public void multiTurnTrialRequiresSessionFileSupport() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path runnerWorktree = Files.createDirectories(tempDir.resolve("runner-worktree"));
      Path promptOne = tempDir.resolve("turn1.md");
      Path promptTwo = tempDir.resolve("turn2.md");
      Files.writeString(promptOne, "turn one", StandardCharsets.UTF_8);
      Files.writeString(promptTwo, "turn two", StandardCharsets.UTF_8);
      Path outputJson = tempDir.resolve("output.json");
      Path launcher = runnerWorktree.resolve("client/distribution/target/jlink/claude/bin/claude-runner");
      Files.createDirectories(launcher.getParent());
      Files.writeString(launcher, """
        #!/usr/bin/env bash
        set -euo pipefail
        if [[ "${1:-}" == "--help" ]]; then
          echo "--plugin-source --jlink-bin --output"
          exit 0
        fi
        exit 0
        """, StandardCharsets.UTF_8);
      requireThat(launcher.toFile().setExecutable(true), "launcherExecutable").isTrue();

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      SharedSecrets.runTrial(runner, List.of(promptOne, promptTwo), "claude-sonnet-4-5", "high",
        runnerWorktree.toString(), outputJson.toString(),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multi-turn trial execution fails if a successful intermediate turn does not
   * persist the managed session file.
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*did not persist session state.*")
  public void multiTurnTrialRequiresSessionState() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path runnerWorktree = Files.createDirectories(tempDir.resolve("runner-worktree"));
      Path promptOne = tempDir.resolve("turn1.md");
      Path promptTwo = tempDir.resolve("turn2.md");
      Files.writeString(promptOne, "turn one", StandardCharsets.UTF_8);
      Files.writeString(promptTwo, "turn two", StandardCharsets.UTF_8);
      Path outputJson = tempDir.resolve("output.json");
      Path launcher = runnerWorktree.resolve("client/distribution/target/jlink/claude/bin/claude-runner");
      Files.createDirectories(launcher.getParent());
      Files.writeString(launcher, """
        #!/usr/bin/env bash
        set -euo pipefail
        if [[ "${1:-}" == "--help" ]]; then
          echo "--plugin-source --jlink-bin --output --session-file"
          exit 0
        fi
        exit 0
        """, StandardCharsets.UTF_8);
      requireThat(launcher.toFile().setExecutable(true), "launcherExecutable").isTrue();

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      SharedSecrets.runTrial(runner, List.of(promptOne, promptTwo), "claude-sonnet-4-5", "high",
        runnerWorktree.toString(), outputJson.toString(),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a later multi-turn runner failure cannot leave stale output from an earlier turn behind.
   */
  @Test
  public void multiTurnFailureDeletesStaleOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path runnerWorktree = Files.createDirectories(tempDir.resolve("runner-worktree"));
      Path promptOne = tempDir.resolve("turn1.md");
      Path promptTwo = tempDir.resolve("turn2.md");
      Files.writeString(promptOne, "turn one", StandardCharsets.UTF_8);
      Files.writeString(promptTwo, "turn two", StandardCharsets.UTF_8);
      Path outputJson = tempDir.resolve("output.json");
      Path launcher = runnerWorktree.resolve("client/distribution/target/jlink/claude/bin/claude-runner");
      Files.createDirectories(launcher.getParent());
      Files.writeString(launcher, """
        #!/usr/bin/env bash
        set -euo pipefail
        if [[ "${1:-}" == "--help" ]]; then
          echo "--plugin-source --jlink-bin --output --session-file"
          exit 0
        fi
        prompt_file=""
        output_file=""
        session_file=""
        for ((i=1; i<=$#; ++i)); do
          arg="${!i}"
          if [[ "$arg" == "--prompt-file" ]]; then
            next=$((i + 1))
            prompt_file="${!next}"
          elif [[ "$arg" == "--output" ]]; then
            next=$((i + 1))
            output_file="${!next}"
          elif [[ "$arg" == "--session-file" ]]; then
            next=$((i + 1))
            session_file="${!next}"
          fi
        done
        if [[ "$prompt_file" == *"turn1.md" ]]; then
          mkdir -p "$(dirname "$session_file")"
          printf 'first-turn-state' > "$session_file"
          if [[ -z "$output_file" ]]; then
            exit 0
          fi
          printf '%s' \
            '{"texts":["first turn"],"toolUses":[],"writeContents":[],"turns":[],' \
            '"sessionId":"session-123"}' > "$output_file"
          exit 0
        fi
        exit 1
        """, StandardCharsets.UTF_8);
      requireThat(launcher.toFile().setExecutable(true), "launcherExecutable").isTrue();

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      int exitCode = SharedSecrets.runTrial(runner, List.of(promptOne, promptTwo),
        "claude-sonnet-4-5", "high", runnerWorktree.toString(), outputJson.toString(),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

      requireThat(exitCode, "exitCode").isEqualTo(1);
      requireThat(Files.exists(outputJson), "outputExistsAfterFailure").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that write-test-results returns overall_decision and test_sha after a successful commit.
   */
  @Test
  public void writeTestResultsReturnsOverallDecision() throws IOException, InterruptedException
  {
    Path mainRepo = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sprtStatePath = mainRepo.resolve(".cat/work/sprt-state.json");
      Files.createDirectories(sprtStatePath.getParent());
      Files.writeString(sprtStatePath,
        "{\"model_id\":\"claude-haiku-4-5\",\"failed_test_ids\":[]," +
        "\"sprt_state\":{\"tc1\":{\"decision\":\"ACCEPT\",\"runs\":3,\"log_ratio\":2.944," +
        "\"passes\":3,\"fails\":0}}}",
        StandardCharsets.UTF_8);

      Path testDirPath = mainRepo.resolve("plugin/tests/myskill");
      Files.createDirectories(testDirPath);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.writeTestResults(new String[]{
        mainRepo.toString(), sprtStatePath.toString(), testDirPath.toString()});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      requireThat(pairs.get("status"), "status").isEqualTo("ok");
      requireThat(pairs.get("overall_decision"), "overall_decision").isEqualTo("ACCEPT");
      requireThat(pairs.get("test_sha"), "test_sha").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the Claude trial runner receives the expected argument shape.
   */
  @Test
  public void buildClaudeTrialArgsIncludesEngine() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-trial-args-");
    try
    {
      Path promptFile = tempDir.resolve("trial-prompt.txt");
      Files.writeString(promptFile, "test prompt", StandardCharsets.UTF_8);
      String runnerWorktree = tempDir.toString();
      String modelId = "claude-sonnet-4-5";
      String effort = "high";
      String outputJson = tempDir.resolve("output.json").toString();
      Path jlinkBin = tempDir.resolve("client/distribution/target/jlink/claude/bin");

      String[] args = SharedSecrets.buildClaudeTrialArgs(promptFile, modelId, effort,
        runnerWorktree, outputJson, jlinkBin);

      requireThat(args, "args").length().isEqualTo(14);
      requireThat(args[0], "args[0]").isEqualTo("--prompt-file");
      requireThat(args[1], "args[1]").isEqualTo(promptFile.toString());
      requireThat(args[2], "args[2]").isEqualTo("--model");
      requireThat(args[3], "args[3]").isEqualTo(modelId);
      requireThat(args[4], "args[4]").isEqualTo("--effort");
      requireThat(args[5], "args[5]").isEqualTo(effort);
      requireThat(args[6], "args[6]").isEqualTo("--plugin-source");
      requireThat(args[7], "args[7]").isEqualTo(Path.of(runnerWorktree, "client/plugin").toString());
      requireThat(args[8], "args[8]").isEqualTo("--jlink-bin");
      requireThat(args[9], "args[9]").isEqualTo(jlinkBin.toString());
      requireThat(args[10], "args[10]").isEqualTo("--cwd");
      requireThat(args[11], "args[11]").isEqualTo(runnerWorktree);
      requireThat(args[12], "args[12]").isEqualTo("--output");
      requireThat(args[13], "args[13]").isEqualTo(outputJson);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the Codex trial runner receives the expected argument shape.
   */
  @Test
  public void buildCodexTrialArgsIncludesEngine() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-trial-args-");
    try
    {
      Path promptFile = tempDir.resolve("trial-prompt.txt");
      Files.writeString(promptFile, "test prompt", StandardCharsets.UTF_8);
      String runnerWorktree = tempDir.toString();
      String modelId = "gpt-5.3-codex";
      String effort = "xhigh";
      String outputJson = tempDir.resolve("output.json").toString();

      String[] args = SharedSecrets.buildCodexTrialArgs(promptFile, modelId, effort,
        runnerWorktree, outputJson);

      requireThat(args, "args").length().isEqualTo(10);
      requireThat(args[0], "args[0]").isEqualTo("--prompt-file");
      requireThat(args[1], "args[1]").isEqualTo(promptFile.toString());
      requireThat(args[2], "args[2]").isEqualTo("--model");
      requireThat(args[3], "args[3]").isEqualTo(modelId);
      requireThat(args[4], "args[4]").isEqualTo("--effort");
      requireThat(args[5], "args[5]").isEqualTo(effort);
      requireThat(args[6], "args[6]").isEqualTo("--cwd");
      requireThat(args[7], "args[7]").isEqualTo(runnerWorktree);
      requireThat(args[8], "args[8]").isEqualTo("--output");
      requireThat(args[9], "args[9]").isEqualTo(outputJson);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the Claude grader receives the expected argument shape.
   */
  @Test
  public void buildClaudeGraderArgsUsesAgentFlag() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-grader-args-");
    try
    {
      Path graderPromptFile = tempDir.resolve("grader-prompt.txt");
      Files.writeString(graderPromptFile, "test prompt", StandardCharsets.UTF_8);
      String runnerWorktree = tempDir.toString();
      String modelId = "claude-sonnet-4-5";
      String effort = "medium";
      Path jlinkBin = tempDir.resolve("client/distribution/target/jlink/claude/bin");

      String[] args = SharedSecrets.buildClaudeGraderArgs(graderPromptFile, modelId, effort,
        runnerWorktree, jlinkBin);

      requireThat(args, "args").length().isEqualTo(14);
      requireThat(args[0], "args[0]").isEqualTo("--prompt-file");
      requireThat(args[1], "args[1]").isEqualTo(graderPromptFile.toString());
      requireThat(args[2], "args[2]").isEqualTo("--model");
      requireThat(args[3], "args[3]").isEqualTo(modelId);
      requireThat(args[4], "args[4]").isEqualTo("--effort");
      requireThat(args[5], "args[5]").isEqualTo(effort);
      requireThat(args[6], "args[6]").isEqualTo("--agent");
      requireThat(args[7], "args[7]").isEqualTo("instruction-grader-agent");
      requireThat(args[8], "args[8]").isEqualTo("--plugin-source");
      requireThat(args[9], "args[9]").isEqualTo(Path.of(runnerWorktree, "client/plugin").toString());
      requireThat(args[10], "args[10]").isEqualTo("--jlink-bin");
      requireThat(args[11], "args[11]").isEqualTo(jlinkBin.toString());
      requireThat(args[12], "args[12]").isEqualTo("--cwd");
      requireThat(args[13], "args[13]").isEqualTo(runnerWorktree);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the Codex grader receives the expected argument shape.
   */
  @Test
  public void buildCodexGraderArgsIncludesEngine() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-grader-args-");
    try
    {
      Path graderPromptFile = tempDir.resolve("grader-prompt.txt");
      Files.writeString(graderPromptFile, "test prompt", StandardCharsets.UTF_8);
      String runnerWorktree = tempDir.toString();
      String modelId = "gpt-5.3-codex";
      String effort = "high";

      String[] args = SharedSecrets.buildCodexGraderArgs(graderPromptFile, modelId, effort,
        runnerWorktree);

      requireThat(args, "args").length().isEqualTo(8);
      requireThat(args[0], "args[0]").isEqualTo("--prompt-file");
      requireThat(args[1], "args[1]").isEqualTo(graderPromptFile.toString());
      requireThat(args[2], "args[2]").isEqualTo("--model");
      requireThat(args[3], "args[3]").isEqualTo(modelId);
      requireThat(args[4], "args[4]").isEqualTo("--effort");
      requireThat(args[5], "args[5]").isEqualTo(effort);
      requireThat(args[6], "args[6]").isEqualTo("--cwd");
      requireThat(args[7], "args[7]").isEqualTo(runnerWorktree);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Claude trial argument building rejects a null prompt file.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*promptFile.*")
  public void buildClaudeTrialArgsRejectsNullPrompt()
  {
    SharedSecrets.buildClaudeTrialArgs(null, "claude-sonnet-4-5", "high", "/tmp/worktree",
      "/tmp/output.json", Path.of("/tmp/worktree/client/distribution/target/jlink/claude/bin"));
  }

  /**
   * Verifies that Codex trial argument building rejects a blank model ID.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*modelId.*")
  public void buildCodexTrialArgsRejectsBlankModelId()
  {
    SharedSecrets.buildCodexTrialArgs(Path.of("/tmp/prompt.txt"), " ", "high",
      "/tmp/worktree", "/tmp/output.json");
  }

  /**
   * Verifies that Claude grader argument building rejects a blank effort.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*effort.*")
  public void buildClaudeGraderArgsRejectsBlankEffort()
  {
    SharedSecrets.buildClaudeGraderArgs(Path.of("/tmp/grader-prompt.txt"), "claude-sonnet-4-5",
      " ", "/tmp/worktree",
      Path.of("/tmp/worktree/client/distribution/target/jlink/claude/bin"));
  }

  /**
   * Verifies that Codex grader argument building rejects a blank runner worktree.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*runnerWorktree.*")
  public void buildCodexGraderArgsRejectsBlankRunner()
  {
    SharedSecrets.buildCodexGraderArgs(Path.of("/tmp/grader-prompt.txt"), "gpt-5.3-codex",
      "high", " ");
  }

  /**
   * Verifies that engine-dispatched trial argument building rejects a null descriptor.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*descriptor.*")
  public void engineTrialArgsRejectNullDescriptor()
  {
    SharedSecrets.buildTrialArgsForDescriptor(null, Path.of("/tmp/prompt.txt"),
      "claude-sonnet-4-5", "high", "/tmp/worktree", "/tmp/output.json");
  }

  /**
   * Verifies that engine-dispatched trial argument building rejects an unsupported descriptor.
   */
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*Unsupported CAT engine descriptor.*")
  public void engineTrialArgsRejectUnsupported()
  {
    SharedSecrets.buildTrialArgsForDescriptor(Path.of("/tmp/unsupported-plugin.json"),
      Path.of("/tmp/prompt.txt"), "claude-sonnet-4-5", "high", "/tmp/worktree",
      "/tmp/output.json");
  }

  /**
   * Verifies that engine-dispatched trial argument building rejects a null prompt.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*promptFile.*")
  public void engineTrialArgsRejectNullPrompt()
  {
    SharedSecrets.buildTrialArgsForDescriptor(AgentEngine.CLAUDE.pluginDescriptor(), null,
      "claude-sonnet-4-5", "high", "/tmp/worktree", "/tmp/output.json");
  }

  /**
   * Verifies that engine-dispatched trial argument building rejects a blank output path.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*outputJson.*")
  public void engineTrialArgsRejectBlankOutput()
  {
    SharedSecrets.buildTrialArgsForDescriptor(AgentEngine.CODEX.pluginDescriptor(),
      Path.of("/tmp/prompt.txt"), "gpt-5.3-codex", "high", "/tmp/worktree", " ");
  }

  /**
   * Verifies that engine-dispatched Claude trial arguments reject a blank model ID.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*modelId.*")
  public void engineClaudeTrialArgsRejectBlankModel()
  {
    SharedSecrets.buildTrialArgsForDescriptor(AgentEngine.CLAUDE.pluginDescriptor(),
      Path.of("/tmp/prompt.txt"), " ", "high", "/tmp/worktree", "/tmp/output.json");
  }

  /**
   * Verifies that engine-dispatched Codex trial arguments reject a blank effort.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*effort.*")
  public void engineCodexTrialArgsRejectBlankEffort()
  {
    SharedSecrets.buildTrialArgsForDescriptor(AgentEngine.CODEX.pluginDescriptor(),
      Path.of("/tmp/prompt.txt"), "gpt-5.3-codex", " ", "/tmp/worktree", "/tmp/output.json");
  }

  /**
   * Verifies that engine-dispatched Claude trial arguments reject unsupported model IDs.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid Claude model ID.*")
  public void engineClaudeTrialArgsRejectUnsupported()
  {
    SharedSecrets.buildTrialArgsForDescriptor(AgentEngine.CLAUDE.pluginDescriptor(),
      Path.of("/tmp/prompt.txt"), "gpt-5.3-codex", "high", "/tmp/worktree",
      "/tmp/output.json");
  }

  /**
   * Verifies that engine-dispatched Codex trial arguments reject unsupported efforts.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid effort.*")
  public void engineCodexTrialArgsRejectUnsupported()
  {
    SharedSecrets.buildTrialArgsForDescriptor(AgentEngine.CODEX.pluginDescriptor(),
      Path.of("/tmp/prompt.txt"), "gpt-5.3-codex", "extreme", "/tmp/worktree",
      "/tmp/output.json");
  }

  /**
   * Verifies that engine-dispatched Claude trial arguments reject unsupported efforts.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid effort.*")
  public void engineClaudeTrialArgsRejectCodexOnly()
  {
    SharedSecrets.buildClaudeTrialArgs(Path.of("/tmp/prompt.txt"), "claude-sonnet-4-5",
      "extreme", "/tmp/worktree", "/tmp/output.json", Path.of("/tmp/jlink/bin"));
  }

  /**
   * Verifies that engine-dispatched Codex trial arguments reject Claude-only efforts.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid effort.*")
  public void engineCodexTrialArgsRejectClaudeOnly()
  {
    SharedSecrets.buildTrialArgsForDescriptor(AgentEngine.CODEX.pluginDescriptor(),
      Path.of("/tmp/prompt.txt"), "gpt-5.3-codex", "max", "/tmp/worktree",
      "/tmp/output.json");
  }

  /**
   * Verifies that engine-dispatched Claude trial arguments accept Claude-only efforts.
   */
  @Test
  public void engineClaudeTrialArgsAcceptClaudeOnly()
  {
    String[] args = SharedSecrets.buildClaudeTrialArgs(Path.of("/tmp/prompt.txt"),
      "claude-sonnet-4-5", "max", "/tmp/worktree", "/tmp/output.json",
      Path.of("/tmp/jlink/bin"));

    requireThat(args[5], "effort").isEqualTo("max");
  }

  /**
   * Verifies that engine-dispatched grader argument building rejects a null prompt.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*graderPromptFile.*")
  public void engineGraderArgsRejectNullPrompt()
  {
    SharedSecrets.buildGraderArgsForDescriptor(AgentEngine.CLAUDE.pluginDescriptor(), null,
      "claude-sonnet-4-5", "high", "/tmp/worktree");
  }

  /**
   * Verifies that engine-dispatched grader argument building rejects a blank runner worktree.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*runnerWorktree.*")
  public void engineGraderArgsRejectBlankRunner()
  {
    SharedSecrets.buildGraderArgsForDescriptor(AgentEngine.CODEX.pluginDescriptor(),
      Path.of("/tmp/grader-prompt.txt"), "gpt-5.3-codex", "high", " ");
  }

  /**
   * Verifies that engine-dispatched Claude grader arguments reject a null model ID.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*modelId.*")
  public void engineClaudeGraderArgsRejectNullModel()
  {
    SharedSecrets.buildGraderArgsForDescriptor(AgentEngine.CLAUDE.pluginDescriptor(),
      Path.of("/tmp/grader-prompt.txt"), null, "high", "/tmp/worktree");
  }

  /**
   * Verifies that engine-dispatched Codex grader arguments reject a blank effort.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*effort.*")
  public void engineCodexGraderArgsRejectBlankEffort()
  {
    SharedSecrets.buildGraderArgsForDescriptor(AgentEngine.CODEX.pluginDescriptor(),
      Path.of("/tmp/grader-prompt.txt"), "gpt-5.3-codex", " ", "/tmp/worktree");
  }

  /**
   * Verifies that engine-dispatched Claude grader arguments reject unsupported efforts.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid effort.*")
  public void engineClaudeGraderArgsRejectUnsupported()
  {
    SharedSecrets.buildGraderArgsForDescriptor(AgentEngine.CLAUDE.pluginDescriptor(),
      Path.of("/tmp/grader-prompt.txt"), "claude-sonnet-4-5", "extreme", "/tmp/worktree");
  }

  /**
   * Verifies that engine-dispatched Codex grader arguments reject unsupported model IDs.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid Codex model ID.*")
  public void engineCodexGraderArgsRejectUnsupported()
  {
    SharedSecrets.buildGraderArgsForDescriptor(AgentEngine.CODEX.pluginDescriptor(),
      Path.of("/tmp/grader-prompt.txt"), "claude-sonnet-4-5", "high", "/tmp/worktree");
  }

  /**
   * Verifies that engine-dispatched trial arguments select the Claude runner shape.
   */
  @Test
  public void engineTrialArgsUseClaudeShape() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-engine-trial-args-");
    try
    {
      Path promptFile = tempDir.resolve("trial-prompt.txt");
      Files.writeString(promptFile, "test prompt", StandardCharsets.UTF_8);
      String runnerWorktree = tempDir.toString();
      Files.createDirectories(
        tempDir.resolve("client/distribution/target/jlink/claude/bin"));
      String outputJson = tempDir.resolve("output.json").toString();

      String[] args = SharedSecrets.buildTrialArgsForDescriptor(
        AgentEngine.CLAUDE.pluginDescriptor(), promptFile, "claude-sonnet-4-5", "high",
        runnerWorktree, outputJson);

      requireThat(args, "args").length().isEqualTo(14);
      requireThat(args[6], "pluginSourceFlag").isEqualTo("--plugin-source");
      requireThat(args[8], "jlinkFlag").isEqualTo("--jlink-bin");
      requireThat(args[9], "jlinkPath").isEqualTo(
        Path.of(runnerWorktree, "client/distribution/target/jlink/claude/bin").toString());
      requireThat(args[12], "outputFlag").isEqualTo("--output");
      requireThat(args[13], "outputJson").isEqualTo(outputJson);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that engine-dispatched Claude trial arguments fail fast when the runner worktree
   * does not contain a engine jlink directory.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*jlink directory not found in runner worktree.*")
  public void engineTrialArgsRejectMissingRunnerJlink() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-engine-trial-args-");
    try
    {
      Path promptFile = tempDir.resolve("trial-prompt.txt");
      Files.writeString(promptFile, "test prompt", StandardCharsets.UTF_8);
      SharedSecrets.buildTrialArgsForDescriptor(AgentEngine.CLAUDE.pluginDescriptor(),
        promptFile, "claude-sonnet-4-5", "high", tempDir.toString(),
        tempDir.resolve("output.json").toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that engine-dispatched trial arguments select the Codex runner shape.
   */
  @Test
  public void engineTrialArgsUseCodexShape() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-engine-trial-args-");
    try
    {
      Path promptFile = tempDir.resolve("trial-prompt.txt");
      Files.writeString(promptFile, "test prompt", StandardCharsets.UTF_8);
      String runnerWorktree = tempDir.toString();
      String outputJson = tempDir.resolve("output.json").toString();

      String[] args = SharedSecrets.buildTrialArgsForDescriptor(
        AgentEngine.CODEX.pluginDescriptor(), promptFile, "gpt-5.3-codex", "xhigh",
        runnerWorktree, outputJson);

      requireThat(args, "args").length().isEqualTo(10);
      requireThat(args[0], "promptFlag").isEqualTo("--prompt-file");
      requireThat(args[4], "effortFlag").isEqualTo("--effort");
      requireThat(args[6], "cwdFlag").isEqualTo("--cwd");
      requireThat(args[8], "outputFlag").isEqualTo("--output");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that engine-dispatched grader arguments select the Claude runner shape.
   */
  @Test
  public void engineGraderArgsUseClaudeShape() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-engine-grader-args-");
    try
    {
      Path promptFile = tempDir.resolve("grader-prompt.txt");
      Files.writeString(promptFile, "test prompt", StandardCharsets.UTF_8);
      String runnerWorktree = tempDir.toString();
      Files.createDirectories(
        tempDir.resolve("client/distribution/target/jlink/claude/bin"));

      String[] args = SharedSecrets.buildGraderArgsForDescriptor(
        AgentEngine.CLAUDE.pluginDescriptor(), promptFile, "claude-sonnet-4-5", "medium",
        runnerWorktree);

      requireThat(args, "args").length().isEqualTo(14);
      requireThat(args[6], "agentFlag").isEqualTo("--agent");
      requireThat(args[7], "agentName").isEqualTo("instruction-grader-agent");
      requireThat(args[10], "jlinkFlag").isEqualTo("--jlink-bin");
      requireThat(args[11], "jlinkPath").isEqualTo(
        Path.of(runnerWorktree, "client/distribution/target/jlink/claude/bin").toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that engine-dispatched grader arguments select the Codex runner shape.
   */
  @Test
  public void engineGraderArgsUseCodexShape() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-engine-grader-args-");
    try
    {
      Path promptFile = tempDir.resolve("grader-prompt.txt");
      Files.writeString(promptFile, "test prompt", StandardCharsets.UTF_8);
      String runnerWorktree = tempDir.toString();

      String[] args = SharedSecrets.buildGraderArgsForDescriptor(
        AgentEngine.CODEX.pluginDescriptor(), promptFile, "gpt-5.3-codex", "high",
        runnerWorktree);

      requireThat(args, "args").length().isEqualTo(8);
      requireThat(args[0], "promptFlag").isEqualTo("--prompt-file");
      requireThat(args[4], "effortFlag").isEqualTo("--effort");
      requireThat(args[6], "cwdFlag").isEqualTo("--cwd");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that supported SPRT engine descriptors resolve to their engine identifiers.
   */
  @Test
  public void sprtEngineResolvesSupportedDescriptors()
  {
    requireThat(SharedSecrets.sprtEngineIdForDescriptor(AgentEngine.CLAUDE.pluginDescriptor()),
      "claudeEngine").isEqualTo("claude");
    requireThat(SharedSecrets.sprtEngineIdForDescriptor(AgentEngine.CODEX.pluginDescriptor()),
      "codexEngine").isEqualTo("codex");
  }

  /**
   * Verifies that the fixed Claude grader config is read from the grader agent descriptor.
   */
  @Test
  public void graderConfigResolvesClaudeDescriptor() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-grader-config-");
    try
    {
      Path pluginRoot = tempDir.resolve("plugin");
      Path agentsDir = pluginRoot.resolve("agents/claude");
      Files.createDirectories(agentsDir);
      Files.writeString(agentsDir.resolve("instruction-grader-agent.md"), """
        ---
        name: instruction-grader-agent
        model: haiku
        effort: low
        ---
        """, StandardCharsets.UTF_8);

      SharedSecrets.ModelEffort config = SharedSecrets.resolveGraderModelEffort(pluginRoot,
        AgentEngine.CLAUDE.pluginDescriptor(), "2.1.87");

      requireThat(config.modelId(), "modelId").isEqualTo("claude-haiku-4-5");
      requireThat(config.effort(), "effort").isEqualTo("low");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run-sprt trial config is independent from fixed Codex grader config.
   */
  @Test
  public void trialConfigIndependentFromCodexGrader() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-grader-config-");
    try
    {
      Path pluginRoot = tempDir.resolve("plugin");
      Path agentsDir = pluginRoot.resolve("agents/codex");
      Files.createDirectories(agentsDir);
      Files.writeString(agentsDir.resolve("instruction-grader-agent.toml"), """
        name = "cat-instruction-grader-agent"
        model = "gpt-5.4-mini"
        model_reasoning_effort = "medium"
        """, StandardCharsets.UTF_8);
      Path promptFile = tempDir.resolve("trial-prompt.txt");
      Files.writeString(promptFile, "test prompt", StandardCharsets.UTF_8);

      String[] trialArgs = SharedSecrets.buildTrialArgsForDescriptor(
        AgentEngine.CODEX.pluginDescriptor(), promptFile, "gpt-5.5", "xhigh",
        tempDir.toString(), tempDir.resolve("output.json").toString());
      SharedSecrets.ModelEffort graderConfig = SharedSecrets.resolveGraderModelEffort(pluginRoot,
        AgentEngine.CODEX.pluginDescriptor(), "2.1.87");

      requireThat(trialArgs[3], "trialModel").isEqualTo("gpt-5.5");
      requireThat(trialArgs[5], "trialEffort").isEqualTo("xhigh");
      requireThat(graderConfig.modelId(), "graderModel").isEqualTo("gpt-5.4-mini");
      requireThat(graderConfig.effort(), "graderEffort").isEqualTo("medium");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SPRT engine resolution fails fast for unsupported descriptors.
   */
  @Test(expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = ".*Unsupported CAT engine descriptor.*")
  public void sprtEngineRejectsUnsupportedDescriptor()
  {
    SharedSecrets.sprtEngineIdForDescriptor(Path.of("unsupported/plugin.json"));
  }

  /**
   * Verifies that buildClaudeGraderArgs rejects null parameters.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*graderPromptFile.*")
  public void buildClaudeGraderArgsRejectsNullPrompt()
  {
    SharedSecrets.buildClaudeGraderArgs(null, "model", "medium", "/tmp/worktree",
      Path.of("/tmp/jlink/bin"));
  }
}
