/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.Config;
import io.github.cowwoc.cat.agent.TrustLevel;
import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.util.SessionFileUtils;
import io.github.cowwoc.cat.claude.hook.util.StructuredMergeApproval;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Block direct invocations of the merge-and-cleanup binary when trust=medium/low without explicit
   * structured user approval.
 * <p>
 * Agents that skip the work-with-issue approval gate (Step 9) and invoke the merge-and-cleanup binary
 * directly via Bash tool bypass the Task-tool-level enforcement in EnforceApprovalBeforeMerge. This
 * handler closes that bypass route by enforcing the same approval check at the Bash tool level.
 */
public final class BlockUnauthorizedMergeCleanup implements BashHandler
{
  /**
   * Number of recent JSONL lines to scan for approval messages. 75 lines covers approximately the last
   * several user interactions, which is sufficient to detect recent approval without scanning the entire
   * session file.
   */
  private static final int RECENT_LINES_TO_SCAN = 75;

  private final ClaudeHook scope;

  /**
   * Creates a new handler for blocking unauthorized merge-and-cleanup invocations.
   *
   * @param scope the JVM scope
   * @throws NullPointerException if {@code scope} is null
   */
  public BlockUnauthorizedMergeCleanup(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check()
  {
    String command = scope.getCommand();
    requireThat(command, "command").isNotNull();

    // Only intercept commands that invoke the merge-and-cleanup binary
    if (!command.contains("merge-and-cleanup"))
      return Result.allow();

    TrustLevel trust;
    try
    {
      trust = getTrustLevel();
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }

    if (trust == TrustLevel.HIGH)
      return Result.allow();

    String sessionId = scope.getSessionId();
    if (sessionId.isBlank())
    {
      return Result.block("""
        FAIL: Cannot verify user approval - session ID not available.

        Trust level requires explicit approval before merge.

        BLOCKING: This merge attempt is blocked until user approval can be verified.""");
    }

    Path sessionFile = scope.getClaudeSessionsPath().resolve(sessionId + ".jsonl");

    if (!Files.exists(sessionFile))
    {
      return Result.block("""
        FAIL: Cannot verify user approval - session file not found.

        Trust level requires explicit approval before merge.

        BLOCKING: This merge attempt is blocked until user approval can be verified.""");
    }

    if (checkApprovalInSession(sessionFile))
      return Result.allow();

    return Result.block("""
      FAIL: Explicit user approval required before merge

      Invoking merge-and-cleanup directly bypasses the work-with-issue workflow.

      BLOCKING: No approval detected in session history.

      The correct merge path is:
      1. Complete Step 7 (Squash Commits by Topic): invoke cat:git-squash
      2. Complete Step 8 (Rebase onto Target Branch): invoke cat:git-rebase
      3. Complete Step 9 (Approval Gate): present AskUserQuestion to the user
      4. After user selects "Approve and merge", invoke merge via Task tool (subagent_type: cat:work-merge)
         or Skill tool (skill: cat:work-merge)

      Do NOT invoke merge-and-cleanup directly via Bash - this bypasses the approval gate.

      Fail-fast principle: Unknown consent = No consent = STOP""");
  }

  /**
   * Get the trust level from config.json.
   *
   * @return the trust level
   * @throws IOException if the config file cannot be read or contains invalid JSON
   */
  private TrustLevel getTrustLevel() throws IOException
  {
    Config config = Config.load(scope.getJsonMapper(), scope.getProjectPath());
    return config.getTrust();
  }

  /**
   * Check if explicit approval is found in the session file.
   * <p>
   * Approval is detected only from the AskUserQuestion wizard flow. Plain chat messages such as
   * "approve and merge" are intentionally ignored.
   *
   * @param sessionFile the session JSONL file
   * @return true if approval found
   */
  private boolean checkApprovalInSession(Path sessionFile)
  {
    try
    {
      List<String> recentLines = SessionFileUtils.getRecentLines(sessionFile, RECENT_LINES_TO_SCAN);
      return StructuredMergeApproval.isPresent(scope.getJsonMapper(), recentLines);
    }
    catch (IOException _)
    {
      // Cannot read session file
    }
    return false;
  }
}
