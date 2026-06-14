/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.skills.SharedSecrets;
import io.github.cowwoc.cat.tool.skills.SprtRunner;
import io.github.cowwoc.cat.tool.MainCliTool;
import org.testng.SkipException;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link SprtRunner}.
 * <p>
 * Each test is self-contained with no shared state.
 */
public final class SprtRunnerTest
{
  /**
   * Verifies runtime unknown-command diagnostics from SPRT dispatch.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if interrupted
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*SprtRunner: unknown command: unknown-command.*")
  public void runtimeUnknownCommandDiagnostics() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("sprt-runtime-diagnostics-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"unknown-command"},
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extract-units returns line-numbered body when file has frontmatter.
   */
  @Test
  public void extractUnitsWithFrontmatter() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create a skill file with frontmatter
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        model: haiku
        ---
        # Step 1
        Do something.
        # Step 2
        Do more.
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.extractUnits(new String[]{skillFile.toString()});

      // Body starts at line 5 (3 frontmatter lines + 1 closing ---)
      // Actually: line 1 "---", line 2 "description:...", line 3 "model:...", line 4 "---" = 4 fm lines
      // Body lines start at line 5
      requireThat(result, "result").contains("5\t# Step 1");
      requireThat(result, "result").contains("6\tDo something.");
      requireThat(result, "result").contains("7\t# Step 2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extract-units returns line-numbered body when file has no frontmatter.
   */
  @Test
  public void extractUnitsWithoutFrontmatter() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, """
        # Step 1
        Do something.
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.extractUnits(new String[]{skillFile.toString()});

      requireThat(result, "result").contains("1\t# Step 1");
      requireThat(result, "result").contains("2\tDo something.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extract-units throws when the file does not exist.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*file not found.*")
  public void extractUnitsFileNotFound() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.extractUnits(new String[]{"/nonexistent/skill.md"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extract-model reads the model field from frontmatter.
   */
  @Test
  public void extractModelFromFrontmatter() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        model: sonnet
        ---
        # Body
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String model = runner.extractModel(new String[]{skillFile.toString()});
      requireThat(model, "model").isEqualTo("claude-sonnet-4-5");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extract-model uses the Claude default when no model field is present in frontmatter.
   */
  @Test
  public void extractModelRejectsSkillWithoutModel() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        ---
        # Body
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String model = runner.extractModel(new String[]{skillFile.toString()});
      requireThat(model, "model").isEqualTo("claude-haiku-4-5");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex extract-model uses the fixed test-runner default because Codex skills do
   * not support model frontmatter.
   */
  @Test
  public void extractModelUsesCodexDefault() throws IOException, InterruptedException
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
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        model: gpt-5.5
        ---
        # Body
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String model = runner.extractModel(new String[]{skillFile.toString()});
      requireThat(model, "model").isEqualTo("gpt-5.4-mini");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Codex extract-effort uses the fixed test-runner default because Codex skills do
   * not support effort frontmatter.
   */
  @Test
  public void extractEffortUsesCodexDefault() throws IOException, InterruptedException
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
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        effort: high
        ---
        # Body
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
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
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      Path ruleFile = tempDir.resolve("rules/common/configuration-reads.md");
      Files.createDirectories(ruleFile.getParent());
      Files.writeString(ruleFile, """
        ---
        agents: ["cat:work-execute"]
        ---
        # Rule
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
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
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      writeCodexAgent(tempDir, "work-merge", "gpt-5.4-mini", "medium");
      writeCodexAgent(tempDir, "instruction-builder-implement-agent", "gpt-5.4-mini", "low");
      Path ruleFile = tempDir.resolve("rules/common/multi-agent.md");
      Files.createDirectories(ruleFile.getParent());
      Files.writeString(ruleFile, """
        ---
        agents: ["cat:work-execute", "cat:work-merge", "cat:instruction-builder-implement-agent"]
        ---
        # Rule
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
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
   * Verifies that rules without an agents restriction use the weakest config across all Codex agents.
   */
  @Test
  public void usesWeakestCodexBroadRule() throws IOException, InterruptedException
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
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      writeCodexAgent(tempDir, "work-merge", "gpt-5.4-mini", "medium");
      writeCodexAgent(tempDir, "instruction-builder-implement-agent", "gpt-5.4-mini", "low");
      Path ruleFile = tempDir.resolve("rules/common/broad-rule.md");
      Files.createDirectories(ruleFile.getParent());
      Files.writeString(ruleFile, "# Rule", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
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

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
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
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      Path ruleFile = tempDir.resolve("rules/common/invalid-agents.md");
      Files.createDirectories(ruleFile.getParent());
      Files.writeString(ruleFile, """
        ---
        agents: ["cat:missing-agent", "subagents"]
        ---
        # Rule
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
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
      writeCodexAgent(tempDir, "work-execute", "gpt-5.5", "high");
      Path ruleFile = tempDir.resolve("rules/common/no-subagents.md");
      Files.createDirectories(ruleFile.getParent());
      Files.writeString(ruleFile, """
        ---
        agents: ["main"]
        ---
        # Rule
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
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
      writeCodexAgent(tempDir, "plan-review-agent", "gpt-5.4", "medium");
      Path agentBody = tempDir.resolve("agents/common/plan-review-agent.md");
      Files.createDirectories(agentBody.getParent());
      Files.writeString(agentBody, "Plan review body", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      requireThat(runner.extractModel(new String[]{agentBody.toString()}), "model").
        isEqualTo("gpt-5.4");
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
      writeCodexAgent(tempDir, "work-squash", "gpt-5.4-mini", "medium");
      Path agentBody = tempDir.resolve("agents/common/work-squash.md");
      Files.createDirectories(agentBody.getParent());
      Files.writeString(agentBody, """
        When the squash is ready, invoke Skill("cat:git-amend", args="--no-edit").
        """, StandardCharsets.UTF_8);
      Path skillFile = tempDir.resolve("skills/common/git-amend/first-use.md");
      Files.createDirectories(skillFile.getParent());
      Files.writeString(skillFile, "# Git Amend", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
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

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
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
   * Verifies that map-units correctly partitions test cases based on changed unit IDs.
   */
  @Test
  public void mapUnitsPartitionsCorrectly() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create .md test case files in a test directory; file stem = test case ID
      Path testDir = tempDir.resolve("test-cases");
      Files.createDirectories(testDir);
      // filename stem = semantic unit ID; tc1 and tc3 are changed, tc2 is not
      Files.writeString(testDir.resolve("tc1.md"), """
        ---
        category: REQUIREMENT
        ---

        ## Turn 1

        Test prompt for tc1.

        ## Assertions

        1. The Skill tool was invoked
        """, StandardCharsets.UTF_8);
      Files.writeString(testDir.resolve("tc2.md"), """
        ---
        category: REQUIREMENT
        ---

        ## Turn 1

        Test prompt for tc2.

        ## Assertions

        1. The Skill tool was invoked
        """, StandardCharsets.UTF_8);
      Files.writeString(testDir.resolve("tc3.md"), """
        ---
        category: REQUIREMENT
        ---

        ## Turn 1

        Test prompt for tc3.

        ## Assertions

        1. The Skill tool was invoked
        """, StandardCharsets.UTF_8);

      String changedUnitsJson = "[\"tc1\", \"tc3\"]";
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.mapUnits(new String[]{testDir.toString(), changedUnitsJson});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      // tc1 and tc3 are changed, tc2 is unchanged
      JsonNode rerun = root.path("rerun_test_case_ids");
      JsonNode carryforward = root.path("carryforward_test_case_ids");

      requireThat(rerun.size(), "rerunCount").isEqualTo(2);
      requireThat(carryforward.size(), "carryforwardCount").isEqualTo(1);
      requireThat(carryforward.get(0).asString(), "carryforwardTc").isEqualTo("tc2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that map-units carries all test cases forward when no units changed.
   */
  @Test
  public void mapUnitsNoChangedUnitsCarriesForwardAll() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path testDir = tempDir.resolve("test-cases");
      Files.createDirectories(testDir);
      Files.writeString(testDir.resolve("tc1.md"), """
        ---
        category: REQUIREMENT
        ---

        ## Turn 1

        Test prompt for tc1.

        ## Assertions

        1. The Skill tool was invoked
        """, StandardCharsets.UTF_8);
      Files.writeString(testDir.resolve("tc2.md"), """
        ---
        category: REQUIREMENT
        ---

        ## Turn 1

        Test prompt for tc2.

        ## Assertions

        1. The Skill tool was invoked
        """, StandardCharsets.UTF_8);

      String changedUnitsJson = "[]";
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.mapUnits(new String[]{testDir.toString(), changedUnitsJson});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("rerun_test_case_ids").size(), "rerunCount").isEqualTo(0);
      requireThat(root.path("carryforward_test_case_ids").size(), "carryforwardCount").isEqualTo(2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that init-sprt creates fresh SPRT state for rerun IDs with no prior.
   * <p>
   * SPRT parameters: alpha=0.05, beta=0.05, p0=0.95, p1=0.85.
   * Boundaries: SPRT_ACCEPT = ln((1-beta)/alpha) ≈ 2.944, SPRT_REJECT = ln(beta/(1-alpha)) ≈ -2.944.
   */
  @Test
  public void initSprtFreshStateNoPrior() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      String rerunJson = "[\"TC1\",\"TC2\"]";
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      Path sprtStatePath = tempDir.resolve(".cat/work/sprt-state.json");
      String result = runner.initSprt(new String[]{
        sprtStatePath.toString(), rerunJson, "none", "claude-haiku-4-5", "test-session-id"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode resultNode = mapper.readTree(result);
      requireThat(resultNode.path("ok").asBoolean(), "ok").isTrue();

      // State must have been written to the file
      requireThat(Files.exists(sprtStatePath), "stateFileExists").isTrue();
      JsonNode root = mapper.readTree(sprtStatePath.toFile());
      JsonNode sprtState = root.path("sprt_state");

      JsonNode tc1 = sprtState.path("TC1");
      requireThat(tc1.path("log_ratio").asDouble(), "logRatio").isEqualTo(0.0);
      requireThat(tc1.path("passes").asInt(), "passes").isEqualTo(0);
      requireThat(tc1.path("decision").asString(), "decision").isEqualTo("INCONCLUSIVE");
      requireThat(tc1.path("carried_forward").asBoolean(), "carriedForward").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run-sprt is the canonical public SPRT command.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*SprtRunner run-sprt: expected at least 4 arguments.*")
  public void runSprtCommandIsRecognized() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run-sprt is recognized when invoked under the Codex engine.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*SprtRunner run-sprt: expected at least 4 arguments.*")
  public void runSprtCommandIsRecognizedForCodexEngine() throws IOException, InterruptedException
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
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run-sprt requires effort to be specified explicitly.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*SprtRunner run-sprt: expected at least 4 arguments.*")
  public void runSprtRejectsMissingEffort() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt", tempDir.toString(), "tests", "claude-haiku-4-5"},
        System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run-sprt rejects test directories that traverse outside the worktree before any
   * runner work starts.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*outside.*")
  public void runSprtRejectsRelativeTestDirOutside() throws IOException, InterruptedException
  {
    Path parent = Files.createTempDirectory("test-skill-test-runner-parent-");
    Path worktree = parent.resolve("worktree");
    Path outside = parent.resolve("outside");
    try (var scope = new TestClaudeTool(worktree, worktree))
    {
      Files.createDirectories(worktree);
      Files.createDirectories(outside);
      Files.writeString(outside.resolve("test-case.md"), "# Test", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt", worktree.toString(), "../outside",
        "claude-haiku-4-5", "high"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(parent);
    }
  }

  /**
   * Verifies that run-sprt rejects symlinked test directories that resolve outside the worktree.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*outside.*")
  public void runSprtRejectsSymlinkedTestDirOutside() throws IOException, InterruptedException
  {
    Path parent = Files.createTempDirectory("test-skill-test-runner-parent-");
    Path worktree = parent.resolve("worktree");
    Path outside = parent.resolve("outside");
    try (var scope = new TestClaudeTool(worktree, worktree))
    {
      Files.createDirectories(worktree);
      Files.createDirectories(outside);
      Files.writeString(outside.resolve("test-case.md"), "# Test", StandardCharsets.UTF_8);
      try
      {
        Files.createSymbolicLink(worktree.resolve("linked-tests"), outside);
      }
      catch (UnsupportedOperationException | IOException e)
      {
        throw new SkipException("Symbolic links are not available in this test environment", e);
      }

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt", worktree.toString(), "linked-tests",
        "claude-haiku-4-5", "high"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(parent);
    }
  }

  /**
   * Verifies that run-sprt validates Claude model IDs at the command entrypoint.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid Claude model ID.*")
  public void runSprtRejectsUnsupportedClaudeModelAt()
    throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt", tempDir.toString(), "tests", "gpt-5.4",
        "high"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run-sprt validates Codex model IDs at the command entrypoint.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid Codex model ID.*")
  public void runSprtRejectsUnsupportedCodexModelAt()
    throws IOException, InterruptedException
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
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt", tempDir.toString(), "tests", "claude-haiku-4-5",
        "high"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run-sprt validates effort values at the command entrypoint.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid effort.*")
  public void runSprtRejectsUnsupportedEffortAt()
    throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt", tempDir.toString(), "tests", "claude-haiku-4-5",
        "extreme"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run-sprt validates Codex effort values at the command entrypoint.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid effort.*")
  public void runSprtRejectsUnsupportedCodexEffortAt()
    throws IOException, InterruptedException
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
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt", tempDir.toString(), "tests", "gpt-5.4",
        "extreme"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run-sprt argument parsing keeps effort immediately after the model ID.
   */
  @Test
  public void parseRunSprtArgsRequiresExplicitEffort()
  {
    String[] parsed = SharedSecrets.parseRunSprtArgs(new String[]{
      "/tmp/worktree", "client/plugin/tests/skills/learn", "claude-haiku-4-5", "high"});

    requireThat(parsed, "parsed").length().isEqualTo(5);
    requireThat(parsed[0], "worktreePath").isEqualTo("/tmp/worktree");
    requireThat(parsed[1], "testDir").isEqualTo("client/plugin/tests/skills/learn");
    requireThat(parsed[2], "testModel").isEqualTo("claude-haiku-4-5");
    requireThat(parsed[3], "testEffort").isEqualTo("high");
    requireThat(parsed[4], "sessionId").isEqualTo("test-session");
  }

  /**
   * Verifies that run-sprt does not accept a missing effort argument.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*SprtRunner run-sprt: expected at least 4 arguments.*")
  public void parseRunSprtArgsRejectsMissingEffort()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", "claude-haiku-4-5"});
  }

  /**
   * Verifies that run-sprt rejects too many arguments.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*session_id is derived from active engine scope.*")
  public void parseRunSprtArgsRejectsExtraArguments()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", "claude-haiku-4-5",
      "high", "test-session-id", "extra"});
  }

  /**
   * Verifies that run-sprt rejects a null argument array.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*args.*")
  public void parseRunSprtArgsRejectsNullArguments()
  {
    SharedSecrets.parseRunSprtArgs(null);
  }

  /**
   * Verifies that run-sprt rejects a null model argument.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*test_model.*")
  public void parseRunSprtArgsRejectsNullModel()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", null, "high"});
  }

  /**
   * Verifies that run-sprt rejects a blank worktree path.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*worktree_path.*")
  public void parseRunSprtArgsRejectsBlankWorktreePath()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{" ", "tests", "claude-haiku-4-5", "high"});
  }

  /**
   * Verifies that run-sprt rejects a null test directory.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*test_dir.*")
  public void parseRunSprtArgsRejectsNullTestDirectory()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", null, "claude-haiku-4-5", "high"});
  }

  /**
   * Verifies that run-sprt rejects a blank test directory.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*test_dir.*")
  public void parseRunSprtArgsRejectsBlankTest()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", " ", "claude-haiku-4-5", "high"});
  }

  /**
   * Verifies that run-sprt rejects a blank model argument.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*test_model.*")
  public void parseRunSprtArgsRejectsBlankModel()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", " ", "high"});
  }

  /**
   * Verifies that run-sprt rejects a blank effort argument.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*effort.*")
  public void parseRunSprtArgsRejectsBlankEffort()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", "claude-haiku-4-5", " "});
  }

  /**
   * Verifies that run-sprt rejects an explicitly supplied session ID.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*session_id is derived from active engine scope.*")
  public void parseRunSprtArgsRejectsExplicitSessionId()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", "claude-haiku-4-5",
      "high", " "});
  }

  /**
   * Verifies that run-sprt rejects any explicitly supplied session ID payload.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*session_id is derived from active engine scope.*")
  public void parseRunSprtArgsRejectsUnsafeSessionId()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", "claude-haiku-4-5",
      "high", "../outside"});
  }

  /**
   * Verifies that batch execution remains an internal implementation detail driven by run-sprt.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*unknown command: run-sprt-batch.*")
  public void runSprtBatchCommandIsInternal() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt-batch"}, new PrintStream(new ByteArrayOutputStream(), true,
        StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that obsolete SPRT command names are no longer accepted.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*unknown command: run-full-sprt.*")
  public void runFullSprtCommandIsRemoved() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-full-sprt"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the focused single-test SPRT command is no longer accepted.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*unknown command: run-single-test.*")
  public void runSingleTestCommandIsRemoved() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-single-test"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that init-sprt writes model_id to the state file so subsequent calls with the state
   * as prior results can validate model consistency.
   */
  @Test
  public void initSprtWritesModelId() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      Path sprtStatePath = tempDir.resolve(".cat/work/sprt-state.json");
      runner.initSprt(new String[]{
        sprtStatePath.toString(), "[\"tc1\"]", "none", "claude-haiku-4-5", "test-session-id"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(sprtStatePath.toFile());
      requireThat(root.path("model_id").asString(), "modelId").isEqualTo("claude-haiku-4-5");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that init-sprt sets log_ratio to PRIOR_BOOST when --prior-boost is enabled and the
   * prior test case has ACCEPT decision.
   * <p>
   * PRIOR_BOOST = 1.112, equivalent to 10 prior PASS observations (10 × SPRT_LOG_PASS = 10 × 0.1112).
   */
  @Test
  public void initSprtUsePriorBoostWithAcceptPrior() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Prior instruction-test has TC1 as ACCEPT
      Path priorPath = tempDir.resolve("prior.json");
      Files.writeString(priorPath, """
        {"model_id":"claude-haiku-4-5","test_cases":[
          {"test_case_id":"TC1","log_ratio":3.0,"passes":10,"fails":0,"runs":10,"decision": "ACCEPT"}
        ]}
        """, StandardCharsets.UTF_8);

      String rerunJson = "[\"TC1\"]";
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      Path sprtStatePath = tempDir.resolve(".cat/work/sprt-state.json");
      runner.initSprt(new String[]{
        sprtStatePath.toString(), rerunJson, priorPath.toString(),
        "claude-haiku-4-5", "test-session-id", "--prior-boost"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(sprtStatePath.toFile());
      JsonNode tc1 = root.path("sprt_state").path("TC1");

      // When --prior-boost is set and prior decision is ACCEPT, initial log_ratio should be PRIOR_BOOST (1.112)
      double logRatio = tc1.path("log_ratio").asDouble();
      requireThat(logRatio, "logRatio").isBetween(1.111, true, 1.113, true);
      requireThat(tc1.path("passes").asInt(), "passes").isEqualTo(0);
      requireThat(tc1.path("decision").asString(), "decision").isEqualTo("INCONCLUSIVE");
      requireThat(tc1.path("carried_forward").asBoolean(), "carriedForward").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that init-sprt initializes with default values (log_ratio=0.0) when the prior path is 'none'
   * (no prior instruction-test available).
   */
  @Test
  public void initSprtWithEmptyPrior() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Pass "none" as prior path to indicate no prior instruction-test
      String rerunJson = "[\"TC1\"]";
      Path sprtStatePath = tempDir.resolve(".cat/work/sprt-state.json");
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.initSprt(new String[]{sprtStatePath.toString(), rerunJson, "none", "claude-haiku-4-5",
        "test-session-id"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(sprtStatePath.toFile());
      JsonNode tc1 = root.path("sprt_state").path("TC1");

      // Without a prior, log_ratio defaults to 0.0
      requireThat(tc1.path("log_ratio").asDouble(), "logRatio").isEqualTo(0.0);
      requireThat(tc1.path("passes").asInt(), "passes").isEqualTo(0);
      requireThat(tc1.path("decision").asString(), "decision").isEqualTo("INCONCLUSIVE");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that init-sprt deletes stale test-run outputs when cached results are invalidated by a model change.
   */
  @Test
  public void initSprtDeletesStaleOutputsOnModelChange() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sprtStatePath = tempDir.resolve(".cat/work/sprt-state.json");
      Path priorPath = tempDir.resolve("prior.json");
      Path staleDir = tempDir.resolve(".cat/work/test-runs/test-session-id");
      Files.createDirectories(staleDir);
      Files.writeString(staleDir.resolve("stale.json"), "stale", StandardCharsets.UTF_8);
      Files.writeString(priorPath, """
        {"model_id":"claude-sonnet-4-5","effort":"high","failed_test_ids":[],"sprt":{"test_cases":[]}}
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.initSprt(new String[]{sprtStatePath.toString(), "[\"TC1\"]", priorPath.toString(),
        "claude-haiku-4-5", "test-session-id", "--effort", "high"});

      requireThat(Files.exists(staleDir), "staleDirExists").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SPRT runner launcher timeouts fail fast while waiting for process completion.
   */
  @Test
  public void sprtLauncherTimeoutWhileWaiting() throws Exception
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path runnerWorktree = createFakeRunnerWorktree(tempDir, """
        #!/usr/bin/env bash
        sleep 5
        """);
      SprtRunner runner = new SprtRunner(scope, "2.1.87", Duration.ofMillis(100));

      try
      {
        runSprtEngineCommandWithRetry(runner, new String[0], runnerWorktree.toString(),
          new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        throw new AssertionError("Expected timeout while waiting for fake launcher");
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "errorMessage").
          contains("Timeout while waiting for claude-runner");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SPRT runner launcher timeouts fail fast while draining stdout.
   */
  @Test
  public void sprtLauncherTimeoutWhileDraining() throws Exception
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path runnerWorktree = createFakeRunnerWorktree(tempDir, """
        #!/usr/bin/env bash
        printf 'hello\\n'
        """);
      SprtRunner runner = new SprtRunner(scope, "2.1.87", Duration.ofMillis(100));
      Duration elapsed;
      try (BlockingPrintStream out = new BlockingPrintStream())
      {
        long startTime = System.nanoTime();
        try
        {
          runSprtEngineCommandWithRetry(runner, new String[0], runnerWorktree.toString(),
            out);
          throw new AssertionError("Expected timeout while draining stdout for fake launcher");
        }
        catch (IOException e)
        {
          requireThat(e.getMessage(), "errorMessage").
            contains("Timeout while draining stdout for claude-runner");
        }
        finally
        {
          out.release();
        }
        elapsed = Duration.ofNanos(System.nanoTime() - startTime);

        requireThat(out.started.await(1, TimeUnit.SECONDS), "stdoutBlocked").isTrue();
      }
      requireThat(elapsed.compareTo(Duration.ofMillis(220)), "elapsed").isLessThan(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that SPRT runner launcher output failures abort promptly instead of waiting for process timeout.
   */
  @Test
  public void sprtLauncherReturnsWhenOutputFails() throws Exception
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path runnerWorktree = createFakeRunnerWorktree(tempDir, """
        #!/usr/bin/env bash
        printf 'hello\\n'
        sleep 5
        """);
      SprtRunner runner = new SprtRunner(scope, "2.1.87", Duration.ofMillis(200));
      Duration elapsed;
      try (ThrowingPrintStream out = new ThrowingPrintStream())
      {
        long startTime = System.nanoTime();
        try
        {
          runSprtEngineCommandWithRetry(runner, new String[0], runnerWorktree.toString(), out);
          throw new AssertionError("Expected fake launcher output to fail");
        }
        catch (IllegalStateException e)
        {
          requireThat(e.getMessage(), "errorMessage").contains("output boom");
        }
        elapsed = Duration.ofNanos(System.nanoTime() - startTime);
      }
      requireThat(elapsed.compareTo(Duration.ofMillis(320)), "elapsed").isLessThan(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check-boundary handles an empty test directory (zero test cases) gracefully:
   * the decision defaults to INCONCLUSIVE and log_ratio to 0.0.
   */
  @Test
  public void checkBoundaryWithZeroTestCases() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // SPRT state with no entries (empty object)
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{}}
        """, StandardCharsets.UTF_8);

      // check-boundary on an ID not present in state returns default INCONCLUSIVE values
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.checkBoundary(new String[]{statePath.toString(), "NONEXISTENT"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("test_case_id").asString(), "test_case_id").isEqualTo("NONEXISTENT");
      requireThat(root.path("decision").asString(), "decision").isEqualTo("INCONCLUSIVE");
      requireThat(root.path("log_ratio").asDouble(), "log_ratio").isEqualTo(0.0);
      requireThat(root.path("runs").asInt(), "runs").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that persist-artifacts throws IllegalArgumentException when the artifacts directory
   * contains no .md test case files (simulates corruption or empty artifacts directory).
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*no .md test case files found.*")
  public void persistArtifactsRejectsEmptyArtifactsDir() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create skill file
      Path skillFile = repoDir.resolve("skill.md");
      Files.writeString(skillFile, "---\ndescription: Test\nmodel: haiku\n---\n# Body\n",
        StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "skill.md");
      TestUtils.runGit(repoDir, "commit", "-m", "add skill");

      // Create artifacts dir without any .md test case files (simulates corruption/missing files)
      Path artifactsDir = tempDir.resolve("artifacts");
      Files.createDirectories(artifactsDir);
      // Intentionally do NOT create any .md test case files

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.persistArtifacts(
        new String[]{"skill.md", artifactsDir.toString(), "sess1", repoDir.toString(), "initial"},
        System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that update-sprt correctly updates log_ratio and decision after a PASS.
   * <p>
   * SPRT parameters: alpha=0.05, beta=0.05, p0=0.95, p1=0.85.
   * SPRT_LOG_PASS = ln(p0/p1) = ln(0.95/0.85) ≈ 0.1112.
   * SPRT_ACCEPT = ln((1-beta)/alpha) = ln(19) ≈ 2.944.
   */
  @Test
  public void updateSprtPassUpdatesLogRatioAndDecision() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create initial state file with TC1 in INCONCLUSIVE state near ACCEPT boundary
      Path statePath = tempDir.resolve("sprt_state.json");
      // log_ratio 2.9 is just below ACCEPT (2.944); one PASS (0.1112) should push it over
      Files.writeString(statePath, """
        {"sprt_state":{"TC1":{"log_ratio":2.9,"passes":10,"fails":0,"runs":10,
        "decision": "INCONCLUSIVE","carried_forward":false,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.updateSprt(new String[]{statePath.toString(), "TC1", "true"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(statePath.toFile());
      JsonNode tc1 = root.path("sprt_state").path("TC1");

      requireThat(tc1.path("decision").asString(), "decision").isEqualTo("ACCEPT");
      requireThat(tc1.path("passes").asInt(), "passes").isEqualTo(11);
      requireThat(tc1.path("runs").asInt(), "runs").isEqualTo(11);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that update-sprt correctly updates log_ratio and decision after a FAIL.
   * <p>
   * SPRT parameters: alpha=0.05, beta=0.05, p0=0.95, p1=0.85.
   * SPRT_LOG_FAIL = ln((1-p0)/(1-p1)) = ln(0.05/0.15) ≈ -1.0986.
   * SPRT_REJECT = ln(beta/(1-alpha)) = ln(0.0526) ≈ -2.944.
   */
  @Test
  public void updateSprtFailUpdatesLogRatioAndDecision() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      // log_ratio -2.9 is just above REJECT (-2.944); one FAIL (-1.0986) should push it below
      Files.writeString(statePath, """
        {"sprt_state":{"TC1":{"log_ratio":-2.9,"passes":0,"fails":10,"runs":10,
        "decision": "INCONCLUSIVE","carried_forward":false,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.updateSprt(new String[]{statePath.toString(), "TC1", "false"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(statePath.toFile());
      JsonNode tc1 = root.path("sprt_state").path("TC1");

      requireThat(tc1.path("decision").asString(), "decision").isEqualTo("REJECT");
      requireThat(tc1.path("fails").asInt(), "fails").isEqualTo(11);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that update-sprt preserves top-level fields (e.g., model_id) when rewriting the state file.
   * <p>
   * The model_id field written by init-sprt must survive round-trips through update-sprt so that
   * downstream commands (check-boundary, write-test-results) can still read the model identity.
   */
  @Test
  public void updateSprtPreservesModelId() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath,
        "{\"model_id\":\"claude-haiku-4-5\",\"sprt_state\":{\"TC1\":{\"log_ratio\":0.0," +
        "\"passes\":0,\"fails\":0,\"runs\":0,\"decision\":\"INCONCLUSIVE\"," +
        "\"carried_forward\":false,\"smoke_runs_done\":0}}}",
        StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.updateSprt(new String[]{statePath.toString(), "TC1", "true"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(statePath.toFile());
      requireThat(root.path("model_id").asString(), "model_id").isEqualTo("claude-haiku-4-5");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check-boundary returns correct values for a known state.
   */
  @Test
  public void checkBoundaryReturnsCorrectFields() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{"TC1":{"log_ratio":1.5,"passes":5,"fails":2,"runs":7,
        "decision": "INCONCLUSIVE","carried_forward":true,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.checkBoundary(new String[]{statePath.toString(), "TC1"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("test_case_id").asString(), "test_case_id").isEqualTo("TC1");
      requireThat(root.path("decision").asString(), "decision").isEqualTo("INCONCLUSIVE");
      requireThat(root.path("log_ratio").asDouble(), "log_ratio").isEqualTo(1.5);
      requireThat(root.path("runs").asInt(), "runs").isEqualTo(7);
      requireThat(root.path("smoke_runs_done").asInt(), "smoke_runs_done").isEqualTo(3);
      requireThat(root.path("carried_forward").asBoolean(), "carried_forward").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that smoke-status correctly identifies in_smoke_phase when smoke_runs_done less than SMOKE_RUNS.
   */
  @Test
  public void smokeStatusInSmokePhase() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{"TC1":{"log_ratio":0.0,"passes":1,"fails":0,"runs":1,
        "decision": "INCONCLUSIVE","carried_forward":false,"smoke_runs_done":1}}}
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.smokeStatus(new String[]{statePath.toString(), "TC1"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("in_smoke_phase").asBoolean(), "in_smoke_phase").isTrue();
      requireThat(root.path("smoke_runs_done").asInt(), "smoke_runs_done").isEqualTo(1);
      requireThat(root.path("smoke_runs_remaining").asInt(), "smoke_runs_remaining").isEqualTo(2);
      requireThat(root.path("escalate_to_full_sprt").asBoolean(), "escalate").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that smoke-status correctly signals escalation when smoke phase complete but INCONCLUSIVE.
   */
  @Test
  public void smokeStatusEscalates() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{"TC1":{"log_ratio":0.0,"passes":1,"fails":2,"runs":3,
        "decision": "INCONCLUSIVE","carried_forward":false,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.smokeStatus(new String[]{statePath.toString(), "TC1"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("in_smoke_phase").asBoolean(), "in_smoke_phase").isFalse();
      requireThat(root.path("escalate_to_full_sprt").asBoolean(), "escalate").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that merge-results produces ACCEPT overall_decision": "accept.
   */
  @Test
  public void mergeResultsAllAccept() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{
          "TC1":{"log_ratio":3.0,"passes":10,"fails":0,"runs":10,
                 "decision": "ACCEPT","carried_forward":false,"smoke_runs_done":3},
          "TC2":{"log_ratio":3.0,"passes":10,"fails":0,"runs":10,
                 "decision": "ACCEPT","carried_forward":true,"smoke_runs_done":3}
        }}
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.mergeResults(
        new String[]{statePath.toString(), "none", "[]", "claude-haiku-4-5"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("overall_decision").asString(), "overall_decision").isEqualTo("ACCEPT");
      requireThat(root.path("incremental").asBoolean(), "incremental").isTrue();
      requireThat(root.path("test_cases").size(), "testCasesCount").isEqualTo(2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that merge-results produces reject overall_decision.
   */
  @Test
  public void mergeResultsAnyRejectProducesReject() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{
          "TC1":{"log_ratio":3.0,"passes":10,"fails":0,"runs":10,
                 "decision": "ACCEPT","carried_forward":false,"smoke_runs_done":3},
          "TC2":{"log_ratio":-3.0,"passes":0,"fails":10,"runs":10,
                 "decision": "REJECT","carried_forward":false,"smoke_runs_done":3}
        }}
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.mergeResults(
        new String[]{statePath.toString(), "none", "[]", "claude-haiku-4-5"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("overall_decision").asString(), "overall_decision").isEqualTo("REJECT");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() dispatches to the correct subcommand and produces JSON output.
   */
  @Test
  public void runDispatchesToSubcommand() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path testDir = tempDir.resolve("test-cases");
      Files.createDirectories(testDir);
      Files.writeString(testDir.resolve("tc1.md"), """
        ---
        category: REQUIREMENT
        ---

        ## Turn 1

        Test prompt for tc1.

        ## Assertions

        1. The Skill tool was invoked
        """, StandardCharsets.UTF_8);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(baos, false, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"map-units", testDir.toString(), "[]"}, printStream);
      printStream.flush();

      String output = baos.toString(StandardCharsets.UTF_8).strip();
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(output);
      requireThat(root.path("all_test_case_ids").size(), "allIdsCount").isEqualTo(1);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that clean test run output passes contamination check.
   */
  @Test
  public void checkRunContaminationCleanOutputPasses() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path stdoutFile = tempDir.resolve("stdout.txt");
      Files.writeString(stdoutFile, "The skill ran and produced correct output.", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.checkRunContamination(new String[]{stdoutFile.toString()});

      requireThat(result.strip(), "result").isEqualTo("status=PASS");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that output mentioning "previous run" triggers contamination detection.
   */
  @Test
  public void checkRunContaminationPriorRunPhraseFails() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path stdoutFile = tempDir.resolve("stdout.txt");
      Files.writeString(stdoutFile, "Based on the previous run, I will apply the same approach.",
        StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.checkRunContamination(new String[]{stdoutFile.toString()});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      requireThat(pairs.get("status"), "status").isEqualTo("FAIL");
      requireThat(pairs.get("violation"), "violation").contains("previous run");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that write-test-results writes test-results.json and commits.
   */
  @Test
  public void writeTestResultsWritesAndCommits() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("test-branch");
    try
    {
      // Create test dir with a placeholder file so git can track it
      Path testDir = repoDir.resolve("my-test-dir");
      Files.createDirectories(testDir);
      Files.writeString(testDir.resolve(".gitkeep"), "", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add test dir");

      Path statePath = repoDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"model_id":"claude-haiku-4-5","failed_test_ids":[],
        "sprt_state":{"tc1":{"log_ratio":2.944,"passes":9,"fails":1,"runs":10,
        "decision": "ACCEPT","carried_forward":false,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      Path tempDir = Files.createTempDirectory("test-scope-");
      try (var scope = new TestClaudeTool(tempDir, tempDir))
      {
        SprtRunner runner = new SprtRunner(scope, "2.1.87");
        String result = runner.writeTestResults(
          new String[]{repoDir.toString(), statePath.toString(), testDir.toString()});

        Map<String, String> pairs = new LinkedHashMap<>();
        for (String line : result.strip().split("\n"))
        {
          int eq = line.indexOf('=');
          if (eq > 0)
            pairs.put(line.substring(0, eq), line.substring(eq + 1));
        }
        requireThat(pairs.get("status"), "status").isEqualTo("ok");

        Path testResultsFile = testDir.resolve("test-results.json");
        requireThat(Files.exists(testResultsFile), "testResultsExists").isTrue();
        JsonMapper mapper = scope.getJsonMapper();
        JsonNode testResults = mapper.readTree(testResultsFile.toFile());
        requireThat(testResults.path("sprt").path("overall_decision").asString(),
          "overall_decision").isEqualTo("ACCEPT");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }

  /**
   * Writes a minimal fake Codex grader-agent descriptor for test coverage.
   *
   * @param pluginRoot the fake plugin root
   * @param agentName the fake agent name
   * @param model the configured model
   * @param effort the configured effort
   */
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

  /**
   * Creates a fake runner worktree containing a synthetic Claude launcher script.
   *
   * @param tempDir the temporary root directory
   * @param launcherScript the launcher script contents
   * @return the fake runner worktree path
   */
  private static Path createFakeRunnerWorktree(Path tempDir, String launcherScript) throws IOException
  {
    Path binDir = tempDir.resolve("runner/client/distribution/target/jlink/claude/bin");
    Files.createDirectories(binDir);
    Path launcher = binDir.resolve("claude-runner");
    Path tempLauncher = Files.createTempFile(binDir, "claude-runner-", ".tmp");
    Files.writeString(tempLauncher, launcherScript, StandardCharsets.UTF_8);
    Files.move(tempLauncher, launcher, StandardCopyOption.REPLACE_EXISTING,
      StandardCopyOption.ATOMIC_MOVE);
    requireThat(launcher.toFile().setExecutable(true), "launcherExecutable").isTrue();
    return tempDir.resolve("runner");
  }

  /**
   * Retries the nested engine command once when the test launcher hits {@code ETXTBSY}.
   *
   * @param runner the runner under test
   * @param args the launcher arguments
   * @param runnerWorktree the fake runner worktree
   * @param out the output sink
   * @return the nested process exit code
   * @throws IOException if launcher execution fails
   * @throws InterruptedException if the retry sleep is interrupted
   */
  private static int runSprtEngineCommandWithRetry(SprtRunner runner, String[] args,
    String runnerWorktree, PrintStream out) throws IOException, InterruptedException
  {
    try
    {
      return SharedSecrets.runSprtEngineCommand(runner, args, runnerWorktree, out);
    }
    catch (IOException e)
    {
      if (!e.getMessage().contains("Text file busy"))
        throw e;
      Thread.sleep(50);
      return SharedSecrets.runSprtEngineCommand(runner, args, runnerWorktree, out);
    }
  }

  private static final class BlockingPrintStream extends PrintStream
  {
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch started = new CountDownLatch(1);

    private BlockingPrintStream() throws IOException
    {
      super(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
    }

    @Override
    public void println(String line)
    {
      started.countDown();
      try
      {
        release.await();
      }
      catch (InterruptedException _)
      {
        Thread.currentThread().interrupt();
      }
    }

    /**
     * Unblocks the next blocked {@link #println(String)} call.
     */
    private void release()
    {
      release.countDown();
    }
  }

  private static final class ThrowingPrintStream extends PrintStream
  {
    private ThrowingPrintStream() throws IOException
    {
      super(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
    }

    @Override
    public void println(String line)
    {
      throw new IllegalStateException("output boom");
    }
  }
}
