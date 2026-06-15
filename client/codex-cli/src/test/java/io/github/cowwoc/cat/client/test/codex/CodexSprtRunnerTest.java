/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test.codex;

import io.github.cowwoc.cat.client.test.TestCodexTool;
import io.github.cowwoc.cat.client.test.TestUtils;
import io.github.cowwoc.cat.codex.engine.CodexSprtMetadataResolver;
import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.skills.SharedSecrets;
import io.github.cowwoc.cat.tool.skills.SprtRunner;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Runtime tests for Codex SPRT runner behavior.
 */
public final class CodexSprtRunnerTest
{
  /**
   * Verifies runtime command delegation behavior through SprtRunner for a Codex scope.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if interrupted
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*unknown command: unknown-command.*")
  public void unknownCommandUsesSprtRunnerDispatch() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-runner-test-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      SprtRunner.run(scope, new String[]{"unknown-command"},
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
        new CodexSprtMetadataResolver(scope));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex metadata extraction uses the fixed test-runner default because Codex
   * skills do not support model frontmatter.
   */
  @Test
  public void extractModelUsesCodexDefault() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-metadata-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        model: gpt-5.5
        ---
        # Body
        """, StandardCharsets.UTF_8);

      SprtRunner runner = newMetadataRunner(scope);
      String model = runner.extractModel(new String[]{skillFile.toString()});
      requireThat(model, "model").isEqualTo("gpt-5.4-mini");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex metadata extraction uses the fixed test-runner default because Codex
   * skills do not support effort frontmatter.
   */
  @Test
  public void extractEffortUsesCodexDefault() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-metadata-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        effort: high
        ---
        # Body
        """, StandardCharsets.UTF_8);

      SprtRunner runner = newMetadataRunner(scope);
      String effort = runner.extractEffort(new String[]{skillFile.toString()});
      requireThat(effort, "effort").isEqualTo("low");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex extracts the test-runner config from a uniquely targeted rule agent.
   */
  @Test
  public void extractConfigUsesCodexRuleOwner() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-metadata-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      Path ruleFile = tempDir.resolve("rules/common/configuration-reads.md");
      Files.createDirectories(ruleFile.getParent());
      Files.writeString(ruleFile, """
        ---
        agents: ["cat:work-execute"]
        ---
        # Rule
        """, StandardCharsets.UTF_8);

      SprtRunner runner = newMetadataRunner(scope);
      requireThat(runner.extractModel(new String[]{ruleFile.toString()}), "model").
        isEqualTo("gpt-5.5");
      requireThat(runner.extractEffort(new String[]{ruleFile.toString()}), "effort").
        isEqualTo("high");
      requireThat(runner.extractConfigSource(new String[]{ruleFile.toString()}), "configSource").
        isEqualTo("owner");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex extracts the weakest config from multiple targeted rule agents.
   */
  @Test
  public void usesWeakestCodexRuleOwner() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-metadata-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      writeCodexAgent(tempDir, "work-merge", "gpt-5.4-mini", "medium");
      writeCodexAgent(tempDir, "instruction-builder-implement-agent", "gpt-5.4-mini",
        "low");
      Path ruleFile = tempDir.resolve("rules/common/multi-agent.md");
      Files.createDirectories(ruleFile.getParent());
      Files.writeString(ruleFile, """
        ---
        agents: ["cat:work-execute", "cat:work-merge", "cat:instruction-builder-implement-agent"]
        ---
        # Rule
        """, StandardCharsets.UTF_8);

      SprtRunner runner = newMetadataRunner(scope);
      requireThat(runner.extractModel(new String[]{ruleFile.toString()}), "model").
        isEqualTo("gpt-5.4-mini");
      requireThat(runner.extractEffort(new String[]{ruleFile.toString()}), "effort").
        isEqualTo("low");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that rules without an agents restriction use the weakest config across all Codex
   * agents.
   */
  @Test
  public void usesWeakestCodexBroadRule() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-metadata-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      writeCodexAgent(tempDir, "work-merge", "gpt-5.4-mini", "medium");
      writeCodexAgent(tempDir, "instruction-builder-implement-agent", "gpt-5.4-mini",
        "low");
      Path ruleFile = tempDir.resolve("rules/common/broad-rule.md");
      Files.createDirectories(ruleFile.getParent());
      Files.writeString(ruleFile, "# Rule", StandardCharsets.UTF_8);

      SprtRunner runner = newMetadataRunner(scope);
      requireThat(runner.extractModel(new String[]{ruleFile.toString()}), "model").
        isEqualTo("gpt-5.4-mini");
      requireThat(runner.extractEffort(new String[]{ruleFile.toString()}), "effort").
        isEqualTo("low");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that agents: ["subagents"] uses the weakest config across all Codex agents.
   */
  @Test
  public void usesWeakestCodexAllSubagentsRule() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-metadata-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      writeCodexAgent(tempDir, "work-merge", "gpt-5.4-mini", "medium");
      Path ruleFile = tempDir.resolve("rules/common/all-subagents.md");
      Files.createDirectories(ruleFile.getParent());
      Files.writeString(ruleFile, """
        ---
        agents: ["subagents"]
        ---
        # Rule
        """, StandardCharsets.UTF_8);

      SprtRunner runner = newMetadataRunner(scope);
      requireThat(runner.extractModel(new String[]{ruleFile.toString()}), "model").
        isEqualTo("gpt-5.4-mini");
      requireThat(runner.extractEffort(new String[]{ruleFile.toString()}), "effort").
        isEqualTo("medium");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that agents: ["subagents"] cannot be combined with specific subagent owners.
   */
  @Test
  public void rejectsMixedSubagentAgents() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-metadata-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      Path ruleFile = tempDir.resolve("rules/common/invalid-agents.md");
      Files.createDirectories(ruleFile.getParent());
      Files.writeString(ruleFile, """
        ---
        agents: ["cat:missing-agent", "subagents"]
        ---
        # Rule
        """, StandardCharsets.UTF_8);

      SprtRunner runner = newMetadataRunner(scope);
      try
      {
        runner.extractModel(new String[]{ruleFile.toString()});
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("must not combine");
        return;
      }
      throw new AssertionError("Expected agents validation failure");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that agents: ["main"] means no Codex subagent owners and therefore uses the default.
   */
  @Test
  public void usesDefaultForNoSubagentRule() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-metadata-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      Path ruleFile = tempDir.resolve("rules/common/no-subagents.md");
      Files.createDirectories(ruleFile.getParent());
      Files.writeString(ruleFile, """
        ---
        agents: ["main"]
        ---
        # Rule
        """, StandardCharsets.UTF_8);

      SprtRunner runner = newMetadataRunner(scope);
      requireThat(runner.extractModel(new String[]{ruleFile.toString()}), "model").
        isEqualTo("gpt-5.4-mini");
      requireThat(runner.extractEffort(new String[]{ruleFile.toString()}), "effort").
        isEqualTo("low");
      requireThat(runner.extractConfigSource(new String[]{ruleFile.toString()}), "configSource").
        isEqualTo("default");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex extracts the test-runner config from the matching agent wrapper for a
   * shared agent body.
   */
  @Test
  public void extractConfigUsesCodexAgentBodyOwner() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-metadata-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      writeCodexAgentIncludingCommonBody(tempDir, "plan-review-low", "gpt-5.4-mini",
        "medium", "plan-review-agent.md");
      writeCodexAgentIncludingCommonBody(tempDir, "plan-review-medium", "gpt-5.4",
        "medium", "plan-review-agent.md");
      Path agentBody = tempDir.resolve("agents/common/plan-review-agent.md");
      Files.createDirectories(agentBody.getParent());
      Files.writeString(agentBody, "Plan review body", StandardCharsets.UTF_8);

      SprtRunner runner = newMetadataRunner(scope);
      requireThat(runner.extractModel(new String[]{agentBody.toString()}), "model").
        isEqualTo("gpt-5.4-mini");
      requireThat(runner.extractEffort(new String[]{agentBody.toString()}), "effort").
        isEqualTo("medium");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex extracts the test-runner config from the unique agent body that invokes a
   * skill under test.
   */
  @Test
  public void extractConfigUsesUniqueCodexSkillInvoker() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-metadata-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      writeCodexAgent(tempDir, "work-squash", "gpt-5.4-mini", "medium");
      Path agentBody = tempDir.resolve("agents/common/work-squash.md");
      Files.createDirectories(agentBody.getParent());
      Files.writeString(agentBody, """
        When the squash is ready, invoke Skill("cat:git-amend", args="--no-edit").
        """, StandardCharsets.UTF_8);
      Path skillFile = tempDir.resolve("skills/common/git-amend/first-use.md");
      Files.createDirectories(skillFile.getParent());
      Files.writeString(skillFile, "# Git Amend", StandardCharsets.UTF_8);

      SprtRunner runner = newMetadataRunner(scope);
      requireThat(runner.extractModel(new String[]{skillFile.toString()}), "model").
        isEqualTo("gpt-5.4-mini");
      requireThat(runner.extractEffort(new String[]{skillFile.toString()}), "effort").
        isEqualTo("medium");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex extracts the weakest config when multiple agent bodies invoke a skill.
   */
  @Test
  public void usesWeakestCodexSkillInvoker() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("codex-sprt-metadata-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      writeCodexAgent(tempDir, "work-squash", "gpt-5.4-mini", "medium");
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      Path commonAgentsDir = tempDir.resolve("agents/common");
      Files.createDirectories(commonAgentsDir);
      Files.writeString(commonAgentsDir.resolve("work-squash.md"), """
        When the squash is ready, invoke Skill("cat:git-amend", args="--no-edit").
        """, StandardCharsets.UTF_8);
      Files.writeString(commonAgentsDir.resolve("work-execute.md"), """
        If a commit needs repair, invoke Skill("cat:git-amend", args="--message fixed").
        """, StandardCharsets.UTF_8);
      Path skillFile = tempDir.resolve("skills/common/git-amend/first-use.md");
      Files.createDirectories(skillFile.getParent());
      Files.writeString(skillFile, "# Git Amend", StandardCharsets.UTF_8);

      SprtRunner runner = newMetadataRunner(scope);
      requireThat(runner.extractModel(new String[]{skillFile.toString()}), "model").
        isEqualTo("gpt-5.4-mini");
      requireThat(runner.extractEffort(new String[]{skillFile.toString()}), "effort").
        isEqualTo("medium");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies Codex handler registration resolution at runtime via script function execution.
   *
   * @throws IOException if process execution fails
   * @throws InterruptedException if interrupted
   */
  @Test
  public void codexHandlerResolutionRuntime() throws IOException, InterruptedException
  {
    String scriptPath = Path.of(System.getProperty("user.dir")).getParent().resolve(
      "distribution/scripts/build-jlink-images.sh").toString();
    String output;
    int exitCode;
    try (Process process = new ProcessBuilder("bash", "-lc",
      "source '" + scriptPath + "'; set_engine_handlers codex; printf '%s\n' \"${HANDLERS[@]}\"").
      redirectErrorStream(true).start())
    {
      output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      exitCode = process.waitFor();
    }
    requireThat(exitCode, "exitCode").isEqualTo(0);
    requireThat(output, "output").contains("codex-runner:io.github.cowwoc.cat.codex.engine.CodexRunner");
    requireThat(output, "output").contains("sprt-runner:io.github.cowwoc.cat.codex.engine.CodexSprtRunner");
    requireThat(output, "output").doesNotContain("sprt-runner:io.github.cowwoc.cat.common.cli/");
  }

  /**
   * Verifies that grading uses the instruction-grader-agent TOML model and effort, not the
   * model and effort used by the test run being graded.
   *
   * @throws Exception if setup or process execution fails
   */
  @Test
  public void graderUsesCodexAgentConfig() throws Exception
  {
    Path tempDir = Files.createTempDirectory("codex-grader-agent-config-");
    try (CliTool scope = new TestCodexTool(tempDir, tempDir))
    {
      Path agentFile = tempDir.resolve(
        "client/plugin/agents/codex/instruction-grader-agent.toml");
      Files.createDirectories(agentFile.getParent());
      Files.writeString(agentFile, """
        name = "cat-instruction-grader-agent"
        nickname_candidates = ["instruction-grader-agent"]
        model = "gpt-5.4-mini"
        model_reasoning_effort = "medium"
        """, StandardCharsets.UTF_8);

      Path capturedArgs = tempDir.resolve("captured-args.txt");
      Path launcher = tempDir.resolve(
        "client/distribution/target/jlink/codex/bin/codex-runner");
      writeFakeLauncher(launcher, capturedArgs, "--output");

      Path promptFile = tempDir.resolve("grader-prompt.txt");
      Files.writeString(promptFile, "grade this", StandardCharsets.UTF_8);

      int exitCode = SharedSecrets.runGrader(scope, "2.1.87", promptFile,
        "gpt-5.5", "xhigh", tempDir.toString(),
        tempDir.resolve("grade.json").toString(),
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

      requireThat(exitCode, "exitCode").isEqualTo(0);
      String[] args = Files.readString(capturedArgs, StandardCharsets.UTF_8).strip().split("\n");
      requireThat(valueAfter(args, "--model"), "model").isEqualTo("gpt-5.4-mini");
      requireThat(valueAfter(args, "--effort"), "effort").isEqualTo("medium");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  private static SprtRunner newMetadataRunner(CliTool scope)
  {
    return new SprtRunner(scope, "2.1.87", new CodexSprtMetadataResolver(scope));
  }

  private static void writeCodexAgent(Path pluginRoot, String agentName, String model,
    String effort) throws IOException
  {
    Path agentsDir = pluginRoot.resolve("agents/codex");
    Files.createDirectories(agentsDir);
    Files.writeString(agentsDir.resolve(agentName + ".toml"), """
      name = "cat-%s"
      model = "%s"
      model_reasoning_effort = "%s"
      """.formatted(agentName, model, effort), StandardCharsets.UTF_8);
  }

  private static void writeCodexAgentIncludingCommonBody(Path pluginRoot, String agentName,
    String model, String effort, String commonBodyName) throws IOException
  {
    Path agentsDir = pluginRoot.resolve("agents/codex");
    Files.createDirectories(agentsDir);
    Files.writeString(agentsDir.resolve(agentName + ".toml"), """
      name = "cat-%s"
      model = "%s"
      model_reasoning_effort = "%s"
      developer_instructions = '''
      <!-- cat:include ../common/%s -->
      '''
      """.formatted(agentName, model, effort, commonBodyName), StandardCharsets.UTF_8);
  }

  private static void writeFakeLauncher(Path launcher, Path capturedArgs, String help)
    throws IOException
  {
    Files.createDirectories(launcher.getParent());
    Files.writeString(launcher, """
      #!/usr/bin/env bash
      if [ "$1" = "--help" ]; then
        printf '%%s\\n' '%s'
        exit 0
      fi
      printf '%%s\\n' "$@" > '%s'
      exit 0
      """.formatted(help, capturedArgs), StandardCharsets.UTF_8);
    if (!launcher.toFile().setExecutable(true))
      throw new IOException("Unable to make launcher executable: " + launcher);
  }

  private static String valueAfter(String[] args, String flag)
  {
    for (int index = 0; index + 1 < args.length; ++index)
    {
      if (args[index].equals(flag))
        return args[index + 1];
    }
    throw new AssertionError("Missing flag: " + flag);
  }
}
