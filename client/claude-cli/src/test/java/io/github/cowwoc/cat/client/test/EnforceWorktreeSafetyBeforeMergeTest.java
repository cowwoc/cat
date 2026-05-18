/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.TaskHandler;
import io.github.cowwoc.cat.claude.hook.task.EnforceWorktreeSafetyBeforeMerge;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link EnforceWorktreeSafetyBeforeMerge}.
 */
public final class EnforceWorktreeSafetyBeforeMergeTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";

  /**
   * Verifies that non-merge subagent types are always allowed.
   *
   * @throws Exception if JSON parsing fails
   */
  @Test
  public void nonMergeSubagentTypeIsAllowed() throws Exception
  {
    EnforceWorktreeSafetyBeforeMerge handler = new EnforceWorktreeSafetyBeforeMerge();
    JsonNode toolInput = new JsonMapper().readTree("""
      {"subagent_type":"cat:work-execute"}""");

    TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "/workspace");

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that merge subagent type with blank cwd is allowed.
   *
   * @throws Exception if JSON parsing fails
   */
  @Test
  public void mergeWithBlankCwdIsAllowed() throws Exception
  {
    EnforceWorktreeSafetyBeforeMerge handler = new EnforceWorktreeSafetyBeforeMerge();
    JsonNode toolInput = new JsonMapper().readTree("""
      {"subagent_type":"cat:work-merge"}""");

    TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "");

    requireThat(result.blocked(), "blocked").isFalse();
  }

  /**
   * Verifies that merge subagent type is blocked when cwd is inside a worktree path.
   *
   * @throws Exception if JSON parsing fails
   */
  @Test
  public void mergeInsideWorktreeIsBlocked() throws Exception
  {
    EnforceWorktreeSafetyBeforeMerge handler = new EnforceWorktreeSafetyBeforeMerge();
    JsonNode toolInput = new JsonMapper().readTree("""
      {"subagent_type":"cat:work-merge"}""");
    String cwd = "/home/node/.cat/worktrees/2.1-sample-issue/client";

    TaskHandler.Result result = handler.check(toolInput, SESSION_ID, cwd);

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("cd /workspace");
    requireThat(result.reason(), "reason").contains(cwd);
  }

  /**
   * Verifies that merge subagent type is allowed when cwd is outside worktrees.
   *
   * @throws Exception if JSON parsing fails
   */
  @Test
  public void mergeOutsideWorktreeIsAllowed() throws Exception
  {
    EnforceWorktreeSafetyBeforeMerge handler = new EnforceWorktreeSafetyBeforeMerge();
    JsonNode toolInput = new JsonMapper().readTree("""
      {"subagent_type":"cat:work-merge"}""");

    TaskHandler.Result result = handler.check(toolInput, SESSION_ID, "/workspace");

    requireThat(result.blocked(), "blocked").isFalse();
  }
}
