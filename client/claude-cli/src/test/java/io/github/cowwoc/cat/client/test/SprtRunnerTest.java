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
import io.github.cowwoc.cat.agent.AgentEngine;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
   * Verifies that extract-model defaults to "haiku" when no model field is present in frontmatter.
   */
  @Test
  public void extractModelRejectsSkillWithoutModelField() throws IOException, InterruptedException
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
    expectedExceptionsMessageRegExp = ".*SprtRunner run-sprt: expected 5 arguments.*")
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
    expectedExceptionsMessageRegExp = ".*SprtRunner run-sprt: expected 5 arguments.*")
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
    expectedExceptionsMessageRegExp = ".*SprtRunner run-sprt: expected 5 arguments.*")
  public void runSprtRejectsMissingEffort() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt", tempDir.toString(), "tests", "claude-haiku-4-5",
        "test-session-id"}, System.out);
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
  public void runSprtRejectsRelativeTestDirOutsideWorktree() throws IOException, InterruptedException
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
        "claude-haiku-4-5", "high", "test-session-id"}, System.out);
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
  public void runSprtRejectsSymlinkedTestDirOutsideWorktree() throws IOException, InterruptedException
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
        "claude-haiku-4-5", "high", "test-session-id"}, System.out);
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
  public void runSprtRejectsUnsupportedClaudeModelAtEntrypoint()
    throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt", tempDir.toString(), "tests", "gpt-5.3-codex",
        "high", "test-session-id"}, System.out);
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
  public void runSprtRejectsUnsupportedCodexModelAtEntrypoint()
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
        "high", "test-session-id"}, System.out);
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
  public void runSprtRejectsUnsupportedEffortAtEntrypoint()
    throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"run-sprt", tempDir.toString(), "tests", "claude-haiku-4-5",
        "extreme", "test-session-id"}, System.out);
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
  public void runSprtRejectsUnsupportedCodexEffortAtEntrypoint()
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
      runner.run(new String[]{"run-sprt", tempDir.toString(), "tests", "gpt-5.3-codex",
        "extreme", "test-session-id"}, System.out);
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
      "/tmp/worktree", "client/plugin/tests/skills/learn", "claude-haiku-4-5", "high",
      "test-session-id"});

    requireThat(parsed, "parsed").length().isEqualTo(5);
    requireThat(parsed[0], "worktreePath").isEqualTo("/tmp/worktree");
    requireThat(parsed[1], "testDir").isEqualTo("client/plugin/tests/skills/learn");
    requireThat(parsed[2], "testModel").isEqualTo("claude-haiku-4-5");
    requireThat(parsed[3], "testEffort").isEqualTo("high");
    requireThat(parsed[4], "sessionId").isEqualTo("test-session-id");
  }

  /**
   * Verifies that run-sprt does not accept a missing effort argument.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*SprtRunner run-sprt: expected 5 arguments.*")
  public void parseRunSprtArgsRejectsMissingEffort()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", "claude-haiku-4-5",
      "test-session-id"});
  }

  /**
   * Verifies that run-sprt rejects too many arguments.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*SprtRunner run-sprt: expected 5 arguments.*")
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
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", null,
      "high", "test-session-id"});
  }

  /**
   * Verifies that run-sprt rejects a blank worktree path.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*worktree_path.*")
  public void parseRunSprtArgsRejectsBlankWorktreePath()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{" ", "tests", "claude-haiku-4-5",
      "high", "test-session-id"});
  }

  /**
   * Verifies that run-sprt rejects a null test directory.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*test_dir.*")
  public void parseRunSprtArgsRejectsNullTestDirectory()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", null, "claude-haiku-4-5",
      "high", "test-session-id"});
  }

  /**
   * Verifies that run-sprt rejects a blank test directory.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*test_dir.*")
  public void parseRunSprtArgsRejectsBlankTestDirectory()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", " ", "claude-haiku-4-5",
      "high", "test-session-id"});
  }

  /**
   * Verifies that run-sprt rejects a blank model argument.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*test_model.*")
  public void parseRunSprtArgsRejectsBlankModel()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", " ",
      "high", "test-session-id"});
  }

  /**
   * Verifies that run-sprt rejects a blank effort argument.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*effort.*")
  public void parseRunSprtArgsRejectsBlankEffort()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", "claude-haiku-4-5",
      " ", "test-session-id"});
  }

  /**
   * Verifies that run-sprt rejects a blank session ID.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*session_id.*")
  public void parseRunSprtArgsRejectsBlankSessionId()
  {
    SharedSecrets.parseRunSprtArgs(new String[]{"/tmp/worktree", "tests", "claude-haiku-4-5",
      "high", " "});
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
  public void mergeResultsAnyRejectProducesRejectOverall() throws IOException, InterruptedException
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
   * Verifies that write-test-results computes reject overall_decision.
   */
  @Test
  public void writeTestResultsRejectDecisionWritesRejectOverall() throws IOException, InterruptedException
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
        {"model_id":"claude-haiku-4-5","failed_test_ids":["tc2"],
        "sprt_state":{
          "tc1":{"log_ratio":2.944,"passes":9,"fails":1,"runs":10,
                 "decision": "ACCEPT","carried_forward":false,"smoke_runs_done":3},
          "tc2":{"log_ratio":-2.944,"passes":1,"fails":9,"runs":10,
                 "decision": "REJECT","carried_forward":false,"smoke_runs_done":3}
        }}
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
        JsonMapper mapper = scope.getJsonMapper();
        JsonNode testResults = mapper.readTree(testResultsFile.toFile());
        requireThat(testResults.path("sprt").path("overall_decision").asString(),
          "overall_decision").isEqualTo("REJECT");
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
   * Verifies that create-isolation-branch rejects a dirty worktree.
   */
  @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*uncommitted changes.*")
  public void createIsolationBranchDirtyWorktreeThrows() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("test-branch");
    try
    {
      // Create an untracked file to make the worktree dirty
      Files.writeString(repoDir.resolve("dirty.md"), "dirty content", StandardCharsets.UTF_8);

      Path tempDir = Files.createTempDirectory("test-scope-");
      try (var scope = new TestClaudeTool(tempDir, tempDir))
      {
        SprtRunner runner = new SprtRunner(scope, "2.1.87");
        runner.createIsolationBranch(
          new String[]{repoDir.toString(), repoDir.toString(), "my-issue"});
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
   * Verifies that remove-runner-worktrees returns zero when no runner worktrees exist.
   */
  @Test
  public void removeRunnerWorktreesNoMatchingWorktreesReturnsZero() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("test-branch");
    try
    {
      Path tempDir = Files.createTempDirectory("test-scope-");
      try (var scope = new TestClaudeTool(tempDir, tempDir))
      {
        SprtRunner runner = new SprtRunner(scope, "2.1.87");
        String result = runner.removeRunnerWorktrees(
          new String[]{repoDir.toString(), "nonexistent-issue"});

        requireThat(result.strip(), "result").isEqualTo("removed_count=0");
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
   * Verifies that run() throws when no command is provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*no command specified.*")
  public void runThrowsOnNoCommand() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the static runner entrypoint throws before detecting the Claude Code version when
   * no command is provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*no command specified.*")
  public void staticRunThrowsOnNoCommand() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner.run(scope, new String[]{}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws on an unknown command.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*unknown command.*")
  public void runThrowsOnUnknownCommand() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.run(new String[]{"nonexistent-command"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that detect-changes reports skill_changed=false and all IDs carried forward
   * when the SHA-256 of the current skill file matches the provided hash.
   */
  @Test
  public void detectChangesSha256MatchAllCarriedForward() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create a skill file
      Path skillFile = tempDir.resolve("skill.md");
      String skillContent = """
        ---
        description: Test skill
        model: haiku
        ---
        # Step 1
        Do something.
        """;
      Files.writeString(skillFile, skillContent, StandardCharsets.UTF_8);

      // Compute SHA-256 of the file content using the production helper
      String sha256 = SharedSecrets.sha256Bytes(Files.readAllBytes(skillFile));

      // Create test directory with .md test case files
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

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.detectChanges(new String[]{sha256, skillFile.toString(), testDir.toString()});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("skill_changed").asBoolean(), "skill_changed").isFalse();
      requireThat(root.path("rerun_test_case_ids").size(), "rerunCount").isEqualTo(0);
      requireThat(root.path("carryforward_test_case_ids").size(), "carryforwardCount").isEqualTo(2);
      // semantic_units_path_hint must be present when skill has not changed
      requireThat(root.path("semantic_units_path_hint").isMissingNode(),
        "semanticUnitsPathHintMissing").isFalse();
      requireThat(root.path("semantic_units_path_hint").asString(),
        "semanticUnitsPathHint").contains("skill-test-runner extract-units");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that detect-changes reports skill_changed=true and all IDs in rerun
   * when the SHA-256 of the current skill file does not match the provided hash.
   */
  @Test
  public void detectChangesSha256MismatchAllRerun() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create a skill file
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        model: haiku
        ---
        # Step 1
        Do something.
        """, StandardCharsets.UTF_8);

      // Use the SHA-256 of an empty string — deliberately wrong hash
      String wrongSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

      // Create test directory with .md test case files
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

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.detectChanges(new String[]{wrongSha256, skillFile.toString(),
        testDir.toString()});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("skill_changed").asBoolean(), "skill_changed").isTrue();
      requireThat(root.path("rerun_test_case_ids").size(), "rerunCount").isEqualTo(2);
      requireThat(root.path("carryforward_test_case_ids").size(), "carryforwardCount").isEqualTo(0);
      // semantic_units_path_hint must be absent when skill has changed
      requireThat(root.path("semantic_units_path_hint").isMissingNode(),
        "semanticUnitsPathHintMissing").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that detect-changes throws IllegalArgumentException when given a short (non-64-char)
   * hex string — the old git SHA format is rejected.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*64.*")
  public void detectChangesInvalidShaShortStringThrowsIllegalArgument() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, "# Skill\n", StandardCharsets.UTF_8);
      Path testDir = tempDir.resolve("tests");
      Files.createDirectories(testDir);

      // A 9-character git commit SHA abbreviation — invalid under the new contract
      String shortGitSha = "b40012f59";
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.detectChanges(new String[]{shortGitSha, skillFile.toString(), testDir.toString()});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that detect-changes throws IllegalArgumentException when given a 64-character string
   * that contains non-hex characters.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*64.*")
  public void detectChangesInvalidShaNotHexThrowsIllegalArgument() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, "# Skill\n", StandardCharsets.UTF_8);
      Path testDir = tempDir.resolve("tests");
      Files.createDirectories(testDir);

      // 64 chars but contains uppercase G — not valid lowercase hex
      String notHex = "GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG";
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.detectChanges(new String[]{notHex, skillFile.toString(), testDir.toString()});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that detect-changes with an empty test directory returns empty arrays for all ID fields.
   */
  @Test
  public void detectChangesEmptyTestDirectoryReturnsEmptyArrays() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create a skill file
      Path skillFile = tempDir.resolve("skill.md");
      String skillContent = """
        ---
        description: Test skill
        model: haiku
        ---
        # Step 1
        Do something.
        """;
      Files.writeString(skillFile, skillContent, StandardCharsets.UTF_8);

      // Compute SHA using the production helper
      String sha256 = SharedSecrets.sha256Bytes(Files.readAllBytes(skillFile));

      // Create an empty test directory (no .md files)
      Path testDir = tempDir.resolve("test-cases");
      Files.createDirectories(testDir);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.detectChanges(new String[]{sha256, skillFile.toString(), testDir.toString()});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("skill_changed").asBoolean(), "skill_changed").isFalse();
      requireThat(root.path("all_test_case_ids").size(), "allCount").isEqualTo(0);
      requireThat(root.path("rerun_test_case_ids").size(), "rerunCount").isEqualTo(0);
      requireThat(root.path("carryforward_test_case_ids").size(), "carryforwardCount").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extract-model defaults to "haiku" when SKILL.md is missing the model frontmatter field.
   */
  @Test
  public void extractModelRejectsMissingModelFrontmatter() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-extract-model-");
    Path pluginRoot = tempDir.resolve("plugin");
    try
    {
      // Create skill dir with SKILL.md that has no model: field
      Path skillDir = pluginRoot.resolve("skills").resolve("my-skill");
      Files.createDirectories(skillDir);
      Path skillFile = skillDir.resolve("SKILL.md");
      Files.writeString(skillFile, """
        ---
        description: My skill
        ---
        """, StandardCharsets.UTF_8);

      Path projectDir = tempDir.resolve("project");
      Files.createDirectories(projectDir);
      try (var scope = new TestClaudeTool(projectDir, pluginRoot))
      {
        SprtRunner runner = new SprtRunner(scope, "2.1.87");
        String model = runner.extractModel(new String[]{skillFile.toString()});
        requireThat(model, "model").isEqualTo("claude-haiku-4-5");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extract-test-dir correctly maps a plugin skill path to its test directory.
   */
  @Test
  public void extractTestDirMapsPluginSkillPath()
  {
    Path tempDir = Path.of("/tmp/fake-project");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.extractTestDir(
        new String[]{"client/plugin/skills/common/foo/first-use.md", "/workspace"});
      requireThat(result, "result").
        isEqualTo("/workspace/client/plugin/tests/skills/common/foo/first-use");
    }
  }

  /**
   * Verifies that extract-test-dir correctly maps a non-plugin path (no "plugin/" prefix stripping).
   */
  @Test
  public void extractTestDirMapsNonPluginPath()
  {
    Path tempDir = Path.of("/tmp/fake-project");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.extractTestDir(new String[]{"CLAUDE.md", "/workspace"});
      requireThat(result, "result").isEqualTo("/workspace/client/plugin/tests/CLAUDE");
    }
  }

  /**
   * Verifies that extract-test-dir handles nested non-plugin paths.
   */
  @Test
  public void extractTestDirMapsNestedNonPluginPath()
  {
    Path tempDir = Path.of("/tmp/fake-project");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.extractTestDir(
        new String[]{".claude/rules/common.md", "/workspace"});
      requireThat(result, "result").isEqualTo("/workspace/client/plugin/tests/.claude/rules/common");
    }
  }

  /**
   * Verifies that stakeholder-review prompts include explicit working-directory context.
   */
  @Test
  public void stakeholderReviewPromptIncludesWorkingDirectoryContext() throws IOException
  {
    Path repoRoot = Path.of("").toAbsolutePath().normalize().getParent();
    requireThat(repoRoot, "repoRoot").isNotNull();
    Path promptFile = repoRoot.resolve("plugin/skills/include/stakeholder-review.md");
    String prompt = Files.readString(promptFile, StandardCharsets.UTF_8);

    requireThat(prompt, "prompt").contains("## Review Context");
    requireThat(prompt, "prompt").contains(
      "Changed files (read from review_context.worktree_path): {CHANGED_FILES_BULLETS}");
    requireThat(prompt, "prompt").contains(
      "review_context.worktree_path is the authoritative working directory for this review.");
    requireThat(prompt, "prompt").doesNotContain("\nWORKTREE_PATH: {WORKTREE_PATH}");
    requireThat(prompt, "prompt").contains(
      "Read every changed file using absolute paths rooted at {review_context.worktree_path}/.");
    requireThat(prompt, "prompt").contains("Reading outside these paths invalidates the review.");
  }

  /**
   * Verifies that persist-artifacts writes instruction-test.json with expected JSON fields.
   */
  @Test
  public void persistArtifactsWritesInstructionTestJson() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create skill file inside proper directory structure
      Path skillDir = repoDir.resolve("client/plugin/skills/common/test-skill");
      Files.createDirectories(skillDir);
      Path skillFile = skillDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        model: haiku
        ---
        # Body
        """, StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "client/plugin/skills/common/test-skill/skill.md");
      TestUtils.runGit(repoDir, "commit", "-m", "add skill");

      // Create an artifacts dir with .md test case files
      Path artifactsDir = tempDir.resolve("artifacts");
      Files.createDirectories(artifactsDir);
      Files.writeString(artifactsDir.resolve("tc1.md"), """
        ---
        category: REQUIREMENT
        ---

        ## Turn 1

        Test prompt for tc1.

        ## Assertions

        1. The Skill tool was invoked
        """, StandardCharsets.UTF_8);

      String sessionId = "test-session-001";
      String phase = "initial";

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(baos, false, StandardCharsets.UTF_8);
      String[] args = {
        "client/plugin/skills/common/test-skill/skill.md",
        artifactsDir.toString(),
        sessionId,
        repoDir.toString(),
        phase
      };
      runner.persistArtifacts(args, out);

      // Verify instruction-test.json was created in .cat/work/instruction-test/{skillName}/
      // skillName is extracted from the skill directory name
      Path instructionTestJson = repoDir.resolve(".cat/work/instruction-test/test-skill/instruction-test.json");
      requireThat(Files.exists(instructionTestJson), "instructionTestJsonExists").isTrue();

      String content = Files.readString(instructionTestJson, StandardCharsets.UTF_8);
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(content);

      requireThat(root.path("session_id").asString(), "session_id").isEqualTo(sessionId);
      requireThat(root.path("model_id").asString(), "model_id").
        isEqualTo("claude-haiku-4-5");
      requireThat(root.path("phase").asString(), "phase").isEqualTo(phase);
      requireThat(root.path("skill").path("path").asString(), "skill.path").
        isEqualTo("client/plugin/skills/common/test-skill/skill.md");
      requireThat(root.path("skill").path("sha256").asString(""), "skill.sha256").isNotBlank();
      requireThat(root.path("test_cases").path("path").asString(), "test_cases.path").
        isEqualTo("client/plugin/skills/common/test-skill/first-use");
      requireThat(root.path("test_cases").path("sha256").asString(""), "test_cases.sha256").
        isNotBlank();

      // Assert exclusivity: no undocumented fields
      List<String> fieldNames = new ArrayList<>(root.propertyNames());
      Collections.sort(fieldNames);
      List<String> expectedFieldNames = List.of("model_id", "phase", "session_id", "skill", "test_cases", "timestamp");
      requireThat(fieldNames, "fieldNames").isEqualTo(expectedFieldNames);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that persist-artifacts copies .md test case files into the instruction-test directory.
   */
  @Test
  public void persistArtifactsCopiesMdFiles() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillDir = repoDir.resolve("client/plugin/skills/common/test-skill");
      Files.createDirectories(skillDir);
      Path skillFile = skillDir.resolve("skill.md");
      Files.writeString(skillFile, "---\ndescription: Test\nmodel: haiku\n---\n# Body\n",
        StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "client/plugin/skills/common/test-skill/skill.md");
      TestUtils.runGit(repoDir, "commit", "-m", "add skill");

      Path artifactsDir = tempDir.resolve("artifacts");
      Files.createDirectories(artifactsDir);
      String tc1Content = """
        ---
        category: REQUIREMENT
        ---

        ## Turn 1

        Test prompt for tc1.

        ## Assertions

        1. The Skill tool was invoked
        """;
      Files.writeString(artifactsDir.resolve("tc1.md"), tc1Content, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(baos, false, StandardCharsets.UTF_8);
      String[] args2 = {
        "client/plugin/skills/common/test-skill/skill.md",
        artifactsDir.toString(),
        "sess1",
        repoDir.toString(),
        "final"
      };
      runner.persistArtifacts(args2, out);

      // Verify tc1.md was copied into first-use/
      Path copiedTestCase = repoDir.resolve("client/plugin/skills/common/test-skill/first-use/tc1.md");
      requireThat(Files.exists(copiedTestCase), "tc1MdCopied").isTrue();

      // Verify the content matches
      String copiedContent = Files.readString(copiedTestCase, StandardCharsets.UTF_8);
      requireThat(copiedContent, "copiedContent").contains("category: REQUIREMENT");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that persist-artifacts throws IllegalArgumentException when the worktree root does not exist.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*worktree root not found.*")
  public void persistArtifactsThrowsWhenWorktreeRootMissing() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.persistArtifacts(
        new String[]{"skill.md", tempDir.toString(), "sess1", "/nonexistent/worktree/root", "initial"},
        System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that merge-results uses prior instruction-test stats for carryforward IDs instead of current SPRT state.
   */
  @Test
  public void mergeResultsCarryforwardUsePriorStats() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // TC1 is ACCEPT in current SPRT state; TC2 is INCONCLUSIVE with low log_ratio
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{
          "TC1":{"log_ratio":3.0,"passes":10,"fails":0,"runs":10,
                 "decision": "ACCEPT","carried_forward":false,"smoke_runs_done":3},
          "TC2":{"log_ratio":0.5,"passes":3,"fails":2,"runs":5,
                 "decision": "INCONCLUSIVE","carried_forward":false,"smoke_runs_done":3}
        }}
        """, StandardCharsets.UTF_8);

      // Prior instruction-test has TC2 as ACCEPT with high log_ratio — carryforward should use these values
      Path priorInstructionTestPath = tempDir.resolve("prior_instruction_test.json");
      Files.writeString(priorInstructionTestPath, """
        {"test_cases":[
          {"test_case_id":"TC1","log_ratio":2.8,"passes":9,"fails":0,"runs":9,"decision": "ACCEPT"},
          {"test_case_id":"TC2","log_ratio":3.5,"passes":12,"fails":1,"runs":13,"decision": "ACCEPT"}
        ]}
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      // TC2 is in the carryforward set: its stats should come from prior instruction-test, not SPRT state
      String result = runner.mergeResults(
        new String[]{statePath.toString(), priorInstructionTestPath.toString(), "[\"TC2\"]",
          "claude-haiku-4-5"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      // Overall decision must be accept (both TC1 and TC2 are accept in the output)
      requireThat(root.path("overall_decision").asString(), "overall_decision").isEqualTo("ACCEPT");

      // Find TC2 in the output test_cases array and verify it uses prior stats
      JsonNode testCases = root.path("test_cases");
      JsonNode tc2 = null;
      for (JsonNode tc : testCases)
      {
        if ("TC2".equals(tc.path("test_case_id").asString()))
        {
          tc2 = tc;
          break;
        }
    }
      requireThat(tc2, "tc2").isNotNull();
      // Prior instruction-test has TC2 log_ratio=3.5; SPRT state has 0.5 — must use prior value
      requireThat(tc2.path("log_ratio").asDouble(), "tc2.log_ratio").isEqualTo(3.5);
      requireThat(tc2.path("decision").asString(), "tc2.decision").isEqualTo("ACCEPT");
      requireThat(tc2.path("passes").asInt(), "tc2.passes").isEqualTo(12);
      requireThat(tc2.path("carried_forward").asBoolean(), "tc2.carried_forward").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that create-isolation-branch includes {@code tc_ids_json} in its return JSON,
   * containing an ordered array of testcase IDs derived from sorted test case filenames.
   */
  @Test
  public void createIsolationBranchIncludesTcIdsJson() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("my-issue");
    Path pluginRoot = Files.createTempDirectory("test-plugin-root-");
    try (var scope = new TestClaudeTool(repoDir, pluginRoot))
    {
      // Create stub extract-turns binary that copies input to turn1.md in dest dir.
      // The test relies on SprtIsolationManager's plugin-root fallback path.
      Path binDir = pluginRoot.resolve("client/bin");
      Files.createDirectories(binDir);
      Path extractTurnsBin = binDir.resolve("extract-turns");
      Files.writeString(extractTurnsBin, """
        #!/bin/bash
        base="${2%.md}"
        cp "$1" "${base}_turn1.md"
        """, StandardCharsets.UTF_8);
      extractTurnsBin.toFile().setExecutable(true);

      // Create two test case files in a test dir inside the repo
      Path testDir = repoDir.resolve("tests");
      Files.createDirectories(testDir);
      Files.writeString(testDir.resolve("alpha-test.md"), """
        ## Turn 1
        Do something.
        ## Assertions
        1. Check output.
        """, StandardCharsets.UTF_8);
      Files.writeString(testDir.resolve("beta-test.md"), """
        ## Turn 1
        Do something else.
        ## Assertions
        1. Check something.
        """, StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "-A");
      TestUtils.runGit(repoDir, "commit", "-m", "add test cases");

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.createIsolationBranch(
        new String[]{repoDir.toString(), testDir.toString(), "my-issue"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      // tc_ids_json must be a JSON array of testcase IDs in sorted filename order.
      requireThat(root.has("tc_ids_json"), "hasTcIdsJson").isTrue();
      JsonNode tcIdsJson = root.path("tc_ids_json");
      requireThat(tcIdsJson.isArray(), "isArray").isTrue();
      requireThat(tcIdsJson.size(), "size").isEqualTo(2);
      requireThat(tcIdsJson.get(0).asString(), "tcId0").isEqualTo("alpha-test");
      requireThat(tcIdsJson.get(1).asString(), "tcId1").isEqualTo("beta-test");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that save-failed-run copies the source file to the failed-runs directory
   * and returns the destination path in JSON.
   */
  @Test
  public void saveFailedRunCopiesFileToFailedRunsDir() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create source file
      Path sourceDir = tempDir.resolve(".cat/work/test-runs/session-id");
      Files.createDirectories(sourceDir);
      Path sourceFile = sourceDir.resolve("tc1_run2.json");
      Files.writeString(sourceFile, "{\"result\":\"failed\"}", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.saveFailedRun(new String[]{tempDir.toString(), sourceFile.toString()});

      // Verify the file was copied to the failed-runs directory
      Path expectedDest = tempDir.resolve(".cat/work/failed-runs/tc1_run2.json");
      requireThat(Files.exists(expectedDest), "destExists").isTrue();
      String content = Files.readString(expectedDest, StandardCharsets.UTF_8);
      requireThat(content.trim(), "content").isEqualTo("{\"result\":\"failed\"}");

      // Verify return includes dest_path key=value
      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      requireThat(pairs.get("dest_path"), "destPath").isEqualTo(expectedDest.toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that save-failed-run throws when the source file does not exist.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*file not found.*")
  public void saveFailedRunThrowsWhenSourceMissing() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.saveFailedRun(new String[]{tempDir.toString(), "/nonexistent/tc1_run1.json"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that remove-runner-worktree removes the worktree directory and deletes the branch.
   */
  @Test
  public void removeRunnerWorktreeRemovesWorktreeAndBranch() throws IOException, InterruptedException
  {
    Path mainRepo = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create a runner worktree inside the main repo directory
      String runnerBranch = "my-issue-tc1-r1";
      Path runnerWorktree = mainRepo.resolve("worktrees").resolve(runnerBranch);
      Files.createDirectories(runnerWorktree.getParent());
      TestUtils.runGit(mainRepo, "worktree", "add", "-b", runnerBranch, runnerWorktree.toString());

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.removeRunnerWorktree(new String[]{
        mainRepo.toString(), runnerWorktree.toString(), runnerBranch});

      // Worktree directory must be gone
      requireThat(Files.exists(runnerWorktree), "worktreeStillExists").isFalse();

      // Return must indicate success
      requireThat(result.strip(), "result").isEqualTo("removed=true");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that prepare-run resolves a relative test_dir to an absolute path and derives
   * issue_name, test_dir_rel, and sprt_state_path from worktree_path.
   */
  @Test
  public void prepareRunResolvesAbsoluteTestDir() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create a subdirectory to use as test_dir (with a .md file to pass validation)
      Path testDirAbs = tempDir.resolve("plugin/tests/myskill");
      Files.createDirectories(testDirAbs);
      Files.writeString(testDirAbs.resolve("test-case.md"), "# Test", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      // Pass relative test_dir
      String result = runner.prepareRun(new String[]{tempDir.toString(), "plugin/tests/myskill"});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      requireThat(pairs.get("test_dir_abs"), "test_dir_abs").isEqualTo(testDirAbs.toString());
      requireThat(pairs.get("test_dir_rel"), "test_dir_rel").isEqualTo("plugin/tests/myskill");
      requireThat(pairs.get("issue_name"), "issue_name").isEqualTo(tempDir.getFileName().toString());
      requireThat(pairs.get("sprt_state_path"), "sprt_state_path").
        isEqualTo(tempDir.resolve(".cat/work/sprt-state.json").toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that prepare-run rejects test directories outside the worktree.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*outside.*")
  public void prepareRunRejectsTestDirOutsideWorktree() throws IOException, InterruptedException
  {
    Path worktree = Files.createTempDirectory("test-worktree-");
    Path outside = Files.createTempDirectory("test-outside-");
    try (var scope = new TestClaudeTool(worktree, worktree))
    {
      Files.writeString(outside.resolve("test-case.md"), "# Test", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.prepareRun(new String[]{worktree.toString(), outside.toString()});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(worktree);
      TestUtils.deleteDirectoryRecursively(outside);
    }
  }

  /**
   * Verifies that get-json-field extracts a top-level string field from a JSON object.
   */
  @Test
  public void getJsonFieldExtractsStringValue() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.getJsonField(
        new String[]{"{\"decision\":\"ACCEPT\",\"runs\":5}", "decision"});
      requireThat(result, "decision").isEqualTo("ACCEPT");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that create-runner-worktrees creates the output directory and returns output_dir
   * when there are no INCONCLUSIVE test cases.
   */
  @Test
  public void createRunnerWorktreesCreatesOutputDir() throws IOException, InterruptedException
  {
    Path mainRepo = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Write a sprt_state.json with one ACCEPT TC — no INCONCLUSIVE → no worktrees created
      Path sprtStatePath = mainRepo.resolve(".cat/work/sprt-state.json");
      Files.createDirectories(sprtStatePath.getParent());
      Files.writeString(sprtStatePath,
        "{\"sprt_state\":{\"tc1\":{\"decision\":\"ACCEPT\",\"runs\":3}}}",
        StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.createRunnerWorktrees(new String[]{
        mainRepo.toString(), sprtStatePath.toString(),
        "my-issue", "test-session-id"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      // output_dir must be present and the directory must have been created
      String outputDir = root.path("output_dir").asString();
      requireThat(outputDir, "output_dir").isNotBlank();
      requireThat(Files.isDirectory(Path.of(outputDir)), "outputDirExists").isTrue();
      requireThat(outputDir, "output_dir").contains("test-session-id");

      // No INCONCLUSIVE TCs → empty worktrees array
      requireThat(root.path("worktrees").isArray(), "isArray").isTrue();
      requireThat(root.path("worktrees").size(), "worktreesSize").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that prepare-run rejects a test_dir containing no .md files.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*no .md.*")
  public void prepareRunFailsOnEmptyTestDir() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path testDirAbs = tempDir.resolve("plugin/tests/myskill");
      Files.createDirectories(testDirAbs);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.prepareRun(new String[]{tempDir.toString(), "plugin/tests/myskill"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

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
   * Verifies that write-test-results returns overall_decision and test_sha after a successful commit.
   */
  @Test
  public void writeTestResultsReturnsOverallDecisionAndSha() throws IOException, InterruptedException
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
  public void buildClaudeTrialArgsIncludesEngineSpecificArguments() throws IOException
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
  public void buildCodexTrialArgsIncludesEngineSpecificArguments() throws IOException
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
  public void buildCodexGraderArgsIncludesEngineSpecificArguments() throws IOException
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
  public void buildClaudeTrialArgsRejectsNullPromptFile()
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
  public void buildCodexGraderArgsRejectsBlankRunnerWorktree()
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
  public void engineTrialArgsRejectUnsupportedDescriptor()
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
  public void engineClaudeTrialArgsRejectUnsupportedModel()
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
  public void engineCodexTrialArgsRejectUnsupportedEffort()
  {
    SharedSecrets.buildTrialArgsForDescriptor(AgentEngine.CODEX.pluginDescriptor(),
      Path.of("/tmp/prompt.txt"), "gpt-5.3-codex", "extreme", "/tmp/worktree",
      "/tmp/output.json");
  }

  /**
   * Verifies that engine-dispatched Claude trial arguments reject Codex-only efforts.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid effort.*")
  public void engineClaudeTrialArgsRejectCodexOnlyEffort()
  {
    SharedSecrets.buildClaudeTrialArgs(Path.of("/tmp/prompt.txt"), "claude-sonnet-4-5",
      "minimal", "/tmp/worktree", "/tmp/output.json", Path.of("/tmp/jlink/bin"));
  }

  /**
   * Verifies that engine-dispatched Codex trial arguments reject Claude-only efforts.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid effort.*")
  public void engineCodexTrialArgsRejectClaudeOnlyEffort()
  {
    SharedSecrets.buildTrialArgsForDescriptor(AgentEngine.CODEX.pluginDescriptor(),
      Path.of("/tmp/prompt.txt"), "gpt-5.3-codex", "max", "/tmp/worktree",
      "/tmp/output.json");
  }

  /**
   * Verifies that engine-dispatched Claude trial arguments accept Claude-only efforts.
   */
  @Test
  public void engineClaudeTrialArgsAcceptClaudeOnlyEffort()
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
  public void engineGraderArgsRejectBlankRunnerWorktree()
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
  public void engineClaudeGraderArgsRejectUnsupportedEffort()
  {
    SharedSecrets.buildGraderArgsForDescriptor(AgentEngine.CLAUDE.pluginDescriptor(),
      Path.of("/tmp/grader-prompt.txt"), "claude-sonnet-4-5", "extreme", "/tmp/worktree");
  }

  /**
   * Verifies that engine-dispatched Codex grader arguments reject unsupported model IDs.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Invalid Codex model ID.*")
  public void engineCodexGraderArgsRejectUnsupportedModel()
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
  public void buildClaudeGraderArgsRejectsNullPromptFile()
  {
    SharedSecrets.buildClaudeGraderArgs(null, "model", "medium", "/tmp/worktree",
      Path.of("/tmp/jlink/bin"));
  }
}
