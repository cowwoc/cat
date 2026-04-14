/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.internal.SharedSecrets;
import io.github.cowwoc.cat.claude.hook.skills.InstructionTestRunner;
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
  public void extractUnitsFileNotFound() throws IOException, InterruptedException
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String model = runner.extractModel(new String[]{skillFile.toString()});
      requireThat(model, "model").isEqualTo("claude-sonnet-4-5-20250929");
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
  public void extractModelDefaultsToHaikuWhenFieldMissing() throws IOException, InterruptedException
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
  public void initSprtFreshStateNoPrior() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      String rerunJson = "[\"TC1\",\"TC2\"]";
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      Path sprtStatePath = tempDir.resolve(".cat/work/sprt-state.json");
      String result = runner.initSprt(new String[]{
        sprtStatePath.toString(), rerunJson, "none", "claude-haiku-4-5-20251001"});

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
   * Verifies that init-sprt writes model_id to the state file so subsequent calls with the state
   * as prior results can validate model consistency.
   */
  @Test
  public void initSprtWritesModelId() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      Path sprtStatePath = tempDir.resolve(".cat/work/sprt-state.json");
      runner.initSprt(new String[]{
        sprtStatePath.toString(), "[\"tc1\"]", "none", "claude-haiku-4-5-20251001"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(sprtStatePath.toFile());
      requireThat(root.path("model_id").asString(), "modelId").isEqualTo("claude-haiku-4-5-20251001");
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
        {"model_id":"claude-haiku-4-5-20251001","test_cases":[
          {"test_case_id":"TC1","log_ratio":3.0,"passes":10,"fails":0,"runs":10,"decision":"ACCEPT"}
        ]}
        """, StandardCharsets.UTF_8);

      String rerunJson = "[\"TC1\"]";
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      Path sprtStatePath = tempDir.resolve(".cat/work/sprt-state.json");
      runner.initSprt(new String[]{
        sprtStatePath.toString(), rerunJson, priorPath.toString(),
        "claude-haiku-4-5-20251001", "--prior-boost"});

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
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      runner.initSprt(new String[]{sprtStatePath.toString(), rerunJson, "none", "claude-haiku-4-5-20251001"});

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
        "decision":"INCONCLUSIVE","carried_forward":false,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
        "decision":"INCONCLUSIVE","carried_forward":false,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
        "{\"model_id\":\"claude-haiku-4-5-20251001\",\"sprt_state\":{\"TC1\":{\"log_ratio\":0.0," +
        "\"passes\":0,\"fails\":0,\"runs\":0,\"decision\":\"INCONCLUSIVE\"," +
        "\"carried_forward\":false,\"smoke_runs_done\":0}}}",
        StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      runner.updateSprt(new String[]{statePath.toString(), "TC1", "true"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(statePath.toFile());
      requireThat(root.path("model_id").asString(), "model_id").isEqualTo("claude-haiku-4-5-20251001");
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
  public void smokeStatusInSmokePhase() throws IOException, InterruptedException
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
  public void smokeStatusEscalates() throws IOException, InterruptedException
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
  public void mergeResultsAllAccept() throws IOException, InterruptedException
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
  public void mergeResultsAnyRejectProducesRejectOverall() throws IOException, InterruptedException
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
        {"sprt_state":{"tc1":{"log_ratio":2.944,"passes":9,"fails":1,"runs":10,
        "decision":"ACCEPT","carried_forward":false,"smoke_runs_done":3}}}
        """, StandardCharsets.UTF_8);

      Path tempDir = Files.createTempDirectory("test-scope-");
      try (var scope = new TestClaudeTool(tempDir, tempDir))
      {
        InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
   * Verifies that write-test-results computes REJECT overall_decision when any TC is REJECT.
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
        {"sprt_state":{
          "tc1":{"log_ratio":2.944,"passes":9,"fails":1,"runs":10,
                 "decision":"ACCEPT","carried_forward":false,"smoke_runs_done":3},
          "tc2":{"log_ratio":-2.944,"passes":1,"fails":9,"runs":10,
                 "decision":"REJECT","carried_forward":false,"smoke_runs_done":3}
        }}
        """, StandardCharsets.UTF_8);

      Path tempDir = Files.createTempDirectory("test-scope-");
      try (var scope = new TestClaudeTool(tempDir, tempDir))
      {
        InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
        InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
        InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
  public void runThrowsOnUnknownCommand() throws IOException, InterruptedException
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
  public void extractModelUsesModelSelectionMappingForSonnetSkill() throws IOException, InterruptedException
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
  public void extractModelFallsBackToHaikuWhenNotInModelSelection() throws IOException, InterruptedException
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
  public void persistArtifactsWritesInstructionTestJson() throws IOException, InterruptedException
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
  public void persistArtifactsCopiesMdFiles() throws IOException, InterruptedException
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
  public void persistArtifactsThrowsWhenWorktreeRootMissing() throws IOException, InterruptedException
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

  /**
   * Verifies that create-isolation-branch includes {@code tc_ids_json} in its return JSON,
   * containing an ordered array of opaque TC IDs derived from sorted test case filenames.
   */
  @Test
  public void createIsolationBranchIncludesTcIdsJson() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("my-issue");
    Path pluginRoot = Files.createTempDirectory("test-plugin-root-");
    try (var scope = new TestClaudeTool(repoDir, pluginRoot))
    {
      // Create stub extract-turns binary that copies input to turn1.md in dest dir
      Path binDir = pluginRoot.resolve("client/bin");
      Files.createDirectories(binDir);
      Path extractTurnsBin = binDir.resolve("extract-turns");
      Files.writeString(extractTurnsBin, """
        #!/bin/bash
        mkdir -p "$2"
        cp "$1" "$2/turn1.md"
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.createIsolationBranch(
        new String[]{repoDir.toString(), testDir.toString(), "my-issue"});

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      // tc_ids_json must be a JSON array of opaque IDs in sorted filename order
      requireThat(root.has("tc_ids_json"), "hasTcIdsJson").isTrue();
      JsonNode tcIdsJson = root.path("tc_ids_json");
      requireThat(tcIdsJson.isArray(), "isArray").isTrue();
      requireThat(tcIdsJson.size(), "size").isEqualTo(2);
      // alpha-test.md sorts first → tc1, beta-test.md → tc2
      requireThat(tcIdsJson.get(0).asString(), "tcId0").isEqualTo("tc1");
      requireThat(tcIdsJson.get(1).asString(), "tcId1").isEqualTo("tc2");
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
   * Verifies that get-json-field extracts a top-level string field from a JSON object.
   */
  @Test
  public void getJsonFieldExtractsStringValue() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.createRunnerWorktrees(new String[]{
        mainRepo.toString(), sprtStatePath.toString(),
        "my-issue", mainRepo.toString(), "test-session-id"});

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

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
    Path outputDir = Files.createTempDirectory("test-output-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/tc1_turn1"),
        "test turn content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-sanitized");

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-sanitized", "plugin/tests/myskill",
        "tc1", "/fake/runner", outputDir.toString(), "1", "/fake/project"});

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
      TestUtils.deleteDirectoryRecursively(outputDir);
    }
  }

  /**
   * Verifies that prepare-trial falls back to the project jlink bin when runner worktree
   * does not have a jlink directory.
   */
  @Test
  public void prepareTrialFallsBackToProjectJlinkBin() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    Path runnerWorktree = Files.createTempDirectory("runner-");
    Path claudeProjectDir = Files.createTempDirectory("project-");
    Path outputDir = Files.createTempDirectory("test-output-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/tc1_turn1"),
        "content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-sanitized");

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-sanitized", "plugin/tests/myskill",
        "tc1", runnerWorktree.toString(), outputDir.toString(), "1", claudeProjectDir.toString()});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      requireThat(pairs.get("jlink_bin"), "jlink_bin").
        startsWith(claudeProjectDir.toString()).
        endsWith("/client/target/jlink/bin");
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
      Files.writeString(repoDir.resolve("plugin/tests/myskill/tc1_turn1"),
        "content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-sanitized");

      // Create jlink/bin dir in runner worktree
      Files.createDirectories(runnerWorktree.resolve("client/target/jlink/bin"));

      // prepareTrial writes a VERSION file to the jlink dir using the plugin version from plugin.json
      Files.createDirectories(tempDir.resolve(".claude-plugin"));
      Files.writeString(tempDir.resolve(".claude-plugin/plugin.json"),
        "{\"version\":\"2.1.87\"}", StandardCharsets.UTF_8);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-sanitized", "plugin/tests/myskill",
        "tc1", runnerWorktree.toString(), outputDir.toString(), "1", claudeProjectDir.toString()});

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
    Path outputDir = Files.createTempDirectory("test-output-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/tc1_turn1"),
        "content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-sanitized");

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-sanitized", "plugin/tests/myskill",
        "tc1", "/fake/my-runner", outputDir.toString(), "1", "/fake/project"});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      String promptContent = Files.readString(Path.of(pairs.get("prompt_file")),
        StandardCharsets.UTF_8);
      // CWD tag must be present (not RUNNER_WORKTREE — that tag was removed as attentional noise)
      requireThat(promptContent, "promptContent").contains("[CWD: /fake/my-runner]");
      // Positive mandate: every path MUST begin with the CWD value
      requireThat(promptContent, "promptContent").contains(
        "Every path argument passed to Write, Edit, or Bash MUST begin with the exact CWD value above");
      // Concrete example anchors the correct construction pattern
      requireThat(promptContent, "promptContent").contains("/fake/my-runner/");
      // Mandatory execution instruction must still be present
      requireThat(promptContent, "promptContent").contains("Execute the task below immediately");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
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
    Path outputDir = Files.createTempDirectory("test-output-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/tc1_turn1"),
        "content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-sanitized");

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-sanitized", "plugin/tests/myskill",
        "tc1", "/fake/runner", outputDir.toString(), "3", "/fake/project"});

      Map<String, String> pairs = new LinkedHashMap<>();
      for (String line : result.strip().split("\n"))
      {
        int eq = line.indexOf('=');
        if (eq > 0)
          pairs.put(line.substring(0, eq), line.substring(eq + 1));
      }
      requireThat(pairs.get("output_json"), "output_json").
        isEqualTo(outputDir + "/tc1_run3.json");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
      TestUtils.deleteDirectoryRecursively(tempDir);
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
      Files.createDirectories(repoDir.resolve("plugin/tests/myskill"));
      Files.writeString(repoDir.resolve("plugin/tests/myskill/tc1_turn1"),
        "content", StandardCharsets.UTF_8);
      TestUtils.runGit(repoDir, "add", ".");
      TestUtils.runGit(repoDir, "commit", "-m", "add turn files");
      TestUtils.runGit(repoDir, "checkout", "-b", "my-issue-sanitized");

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
      String result = runner.prepareTrial(new String[]{
        repoDir.toString(), "my-issue-sanitized", "plugin/tests/myskill",
        "tc1", runnerWorktree.toString(), outputDir.toString(), "1", "/fake/project"});

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
        "{\"sprt_state\":{\"tc1\":{\"decision\":\"ACCEPT\",\"runs\":3,\"log_ratio\":2.944," +
        "\"passes\":3,\"fails\":0}}}",
        StandardCharsets.UTF_8);

      Path testDirPath = mainRepo.resolve("plugin/tests/myskill");
      Files.createDirectories(testDirPath);

      InstructionTestRunner runner = new InstructionTestRunner(scope, "2.1.87");
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
}
