/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.common.test;

import io.github.cowwoc.cat.tool.AbstractCliTool;
import io.github.cowwoc.cat.tool.skills.SharedSecrets;
import io.github.cowwoc.cat.tool.skills.SprtRunner;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests detect-changes invalidation behavior.
 */
public final class SprtDetectChangesTest
{
  /**
   * Verifies that companion markdown changes invalidate the skill even when the primary skill file is unchanged.
   *
   * @throws IOException if fixture setup fails
   */
  @Test
  public void companionChangeTriggersSkillChanged() throws IOException
  {
    Path worktree = Files.createTempDirectory("sprt-detect-changes-companion-");
    try (TestCliTool scope = new TestCliTool(worktree))
    {
      Path skillDir = Files.createDirectories(worktree.resolve("skill"));
      Path skillFile = skillDir.resolve("SKILL.md");
      Path firstUseFile = skillDir.resolve("first-use.md");
      Files.writeString(skillFile, """
        ---
        description: Example
        model: haiku
        ---
        # Use
        """, StandardCharsets.UTF_8);
      Path testDir = Files.createDirectories(worktree.resolve("tests"));
      Files.writeString(testDir.resolve("tc1.md"), "# tc1\n", StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      Files.writeString(firstUseFile, "original first use\n", StandardCharsets.UTF_8);
      JsonNode baseline = scope.getJsonMapper().readTree(
        runner.detectChanges(new String[]{
          SharedSecrets.sha256Bytes(Files.readAllBytes(skillFile)),
          skillFile.toString(), testDir.toString()}));
      String metadata = baseline.path("skill_dependency_metadata").toString();
      Files.writeString(firstUseFile, "updated first use\n", StandardCharsets.UTF_8);

      JsonNode root = scope.getJsonMapper().readTree(
        runner.detectChanges(new String[]{metadata, skillFile.toString(), testDir.toString()}));

      requireThat(root.path("skill_changed").asBoolean(), "skillChanged").isTrue();
      requireThat(root.path("rerun_test_case_ids").size(), "rerunCount").isEqualTo(1);
      requireThat(root.path("skill_dependency_metadata").path("files").size(),
        "dependencyFileCount").isEqualTo(2);
    }
    finally
    {
      deleteRecursively(worktree);
    }
  }

  /**
   * Verifies that structured metadata preserves unchanged dependencies and reports prior test-case IDs.
   *
   * @throws IOException if fixture setup fails
   */
  @Test
  public void metadataCarriesForwardPriorIds() throws IOException
  {
    Path worktree = Files.createTempDirectory("sprt-detect-changes-metadata-");
    try (TestCliTool scope = new TestCliTool(worktree))
    {
      Path skillDir = Files.createDirectories(worktree.resolve("skill"));
      Path skillFile = skillDir.resolve("SKILL.md");
      Path firstUseFile = skillDir.resolve("first-use.md");
      Files.writeString(skillFile, """
        ---
        description: Example
        model: haiku
        ---
        # Use
        """, StandardCharsets.UTF_8);
      Files.writeString(firstUseFile, "stable first use\n", StandardCharsets.UTF_8);

      Path testDir = Files.createDirectories(worktree.resolve("tests"));
      Files.writeString(testDir.resolve("tc1.md"), "# tc1\n", StandardCharsets.UTF_8);
      Files.writeString(testDir.resolve("tc2.md"), "# tc2\n", StandardCharsets.UTF_8);
      Files.writeString(testDir.resolve("test-results.json"), """
        {
          "sprt": {
            "test_cases": [
              {"test_case_id":"tc2"},
              {"test_case_id":"tc1"}
            ]
          }
        }
        """, StandardCharsets.UTF_8);

      SprtRunner runner = new SprtRunner(scope, "2.1.87");
      JsonNode firstRun = scope.getJsonMapper().readTree(
        runner.detectChanges(new String[]{
          SharedSecrets.sha256Bytes(Files.readAllBytes(skillFile)),
          skillFile.toString(), testDir.toString()}));
      String metadata = firstRun.path("skill_dependency_metadata").toString();

      JsonNode secondRun = scope.getJsonMapper().readTree(
        runner.detectChanges(new String[]{metadata, skillFile.toString(), testDir.toString()}));

      requireThat(secondRun.path("skill_changed").asBoolean(), "skillChanged").isFalse();
      requireThat(secondRun.path("carryforward_test_case_ids").size(), "carryforwardCount").isEqualTo(2);
      requireThat(secondRun.path("prior_test_case_ids").size(), "priorCount").isEqualTo(2);
      requireThat(secondRun.path("prior_test_case_ids").get(0).asString(), "priorId1").isEqualTo("tc2");
      requireThat(secondRun.path("prior_test_case_ids").get(1).asString(), "priorId2").isEqualTo("tc1");
    }
    finally
    {
      deleteRecursively(worktree);
    }
  }

  /**
   * Deletes a temporary directory tree.
   *
   * @param root the root to delete
   * @throws IOException if deletion fails
   */
  private static void deleteRecursively(Path root) throws IOException
  {
    if (Files.notExists(root))
      return;
    try (java.util.stream.Stream<Path> stream = Files.walk(root))
    {
      for (Path path : stream.sorted(Comparator.reverseOrder()).toList())
        Files.deleteIfExists(path);
    }
  }

  /**
   * Minimal CLI test scope for engine-neutral common-cli tests.
   */
  private static final class TestCliTool extends AbstractCliTool
  {
    /**
     * Creates a Codex-shaped CLI scope rooted at a temporary worktree.
     *
     * @param worktree the temporary worktree and plugin root
     */
    private TestCliTool(Path worktree)
    {
      super("test-session", worktree, worktree, worktree.resolve(".codex"),
        worktree.resolve(".codex"), Path.of(".codex-plugin/plugin.json"),
        List.of(), Path.of(".codex-plugin/plugin.json"), worktree, "UTC", "");
    }
  }
}
