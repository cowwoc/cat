/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.AskHandler;
import io.github.cowwoc.cat.claude.hook.ask.VerifyStateInCommit;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Integration test for the squash auto-close index.json → approval gate flow.
 * <p>
 * Verifies that after the squash phase auto-closes {@code index.json}, the approval gate check
 * ({@link VerifyStateInCommit}) allows the gate. Also verifies that the gate is blocked when
 * {@code index.json} is not yet closed.
 */
public final class SquashAutoCloseIndexJsonTest
{
  /**
   * Verifies the full squash-then-approve flow:
   * <ol>
   *   <li>Creates a temp git repo on a CAT issue branch ({@code 2.1-squash-flow}).</li>
   *   <li>Creates {@code index.json} with {@code "status": "in-progress"} at the expected path.</li>
   *   <li>Makes a {@code bugfix:} commit without staging {@code index.json}.</li>
   *   <li>Simulates the squash auto-close step: updates {@code index.json} to {@code "closed"},
   *       stages it, and amends the last commit.</li>
   *   <li>Asserts that {@link VerifyStateInCommit} allows the approval gate (EXIT 0 equivalent).</li>
   *   <li>Resets {@code index.json} to {@code "in-progress"} (simulating a not-yet-squashed state).</li>
   *   <li>Asserts that {@link VerifyStateInCommit} blocks the approval gate with the expected message.</li>
   * </ol>
   *
   * @throws IOException if test setup or assertion fails
   */
  @Test
  public void squashAutoCloseThenApproveFlowAllowsGateWhenClosed() throws IOException
  {
    // Step 1: Create a temp git repo on the CAT issue branch "2.1-squash-flow"
    Path repoDir = TestUtils.createTempGitRepo("2.1-squash-flow");
    try (TestClaudeHook scope = new TestClaudeHook(repoDir, repoDir, repoDir))
    {
      // Step 2: Create index.json with "in-progress" status at the expected path for "2.1-squash-flow"
      // Branch format: MAJOR.MINOR-issue-name -> .cat/issues/v2/v2.1/squash-flow/index.json
      Path issueDir = repoDir.resolve(".cat").
        resolve("issues").
        resolve("v2").
        resolve("v2.1").
        resolve("squash-flow");
      Files.createDirectories(issueDir);
      Path indexJsonPath = issueDir.resolve("index.json");
      Files.writeString(indexJsonPath, "{\"status\":\"in-progress\"}");

      // Step 3: Make a bugfix: commit without staging index.json
      Path dummyFile = repoDir.resolve("feature.txt");
      Files.writeString(dummyFile, "implementation");
      TestUtils.runGit(repoDir, "add", "feature.txt");
      TestUtils.runGit(repoDir, "commit", "-m", "bugfix: implement feature");

      // Step 4: Simulate squash auto-close step
      // Update index.json status to "closed", stage it, amend the last commit
      Files.writeString(indexJsonPath, "{\"status\":\"closed\"}");
      TestUtils.runGit(repoDir, "add", indexJsonPath.toString());
      TestUtils.runGit(repoDir, "commit", "--amend", "--no-edit");

      // Step 5: Call VerifyStateInCommit and assert it allows the approval gate
      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit(scope);
      JsonNode approvalInput = mapper.readTree("""
        {"question": "Do you approve and merge these changes?", \
        "options": ["Approve and merge", "Reject"]}""");

      AskHandler.Result resultAfterClose = handler.check(approvalInput, "test-session");

      requireThat(resultAfterClose.blocked(), "blockedAfterAutoClose").isFalse();
      requireThat(resultAfterClose.reason(), "reasonAfterAutoClose").isEmpty();

      // Step 6: Reset index.json to "in-progress" (simulating gate presented before squash)
      Files.writeString(indexJsonPath, "{\"status\":\"in-progress\"}");

      // Step 7: Assert that VerifyStateInCommit now blocks the approval gate
      AskHandler.Result resultInProgress = handler.check(approvalInput, "test-session");

      requireThat(resultInProgress.blocked(), "blockedWhenInProgress").isTrue();
      requireThat(resultInProgress.reason(), "reasonWhenInProgress").contains("BLOCKED");
      requireThat(resultInProgress.reason(), "reasonWhenInProgress").contains("index.json");
      requireThat(resultInProgress.reason(), "reasonWhenInProgress").contains("closed");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }

  /**
   * Verifies that the approval gate is blocked when index.json has "in-progress" status before
   * the squash auto-close step has run.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void approvalGateBlockedBeforeSquashAutoClose() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("2.1-pre-squash-issue");
    try (TestClaudeHook scope = new TestClaudeHook(repoDir, repoDir, repoDir))
    {
      // Create index.json with "in-progress" status (not yet closed by squash)
      Path issueDir = repoDir.resolve(".cat").
        resolve("issues").
        resolve("v2").
        resolve("v2.1").
        resolve("pre-squash-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"in-progress\"}");

      // Make a bugfix: commit without closing index.json
      Path dummyFile = repoDir.resolve("work.txt");
      Files.writeString(dummyFile, "work");
      TestUtils.runGit(repoDir, "add", "work.txt");
      TestUtils.runGit(repoDir, "commit", "-m", "bugfix: do work");

      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit(scope);
      JsonNode approvalInput = mapper.readTree("""
        {"question": "Do you approve and merge?", "options": ["Approve and merge", "Reject"]}""");

      AskHandler.Result result = handler.check(approvalInput, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("BLOCKED");
      requireThat(result.reason(), "reason").contains("squash");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }
}
