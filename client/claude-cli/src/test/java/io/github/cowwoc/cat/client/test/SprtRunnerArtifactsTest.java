/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.tool.skills.SharedSecrets;
import io.github.cowwoc.cat.tool.skills.SprtRunner;
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
 * Tests SPRT artifact persistence, change detection, and runner-worktree setup helpers.
 * <p>
 * Each test is self-contained with no shared state.
 */
public final class SprtRunnerArtifactsTest
{
  /**
   * Verifies that write-test-results computes reject overall_decision.
   */
  @Test
  public void writeTestResultsRejectDecisionWrites() throws IOException, InterruptedException
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
  public void removeRunnerWorktreesNoMatchingWorktrees() throws IOException, InterruptedException
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
  public void detectChangesSha256MatchAllCarried() throws IOException, InterruptedException
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
  public void detectChangesInvalidShaShortStringThrows() throws IOException, InterruptedException
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
  public void detectChangesInvalidShaNotHexThrows() throws IOException, InterruptedException
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
  public void detectChangesEmptyTestDirectoryReturns() throws IOException, InterruptedException
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
   * Verifies that extract-model uses the Claude default when SKILL.md is missing the model frontmatter field.
   */
  @Test
  public void extractModelRejectsMissingModel() throws IOException, InterruptedException
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
  public void stakeholderReviewPromptIncludesWorking() throws IOException
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
  public void persistArtifactsWritesInstructionTest() throws IOException, InterruptedException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      createPersistArtifactsSkill(repoDir);
      Path artifactsDir = createArtifactsDirectory(tempDir);

      String sessionId = "test-session-001";
      String phase = "initial";
      runPersistArtifacts(scope, repoDir, artifactsDir, sessionId, phase);
      assertInstructionTestJson(scope, repoDir, sessionId, phase);
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
   * Creates the tracked skill file used by persist-artifacts tests.
   *
   * @param repoDir the temporary repository root
   * @throws IOException if file creation or git commands fail
   * @throws InterruptedException if interrupted while committing
   */
  private void createPersistArtifactsSkill(Path repoDir) throws IOException, InterruptedException
  {
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
  }

  /**
   * Creates the artifact directory containing one markdown testcase.
   *
   * @param tempDir the temporary test root
   * @return the populated artifacts directory
   * @throws IOException if file creation fails
   */
  private Path createArtifactsDirectory(Path tempDir) throws IOException
  {
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
    return artifactsDir;
  }

  /**
   * Runs persist-artifacts for the canonical test skill.
   *
   * @param scope the CLI scope
   * @param repoDir the temporary repository root
   * @param artifactsDir the populated artifacts directory
   * @param sessionId the session id to persist
   * @param phase the persisted execution phase
   * @throws IOException if the runner fails
   * @throws InterruptedException if interrupted while running the command
   */
  private void runPersistArtifacts(TestClaudeTool scope, Path repoDir, Path artifactsDir,
    String sessionId, String phase) throws IOException, InterruptedException
  {
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
  }

  /**
   * Asserts the contents of the persisted instruction-test.json artifact.
   *
   * @param scope the CLI scope
   * @param repoDir the temporary repository root
   * @param sessionId the expected session id
   * @param phase the expected execution phase
   * @throws IOException if the artifact cannot be read
   */
  private void assertInstructionTestJson(TestClaudeTool scope, Path repoDir, String sessionId,
    String phase) throws IOException
  {
    Path instructionTestJson =
      repoDir.resolve(".cat/work/instruction-test/test-skill/instruction-test.json");
    requireThat(Files.exists(instructionTestJson), "instructionTestJsonExists").isTrue();

    String content = Files.readString(instructionTestJson, StandardCharsets.UTF_8);
    JsonMapper mapper = scope.getJsonMapper();
    JsonNode root = mapper.readTree(content);

    requireThat(root.path("session_id").asString(), "session_id").isEqualTo(sessionId);
    requireThat(root.path("model_id").asString(), "model_id").isEqualTo("claude-haiku-4-5");
    requireThat(root.path("phase").asString(), "phase").isEqualTo(phase);
    requireThat(root.path("skill").path("path").asString(), "skill.path").
      isEqualTo("client/plugin/skills/common/test-skill/skill.md");
    requireThat(root.path("skill").path("sha256").asString(""), "skill.sha256").isNotBlank();
    requireThat(root.path("test_cases").path("path").asString(), "test_cases.path").
      isEqualTo("client/plugin/skills/common/test-skill/first-use");
    requireThat(root.path("test_cases").path("sha256").asString(""), "test_cases.sha256").
      isNotBlank();

    List<String> fieldNames = new ArrayList<>(root.propertyNames());
    Collections.sort(fieldNames);
    List<String> expectedFieldNames = List.of("model_id", "phase", "session_id", "skill",
      "test_cases", "timestamp");
    requireThat(fieldNames, "fieldNames").isEqualTo(expectedFieldNames);
  }

  /**
   * Verifies that persist-artifacts throws IllegalArgumentException when the worktree root does not exist.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*worktree root not found.*")
  public void persistArtifactsThrowsWhenWorktreeRoot() throws IOException, InterruptedException
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
        {"effort":"high","sprt_state":{
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
   * Verifies that merge-results can read carryforward stats from a persisted per-model test-results entry.
   */
  @Test
  public void mergeResultsUsesPerModelEntry() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path statePath = tempDir.resolve("sprt_state.json");
      Files.writeString(statePath, """
        {"effort":"high","sprt_state":{
          "TC1":{"log_ratio":3.0,"passes":10,"fails":0,"runs":10,
                 "decision": "ACCEPT","carried_forward":false,"smoke_runs_done":3},
          "TC2":{"log_ratio":0.5,"passes":3,"fails":2,"runs":5,
                 "decision": "INCONCLUSIVE","carried_forward":false,"smoke_runs_done":3}
        }}
        """, StandardCharsets.UTF_8);

      Path priorInstructionTestPath = tempDir.resolve("prior_instruction_test.json");
      Files.writeString(priorInstructionTestPath, """
        {"claude-haiku-4-5|high":{
          "model_id":"claude-haiku-4-5",
          "effort":"high",
          "sprt":{"test_cases":[
            {"test_case_id":"TC2","log_ratio":3.5,"passes":12,"fails":1,"runs":13,
             "decision":"ACCEPT"}
          ]}
        }}
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      String result = runner.mergeResults(
        new String[]{statePath.toString(), priorInstructionTestPath.toString(), "[\"TC2\"]",
          "claude-haiku-4-5"});

      JsonNode root = scope.getJsonMapper().readTree(result);
      JsonNode tc2 = null;
      for (JsonNode tc : root.path("test_cases"))
      {
        if ("TC2".equals(tc.path("test_case_id").asString()))
        {
          tc2 = tc;
          break;
        }
      }
      requireThat(tc2, "tc2").isNotNull();
      requireThat(tc2.path("log_ratio").asDouble(), "tc2.log_ratio").isEqualTo(3.5);
      requireThat(tc2.path("carried_forward").asBoolean(), "tc2.carried_forward").isTrue();
      requireThat(root.path("overall_decision").asString(), "overall_decision").isEqualTo("ACCEPT");
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
  public void removeRunnerWorktreeRemovesWorktreeAnd() throws IOException, InterruptedException
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
   * Verifies that create-runner-worktrees rejects a symlinked test-runs session directory.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*escapes the test-runs directory.*")
  public void createRunnerWorktreesRejectsSymlinkDir() throws IOException, InterruptedException
  {
    Path mainRepo = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    Path outside = Files.createTempDirectory("outside-session-dir-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sprtStatePath = mainRepo.resolve(".cat/work/sprt-state.json");
      Files.createDirectories(sprtStatePath.getParent());
      Files.writeString(sprtStatePath,
        "{\"sprt_state\":{\"tc1\":{\"decision\":\"ACCEPT\",\"runs\":3}}}",
        StandardCharsets.UTF_8);

      Path testRunsRoot = mainRepo.resolve(".cat/work/test-runs");
      Files.createDirectories(testRunsRoot);
      try
      {
        Files.createSymbolicLink(testRunsRoot.resolve("test-session-id"), outside);
      }
      catch (UnsupportedOperationException | IOException e)
      {
        throw new SkipException("Symbolic links are not available in this test environment", e);
      }

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.createRunnerWorktrees(new String[]{
        mainRepo.toString(), sprtStatePath.toString(),
        "my-issue", "test-session-id"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(outside);
    }
  }

  /**
   * Verifies that create-runner-worktrees rejects a symlinked test-runs root.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*escapes the test-runs directory.*")
  public void rejectsSymlinkedTestRunsRoot() throws IOException, InterruptedException
  {
    Path mainRepo = TestUtils.createTempGitRepo("my-issue");
    Path tempDir = Files.createTempDirectory("test-skill-test-runner-");
    Path outside = Files.createTempDirectory("outside-test-runs-root-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path sprtStatePath = mainRepo.resolve(".cat/work/sprt-state.json");
      Files.createDirectories(sprtStatePath.getParent());
      Files.writeString(sprtStatePath,
        "{\"sprt_state\":{\"tc1\":{\"decision\":\"ACCEPT\",\"runs\":3}}}",
        StandardCharsets.UTF_8);

      Path workDir = mainRepo.resolve(".cat/work");
      Files.createDirectories(workDir);
      Path testRunsRoot = workDir.resolve("test-runs");
      try
      {
        Files.createSymbolicLink(testRunsRoot, outside);
      }
      catch (UnsupportedOperationException | IOException e)
      {
        throw new SkipException("Symbolic links are not available in this test environment", e);
      }

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      runner.createRunnerWorktrees(new String[]{
        mainRepo.toString(), sprtStatePath.toString(),
        "my-issue", "test-session-id"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(outside);
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
}
