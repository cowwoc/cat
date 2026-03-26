/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.skills.SkillTestRunner;
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
 * Tests for {@link SkillTestRunner}.
 * <p>
 * Each test is self-contained with no shared state.
 */
public final class SkillTestRunnerTest
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

      SkillTestRunner runner = new SkillTestRunner(scope);
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

      SkillTestRunner runner = new SkillTestRunner(scope);
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
      SkillTestRunner runner = new SkillTestRunner(scope);
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

      SkillTestRunner runner = new SkillTestRunner(scope);
      String model = runner.extractModel(new String[]{skillFile.toString()});
      requireThat(model, "model").isEqualTo("sonnet");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extract-model falls back to "haiku" when no model field is present.
   */
  @Test
  public void extractModelFallsBackToHaiku() throws IOException
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

      SkillTestRunner runner = new SkillTestRunner(scope);
      String model = runner.extractModel(new String[]{skillFile.toString()});
      requireThat(model, "model").isEqualTo("haiku");
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
      // Create test-cases.json
      Path testCasesPath = tempDir.resolve("test-cases.json");
      Files.writeString(testCasesPath, """
        {
          "test_cases": [
            {"test_case_id": "TC1", "semantic_unit_id": "unit_1"},
            {"test_case_id": "TC2", "semantic_unit_id": "unit_2"},
            {"test_case_id": "TC3", "semantic_unit_id": "unit_1"}
          ]
        }
        """, StandardCharsets.UTF_8);

      String changedUnitsJson = "[\"unit_1\"]";
      SkillTestRunner runner = new SkillTestRunner(scope);
      String result = runner.mapUnits(new String[]{testCasesPath.toString(), changedUnitsJson});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      // TC1 and TC3 cover unit_1 (changed), TC2 covers unit_2 (unchanged)
      JsonNode rerun = root.path("rerun_test_case_ids");
      JsonNode carryforward = root.path("carryforward_test_case_ids");

      requireThat(rerun.size(), "rerunCount").isEqualTo(2);
      requireThat(carryforward.size(), "carryforwardCount").isEqualTo(1);
      requireThat(carryforward.get(0).asString(), "carryforwardTc").isEqualTo("TC2");
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
      Path testCasesPath = tempDir.resolve("test-cases.json");
      Files.writeString(testCasesPath, """
        {
          "test_cases": [
            {"test_case_id": "TC1", "semantic_unit_id": "unit_1"},
            {"test_case_id": "TC2", "semantic_unit_id": "unit_2"}
          ]
        }
        """, StandardCharsets.UTF_8);

      String changedUnitsJson = "[]";
      SkillTestRunner runner = new SkillTestRunner(scope);
      String result = runner.mapUnits(new String[]{testCasesPath.toString(), changedUnitsJson});

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
      SkillTestRunner runner = new SkillTestRunner(scope);
      String result = runner.initSprt(new String[]{rerunJson, "none"});

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
      // Prior benchmark has TC1 as ACCEPT
      Path priorPath = tempDir.resolve("prior.json");
      Files.writeString(priorPath, """
        {"test_cases":[
          {"test_case_id":"TC1","log_ratio":3.0,"passes":10,"fails":0,"runs":10,"decision":"ACCEPT"}
        ]}
        """, StandardCharsets.UTF_8);

      String rerunJson = "[\"TC1\"]";
      SkillTestRunner runner = new SkillTestRunner(scope);
      String result = runner.initSprt(new String[]{rerunJson, priorPath.toString(), "--prior-boost"});

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
   * (no prior benchmark available).
   */
  @Test
  public void initSprtWithEmptyPrior() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Pass "none" as prior path to indicate no prior benchmark
      String rerunJson = "[\"TC1\"]";
      SkillTestRunner runner = new SkillTestRunner(scope);
      String result = runner.initSprt(new String[]{rerunJson, "none"});

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
   * Verifies that check-boundary handles an empty test-cases.json (zero test cases) gracefully:
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
      SkillTestRunner runner = new SkillTestRunner(scope);
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
   * Verifies that persist-artifacts throws IllegalArgumentException when benchmark.json has
   * missing required fields (corrupted/empty artifacts directory).
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*test-cases.json not found.*")
  public void persistArtifactsRejectsCorruptBenchmarkJson() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create skill file
      Path skillFile = repoDir.resolve("skill.md");
      Files.writeString(skillFile, "---\ndescription: Test\n---\n# Body\n", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "skill.md");
      TestUtils.runGit(repoDir, "commit", "-m", "add skill");

      // Create artifacts dir without test-cases.json (simulates corruption/missing file)
      Path artifactsDir = tempDir.resolve("artifacts");
      Files.createDirectories(artifactsDir);
      // Intentionally do NOT create test-cases.json

      SkillTestRunner runner = new SkillTestRunner(scope);
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

      SkillTestRunner runner = new SkillTestRunner(scope);
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

      SkillTestRunner runner = new SkillTestRunner(scope);
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

      SkillTestRunner runner = new SkillTestRunner(scope);
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

      SkillTestRunner runner = new SkillTestRunner(scope);
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

      SkillTestRunner runner = new SkillTestRunner(scope);
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

      SkillTestRunner runner = new SkillTestRunner(scope);
      String result = runner.mergeResults(new String[]{statePath.toString(), "none", "[]"});

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

      SkillTestRunner runner = new SkillTestRunner(scope);
      String result = runner.mergeResults(new String[]{statePath.toString(), "none", "[]"});

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
      Path testCasesPath = tempDir.resolve("test-cases.json");
      Files.writeString(testCasesPath, """
        {"test_cases":[{"test_case_id":"TC1","semantic_unit_id":"unit_1"}]}
        """, StandardCharsets.UTF_8);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(baos, false, StandardCharsets.UTF_8);

      SkillTestRunner runner = new SkillTestRunner(scope);
      runner.run(new String[]{"map-units", testCasesPath.toString(), "[]"}, printStream);
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
      SkillTestRunner runner = new SkillTestRunner(scope);
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
      SkillTestRunner runner = new SkillTestRunner(scope);
      runner.run(new String[]{"nonexistent-command"}, System.out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that detect-changes reports skill_changed=false and all IDs carried forward
   * when the skill file is identical at both commits.
   */
  @Test
  public void detectChangesNoChanges() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create skill file and commit it
      Path skillFile = repoDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        model: haiku
        ---
        # Step 1
        Do something.
        """, StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "skill.md");
      TestUtils.runGit(repoDir, "commit", "-m", "add skill");

      // Get the SHA of the commit
      String sha = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD");

      // Create test-cases.json
      Path testCasesPath = tempDir.resolve("test-cases.json");
      Files.writeString(testCasesPath, """
        {"test_cases":[
          {"test_case_id":"TC1","semantic_unit_id":"unit_1"},
          {"test_case_id":"TC2","semantic_unit_id":"unit_2"}
        ]}
        """, StandardCharsets.UTF_8);

      SkillTestRunner runner = new SkillTestRunner(scope);
      String result = runner.detectChanges(new String[]{sha, skillFile.toString(), testCasesPath.toString()});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("skill_changed").asBoolean(), "skill_changed").isFalse();
      requireThat(root.path("rerun_test_case_ids").size(), "rerunCount").isEqualTo(0);
      requireThat(root.path("carryforward_test_case_ids").size(), "carryforwardCount").isEqualTo(2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that detect-changes reports frontmatter_changed=true and all IDs in rerun
   * when the frontmatter differs between commits.
   */
  @Test
  public void detectChangesFrontmatterChanged() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Commit the old skill content
      Path skillFile = repoDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Old description
        model: haiku
        ---
        # Step 1
        Do something.
        """, StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "skill.md");
      TestUtils.runGit(repoDir, "commit", "-m", "add skill");

      // Capture old SHA
      String oldSha = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD");

      // Write new skill file with changed frontmatter (same body)
      Files.writeString(skillFile, """
        ---
        description: New description
        model: haiku
        ---
        # Step 1
        Do something.
        """, StandardCharsets.UTF_8);

      // Create test-cases.json
      Path testCasesPath = tempDir.resolve("test-cases.json");
      Files.writeString(testCasesPath, """
        {"test_cases":[
          {"test_case_id":"TC1","semantic_unit_id":"unit_1"},
          {"test_case_id":"TC2","semantic_unit_id":"unit_2"}
        ]}
        """, StandardCharsets.UTF_8);

      SkillTestRunner runner = new SkillTestRunner(scope);
      String result = runner.detectChanges(new String[]{oldSha, skillFile.toString(), testCasesPath.toString()});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("frontmatter_changed").asBoolean(), "frontmatter_changed").isTrue();
      requireThat(root.path("rerun_test_case_ids").size(), "rerunCount").isEqualTo(2);
      requireThat(root.path("carryforward_test_case_ids").size(), "carryforwardCount").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that detect-changes reports body_changed=true and frontmatter_changed=false
   * and requires_unit_mapping=true when only the body differs between commits.
   */
  @Test
  public void detectChangesBodyOnlyChanged() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Commit the old skill content
      Path skillFile = repoDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        model: haiku
        ---
        # Step 1
        Original body content.
        """, StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "skill.md");
      TestUtils.runGit(repoDir, "commit", "-m", "add skill");

      // Capture old SHA
      String oldSha = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "HEAD");

      // Write new skill file with same frontmatter but different body
      Files.writeString(skillFile, """
        ---
        description: Test skill
        model: haiku
        ---
        # Step 1
        Changed body content.
        """, StandardCharsets.UTF_8);

      // Create test-cases.json
      Path testCasesPath = tempDir.resolve("test-cases.json");
      Files.writeString(testCasesPath, """
        {"test_cases":[{"test_case_id":"TC1","semantic_unit_id":"unit_1"}]}
        """, StandardCharsets.UTF_8);

      SkillTestRunner runner = new SkillTestRunner(scope);
      String result = runner.detectChanges(new String[]{oldSha, skillFile.toString(), testCasesPath.toString()});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("body_changed").asBoolean(), "body_changed").isTrue();
      requireThat(root.path("frontmatter_changed").asBoolean(), "frontmatter_changed").isFalse();
      requireThat(root.path("requires_unit_mapping").asBoolean(), "requires_unit_mapping").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that persist-artifacts writes benchmark.json with expected JSON fields.
   */
  @Test
  public void persistArtifactsWritesBenchmarkJson() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create skill file inside the git repo
      Path skillFile = repoDir.resolve("skill.md");
      Files.writeString(skillFile, """
        ---
        description: Test skill
        ---
        # Body
        """, StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "skill.md");
      TestUtils.runGit(repoDir, "commit", "-m", "add skill");

      // Create an artifacts dir with test-cases.json
      Path artifactsDir = tempDir.resolve("artifacts");
      Files.createDirectories(artifactsDir);
      Path testCasesJson = artifactsDir.resolve("test-cases.json");
      Files.writeString(testCasesJson, """
        {"test_cases":[{"test_case_id":"TC1","semantic_unit_id":"unit_1"}]}
        """, StandardCharsets.UTF_8);

      String sessionId = "test-session-001";
      String phase = "initial";

      SkillTestRunner runner = new SkillTestRunner(scope);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(baos, false, StandardCharsets.UTF_8);
      runner.persistArtifacts(
        new String[]{"skill.md", artifactsDir.toString(), sessionId, repoDir.toString(), phase},
        out);

      // Verify benchmark.json was created
      Path benchmarkJson = repoDir.resolve("benchmark").resolve("benchmark.json");
      requireThat(Files.exists(benchmarkJson), "benchmarkJsonExists").isTrue();

      String content = Files.readString(benchmarkJson, StandardCharsets.UTF_8);
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(content);

      requireThat(root.path("session_id").asString(), "session_id").isEqualTo(sessionId);
      requireThat(root.path("phase").asString(), "phase").isEqualTo(phase);
      requireThat(root.path("skill").path("path").asString(), "skill.path").isEqualTo("skill.md");
      requireThat(root.path("skill").path("sha256").asString(""), "skill.sha256").isNotBlank();
      requireThat(root.path("test_cases").path("sha256").asString(""), "test_cases.sha256").isNotBlank();

      // Assert exclusivity: no undocumented fields
      List<String> fieldNames = new ArrayList<>(root.propertyNames());
      Collections.sort(fieldNames);
      List<String> expectedFieldNames = List.of("phase", "session_id", "skill", "test_cases", "timestamp");
      requireThat(fieldNames, "fieldNames").isEqualTo(expectedFieldNames);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that persist-artifacts copies test-cases.json into the benchmark directory.
   */
  @Test
  public void persistArtifactsCopiesTestCasesJson() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = repoDir.resolve("skill.md");
      Files.writeString(skillFile, "---\ndescription: Test\n---\n# Body\n", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", "skill.md");
      TestUtils.runGit(repoDir, "commit", "-m", "add skill");

      Path artifactsDir = tempDir.resolve("artifacts");
      Files.createDirectories(artifactsDir);
      String testCasesContent = """
        {"test_cases":[{"test_case_id":"TC1","semantic_unit_id":"unit_1"}]}
        """;
      Files.writeString(artifactsDir.resolve("test-cases.json"), testCasesContent, StandardCharsets.UTF_8);

      SkillTestRunner runner = new SkillTestRunner(scope);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(baos, false, StandardCharsets.UTF_8);
      runner.persistArtifacts(
        new String[]{"skill.md", artifactsDir.toString(), "sess1", repoDir.toString(), "final"},
        out);

      // Verify test-cases.json was copied into benchmark/
      Path copiedTestCases = repoDir.resolve("benchmark").resolve("test-cases.json");
      requireThat(Files.exists(copiedTestCases), "testCasesJsonCopied").isTrue();

      // Verify the content matches
      String copiedContent = Files.readString(copiedTestCases, StandardCharsets.UTF_8);
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(copiedContent);
      requireThat(root.path("test_cases").size(), "testCasesCount").isEqualTo(1);
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
      SkillTestRunner runner = new SkillTestRunner(scope);
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
   * Verifies that merge-results uses prior benchmark stats for carryforward IDs instead of current SPRT state.
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

      // Prior benchmark has TC2 as ACCEPT with high log_ratio — carryforward should use these values
      Path priorBenchmarkPath = tempDir.resolve("prior_benchmark.json");
      Files.writeString(priorBenchmarkPath, """
        {"test_cases":[
          {"test_case_id":"TC1","log_ratio":2.8,"passes":9,"fails":0,"runs":9,"decision":"ACCEPT"},
          {"test_case_id":"TC2","log_ratio":3.5,"passes":12,"fails":1,"runs":13,"decision":"ACCEPT"}
        ]}
        """, StandardCharsets.UTF_8);

      SkillTestRunner runner = new SkillTestRunner(scope);
      // TC2 is in the carryforward set: its stats should come from prior benchmark, not SPRT state
      String result = runner.mergeResults(
        new String[]{statePath.toString(), priorBenchmarkPath.toString(), "[\"TC2\"]"});

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
      // Prior benchmark has TC2 log_ratio=3.5; SPRT state has 0.5 — must use prior value
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
