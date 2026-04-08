/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.SharedSecrets;
import io.github.cowwoc.cat.hooks.skills.InstructionTestRunner;
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
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link InstructionTestRunner}.
 * <p>
 * Each test is self-contained with no shared state.
 */
public final class InstructionTestRunnerTest
{
  /**
   * Verifies that extract-units returns line-numbered body when file has frontmatter.
   */
  @Test
  public void extractUnitsWithFrontmatter() throws IOException
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void extractUnitsWithoutFrontmatter() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("skill.md");
      Files.writeString(skillFile, """
        # Step 1
        Do something.
        """, StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void extractUnitsFileNotFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void extractModelFromFrontmatter() throws IOException
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String model = runner.extractModel(new String[]{skillFile.toString()});
      requireThat(model, "model").isEqualTo("claude-sonnet-4-6");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extract-model defaults to haiku when no model field is present in frontmatter
   * and the skill is not listed in model-selection.md.
   */
  @Test
  public void extractModelDefaultsToHaikuWhenFieldMissing() throws IOException
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.extractModel(new String[]{skillFile.toString()});
      requireThat(result, "result").contains("haiku");
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
  public void mapUnitsPartitionsCorrectly() throws IOException
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
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void mapUnitsNoChangedUnitsCarriesForwardAll() throws IOException
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
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void initSprtFreshStateNoPrior() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      String rerunJson = "[\"TC1\",\"TC2\"]";
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.initSprt(new String[]{rerunJson, "none", "claude-haiku-4-5-20251001"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);
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
   * Verifies that init-sprt sets log_ratio to PRIOR_BOOST when --prior-boost is enabled and the
   * prior test case has ACCEPT decision.
   * <p>
   * PRIOR_BOOST = 1.112, equivalent to 10 prior PASS observations (10 × SPRT_LOG_PASS = 10 × 0.1112).
   */
  @Test
  public void initSprtUsePriorBoostWithAcceptPrior() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Prior instruction-test has TC1 as ACCEPT
      Path priorPath = tempDir.resolve("prior.json");
      Files.writeString(priorPath, """
        {"model_id":"claude-haiku-4-5-20251001","test_cases":[
          {"test_case_id":"TC1","log_ratio":3.0,"passes":10,"fails":0,"runs":10,"decision":"ACCEPT"}
        ]}
        """, StandardCharsets.UTF_8);

      String rerunJson = "[\"TC1\"]";
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.initSprt(new String[]{rerunJson, priorPath.toString(),
        "claude-haiku-4-5-20251001", "--prior-boost"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);
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
  public void initSprtWithEmptyPrior() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Pass "none" as prior path to indicate no prior instruction-test
      String rerunJson = "[\"TC1\"]";
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.initSprt(new String[]{rerunJson, "none", "claude-haiku-4-5-20251001"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);
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
  public void checkBoundaryWithZeroTestCases() throws IOException
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
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void persistArtifactsRejectsEmptyArtifactsDir() throws IOException
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void updateSprtPassUpdatesLogRatioAndDecision() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create initial state file with TC1 in INCONCLUSIVE state near ACCEPT boundary
      Path statePath = tempDir.resolve("sprt_state.json");
      // log_ratio 2.9 is just below ACCEPT (2.944); one PASS (0.1112) should push it over
      Files.writeString(statePath, """
        {"sprt_state":{"TC1":{"log_ratio":2.9,"passes":10,"fails":0,"runs":10,
        "decision":"INCONCLUSIVE","carried_forward":false,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.updateSprt(new String[]{statePath.toString(), "TC1", "true"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);
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
  public void updateSprtFailUpdatesLogRatioAndDecision() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      // log_ratio -2.9 is just above REJECT (-2.944); one FAIL (-1.0986) should push it below
      Files.writeString(statePath, """
        {"sprt_state":{"TC1":{"log_ratio":-2.9,"passes":0,"fails":10,"runs":10,
        "decision":"INCONCLUSIVE","carried_forward":false,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.updateSprt(new String[]{statePath.toString(), "TC1", "false"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);
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
   * Verifies that check-boundary returns correct values for a known state.
   */
  @Test
  public void checkBoundaryReturnsCorrectFields() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{"TC1":{"log_ratio":1.5,"passes":5,"fails":2,"runs":7,
        "decision":"INCONCLUSIVE","carried_forward":true,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void smokeStatusInSmokePhase() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{"TC1":{"log_ratio":0.0,"passes":1,"fails":0,"runs":1,
        "decision":"INCONCLUSIVE","carried_forward":false,"smoke_runs_done":1}}}
        """, StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void smokeStatusEscalates() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{"TC1":{"log_ratio":0.0,"passes":1,"fails":2,"runs":3,
        "decision":"INCONCLUSIVE","carried_forward":false,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
   * Verifies that merge-results produces ACCEPT overall_decision when all test cases ACCEPT.
   */
  @Test
  public void mergeResultsAllAccept() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{
          "TC1":{"log_ratio":3.0,"passes":10,"fails":0,"runs":10,
                 "decision":"ACCEPT","carried_forward":false,"smoke_runs_done":3},
          "TC2":{"log_ratio":3.0,"passes":10,"fails":0,"runs":10,
                 "decision":"ACCEPT","carried_forward":true,"smoke_runs_done":3}
        }}
        """, StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.mergeResults(
        new String[]{statePath.toString(), "none", "[]", "claude-haiku-4-5-20251001"});

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
   * Verifies that merge-results produces REJECT overall_decision when any test case REJECTs.
   */
  @Test
  public void mergeResultsAnyRejectProducesRejectOverall() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{
          "TC1":{"log_ratio":3.0,"passes":10,"fails":0,"runs":10,
                 "decision":"ACCEPT","carried_forward":false,"smoke_runs_done":3},
          "TC2":{"log_ratio":-3.0,"passes":0,"fails":10,"runs":10,
                 "decision":"REJECT","carried_forward":false,"smoke_runs_done":3}
        }}
        """, StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.mergeResults(
        new String[]{statePath.toString(), "none", "[]", "claude-haiku-4-5-20251001"});

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
  public void runDispatchesToSubcommand() throws IOException
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
   * Verifies that run() throws when no command is provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*no command specified.*")
  public void runThrowsOnNoCommand() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      runner.run(new String[]{}, System.out);
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
  public void runThrowsOnUnknownCommand() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void detectChangesSha256MatchAllCarriedForward() throws IOException
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void detectChangesSha256MismatchAllRerun() throws IOException
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void detectChangesInvalidShaShortStringThrowsIllegalArgument() throws IOException
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
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void detectChangesInvalidShaNotHexThrowsIllegalArgument() throws IOException
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
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void detectChangesEmptyTestDirectoryReturnsEmptyArrays() throws IOException
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
   * Verifies that extract-model returns the sonnet model ID when the skill is listed in model-selection.md
   * and SKILL.md has no model: frontmatter field.
   */
  @Test
  public void extractModelUsesModelSelectionMappingForSonnetSkill() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-extract-model-");
    Path pluginRoot = tempDir.resolve("plugin");
    try
    {
      // Create plugin root with model-selection.md listing the test skill
      Path rulesDir = pluginRoot.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("model-selection.md"), """
        ## Model Selection for Skills

        **Sonnet-preferred skills**:

        - `cat:my-sonnet-skill`
        """, StandardCharsets.UTF_8);

      // Create skill dir with SKILL.md that has no model: field
      Path skillDir = pluginRoot.resolve("skills").resolve("my-sonnet-skill");
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
        InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
        String result = runner.extractModel(new String[]{skillFile.toString()});

        // Result is a fully-qualified model ID; verify it contains "sonnet"
        requireThat(result, "result").contains("sonnet");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extract-model falls back to haiku when the skill is not listed in model-selection.md
   * and SKILL.md has no model: frontmatter field.
   */
  @Test
  public void extractModelFallsBackToHaikuWhenNotInModelSelection() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-extract-model-");
    Path pluginRoot = tempDir.resolve("plugin");
    try
    {
      // Create plugin root with model-selection.md that does NOT list the test skill
      Path rulesDir = pluginRoot.resolve("rules");
      Files.createDirectories(rulesDir);
      Files.writeString(rulesDir.resolve("model-selection.md"), """
        ## Model Selection for Skills

        **Sonnet-preferred skills**:

        - `cat:some-other-skill`
        """, StandardCharsets.UTF_8);

      // Create skill dir with SKILL.md that has no model: field
      Path skillDir = pluginRoot.resolve("skills").resolve("my-haiku-skill");
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
        InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
        String result = runner.extractModel(new String[]{skillFile.toString()});

        // Result is a fully-qualified model ID; verify it contains "haiku"
        requireThat(result, "result").contains("haiku");
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
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.extractTestDir(
        new String[]{"plugin/skills/foo/first-use.md", "/workspace"});
      requireThat(result, "result").isEqualTo("/workspace/plugin/tests/skills/foo/first-use");
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
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.extractTestDir(new String[]{"CLAUDE.md", "/workspace"});
      requireThat(result, "result").isEqualTo("/workspace/plugin/tests/CLAUDE");
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
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.extractTestDir(
        new String[]{".claude/rules/common.md", "/workspace"});
      requireThat(result, "result").isEqualTo("/workspace/plugin/tests/.claude/rules/common");
    }
  }

  /**
   * Verifies that persist-artifacts writes instruction-test.json with expected JSON fields.
   */
  @Test
  public void persistArtifactsWritesInstructionTestJson() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create skill file inside proper directory structure
      Path skillDir = repoDir.resolve("plugin/skills/test-skill");
      Files.createDirectories(skillDir);
      Path skillFile = skillDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        model: haiku
        ---
        # Body
        """, StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "plugin/skills/test-skill/skill.md");
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(baos, false, StandardCharsets.UTF_8);
      String[] args = {
        "plugin/skills/test-skill/skill.md",
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
        isEqualTo("claude-haiku-4-5-20251001");
      requireThat(root.path("phase").asString(), "phase").isEqualTo(phase);
      requireThat(root.path("skill").path("path").asString(), "skill.path").
        isEqualTo("plugin/skills/test-skill/skill.md");
      requireThat(root.path("skill").path("sha256").asString(""), "skill.sha256").isNotBlank();
      requireThat(root.path("test_cases").path("path").asString(), "test_cases.path").
        isEqualTo("plugin/skills/test-skill/first-use");
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
  public void persistArtifactsCopiesMdFiles() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillDir = repoDir.resolve("plugin/skills/test-skill");
      Files.createDirectories(skillDir);
      Path skillFile = skillDir.resolve("skill.md");
      Files.writeString(skillFile, "---\ndescription: Test\nmodel: haiku\n---\n# Body\n",
        StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "plugin/skills/test-skill/skill.md");
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(baos, false, StandardCharsets.UTF_8);
      String[] args2 = {
        "plugin/skills/test-skill/skill.md",
        artifactsDir.toString(),
        "sess1",
        repoDir.toString(),
        "final"
      };
      runner.persistArtifacts(args2, out);

      // Verify tc1.md was copied into first-use/
      Path copiedTestCase = repoDir.resolve("plugin/skills/test-skill/first-use/tc1.md");
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
  public void persistArtifactsThrowsWhenWorktreeRootMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void mergeResultsCarryforwardUsePriorStats() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // TC1 is ACCEPT in current SPRT state; TC2 is INCONCLUSIVE with low log_ratio
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"sprt_state":{
          "TC1":{"log_ratio":3.0,"passes":10,"fails":0,"runs":10,
                 "decision":"ACCEPT","carried_forward":false,"smoke_runs_done":3},
          "TC2":{"log_ratio":0.5,"passes":3,"fails":2,"runs":5,
                 "decision":"INCONCLUSIVE","carried_forward":false,"smoke_runs_done":3}
        }}
        """, StandardCharsets.UTF_8);

      // Prior instruction-test has TC2 as ACCEPT with high log_ratio — carryforward should use these values
      Path priorInstructionTestPath = tempDir.resolve("prior_instruction_test.json");
      Files.writeString(priorInstructionTestPath, """
        {"test_cases":[
          {"test_case_id":"TC1","log_ratio":2.8,"passes":9,"fails":0,"runs":9,"decision":"ACCEPT"},
          {"test_case_id":"TC2","log_ratio":3.5,"passes":12,"fails":1,"runs":13,"decision":"ACCEPT"}
        ]}
        """, StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      // TC2 is in the carryforward set: its stats should come from prior instruction-test, not SPRT state
      String result = runner.mergeResults(
        new String[]{statePath.toString(), priorInstructionTestPath.toString(), "[\"TC2\"]",
          "claude-haiku-4-5-20251001"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      // Overall decision must be ACCEPT (both TC1 and TC2 are ACCEPT in the output)
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
}
