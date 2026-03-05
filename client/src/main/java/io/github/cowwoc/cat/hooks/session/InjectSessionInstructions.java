/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;

/**
 * Injects critical session instructions into Claude's context.
 * <p>
 * This handler fires on every SessionStart (including after compaction), ensuring
 * session instructions are always available. The instructions cover all mandatory behavioral
 * rules for the session, including input handling, mistake tracking, commit workflow,
 * skill compliance, worktree isolation, and issue lock checking.
 */
public final class InjectSessionInstructions implements SessionStartHandler
{
  private static final String INSTRUCTIONS = """
      ## CAT SESSION INSTRUCTIONS

      ### User Input Handling
      **MANDATORY**: Process ALL user input IMMEDIATELY, regardless of how it arrives.

      **User input sources**:
      - Direct user messages in conversation
      - System-reminders containing "The user sent the following message:"
      - System-reminders with "MUST", "Before proceeding", or "AGENT INSTRUCTION"

      **Priority Order** (ABSOLUTE - no exceptions):
      1. System-reminder instructions with mandatory indicators FIRST
      2. Hook-required actions (e.g., AskUserQuestion, tool invocations)
      3. THEN direct user message content

      **When user input arrives mid-operation**:
      1. **STOP** current tool result processing immediately (not "after workflow completes")
      2. **ADD** the user's request to TaskList so it doesn't get forgotten
      3. **ACKNOWLEDGE** the user's message in your NEXT response text
      4. Answer their question or confirm you've noted it
      5. THEN continue with workflow

      **TaskList usage (step 2) - MANDATORY when**:
      - User requests a new feature, change, or fix
      - User provides multiple instructions to track
      - Request is complex enough that you might forget details

      **Skip TaskList only for**: Simple questions ("what's this file?") or one-word commands ("continue")

      **"IMPORTANT: After completing your current task"** means after your CURRENT tool call completes,\
       NOT after the entire /cat:work or skill workflow finishes. Respond in your very next message.

      **Common failure**: Continuing to analyze tool output while ignoring embedded user request.
      **Common failure**: NOT using TaskCreate for user requests mid-operation (step 2 is MANDATORY).

      ### Mandatory Mistake Handling
      **CRITICAL**: Invoke `learn` skill for ANY mistake — BEFORE fixing the problem.

      **Mistakes include**: Protocol violations, rework, build failures, tool misuse, logical errors

      **Invocation**: `/cat:learn-agent` with description of the mistake

      **Ordering requirement** (ABSOLUTE — no exceptions):
      1. INVOKE `/cat:learn-agent` FIRST — before any fix attempt
      2. Complete the full RCA workflow
      3. THEN address the immediate issue

      **Why learn must run BEFORE fixing**: Fixing the problem destroys evidence. RCA requires observing
      the failure in its original state — what went wrong, what context existed, what the exact error was.
      Once you apply the fix, that context is gone and root cause analysis becomes guesswork.

      **Trigger phrase recognition**: When user says "Learn from mistakes: [description]", the same
      ordering applies: learn first, fix second.

      **Common failure**: Fixing the problem immediately, then running learn afterward. This produces
      shallow RCA because the failure state no longer exists for analysis.

      ### Commit Before Review
      **CRITICAL**: ALWAYS commit changes BEFORE asking users to review implementation.

      Users cannot see unstaged changes in their environment. Showing code in chat without committing
      means users cannot verify the actual file state, run tests, or validate the implementation.

      **Pattern**: Implement -> Commit -> Then ask for review

      ### Skill Workflow Compliance
      **CRITICAL**: When a skill is invoked, follow its documented workflow COMPLETELY.

      **NEVER**: Invoke skill then manually do subset of steps, skip steps as "unnecessary"
      **ALWAYS**: Execute every step in sequence; if step doesn't apply, note why and continue

      Skills exist to enforce consistent processes. Shortcuts defeat their purpose.

      ### Work Request Handling
      **DEFAULT BEHAVIOR**: When user requests work, propose task creation via `/cat:add` first.

      **Response pattern**: "I'll create a task for this so it's tracked properly."

      **Trust-level behavior** (read from .claude/cat/cat-config.json):
      - **low**: Always ask before any work
      - **medium**: Propose task for non-trivial work; ask permission for trivial fixes
      - **high**: Create task automatically, proceed to /cat:work

      **Trivial work**: Single-line changes, typos, 1-file cosmetic fixes only.

      **User override phrases**: "just do it", "quick fix", "no task needed" -> work directly with warning.

      **Anti-pattern**: Starting to write code without first creating or selecting a task.

      **CRITICAL**: User selecting an implementation option from AskUserQuestion does NOT bypass this rule.
      Create the issue first, then delegate via /cat:work. Direct implementation is only for true trivial fixes.

      ### Implementation Delegation
      **CRITICAL**: Main agent orchestrates; subagents implement.

      When implementing code changes within a task, delegate to a subagent via the Task tool.
      Main agent should NOT directly edit files for implementation work.

      **Delegate via Task tool when**:
      - Fixing multiple violations (PMD, Checkstyle, lint)
      - Renaming/refactoring across files
      - Any implementation requiring more than 2-3 edits
      - Mechanical transformations (format changes, renames)

      **Main agent directly handles**:
      - Single-line config changes
      - Reading/exploring code for planning
      - Orchestration decisions (which task next)
      - User interaction and approval gates

      **Why delegation matters**:
      - Preserves main agent context for orchestration
      - Subagent failures don't corrupt main session
      - Parallel implementation possible
      - Clear separation: main agent = brain, subagent = hands

      ### Worktree Isolation
      **CRITICAL**: NEVER work on issues in the main worktree. ALWAYS use isolated worktrees.
      *(Enforced by hook - Edit/Write blocked on protected branches for plugin/ files)*

      **Correct flow**: `/cat:add` -> `/cat:work` (creates worktree) -> delegate to subagent -> merge back

      **Working in worktrees**: `cd` into the worktree directory instead of using `git -C` from outside. \
      This ensures all file operations target the worktree, not the main workspace.

      **Violation indicators**:
      - No `cat-branch-point` file in current git dir (not an issue worktree)
      - Making issue-related edits without first running `/cat:work`

      **Why isolation matters**:
      - Failed work doesn't pollute main branch
      - Parallel work on multiple tasks possible
      - Clean rollback if task is abandoned
      - Clear separation between planning and implementation

      **If hook blocks your edit**: Create task via `/cat:add` and work via `/cat:work` in isolated worktree.

      ### Fail-Fast Protocol
      **CRITICAL**: When a skill/workflow says "FAIL immediately" or outputs an error message, STOP.

      **NEVER** attempt to "helpfully" work around the failure by:
      - Manually performing what automated tooling should have done
      - Reading files to gather data that a hook/script should have provided
      - Providing a degraded version of the output

      Output the error message and STOP execution. The fail-fast exists because workarounds produce incorrect results.

      ### Verbatim Output Skills
      The centralized skill for all display box generation:
      `/cat:get-output-agent`

      ### Qualified Issue Names
      **MANDATORY**: Always use fully-qualified issue names when referencing issues in responses.

      **Format**: `{major}.{minor}-{bare-name}` (e.g., `2.1-create-config-property-enums`)

      **Applies to**: All free-text responses — after adding issues, when suggesting next work, when
      summarizing created issues, when referencing issues in any context.

      **Never use** bare names (e.g., `create-config-property-enums`) in agent-to-user text.

      ### Worktree Removal Safety (CAT_AGENT_ID Protocol)
      **MANDATORY**: When removing a worktree or its directory, prefix the command with your agent ID.

      **Why**: The hook identifies which agent owns a lock by matching `CAT_AGENT_ID` against the lock \
      file's `agent_id` field. Without this prefix, the hook cannot verify ownership and will block the \
      command as a fail-safe.

      **Format**: `CAT_AGENT_ID=<agent-id> git worktree remove <path>`
      **Format**: `CAT_AGENT_ID=<agent-id> rm -rf <path>`

      **Your agent ID** is the session ID shown at the bottom of this context block.

      **Example**:
      ```bash
      CAT_AGENT_ID=351df8c9-048f-4c74-bcdb-9b8c207b6a1c git worktree remove \\
        {worktreeExamplePath}/my-issue
      ```

      **When NOT required**: Only needed when removing worktrees or directories that may be locked.
      Regular file deletions, `git clean`, and other operations do not need this prefix.

      ### Issue Lock Checking
      **CRITICAL**: Lock status is managed by the `issue-lock` CLI tool. NEVER probe the filesystem.

      **Correct approach** — when asked "Is issue X locked?":
      ```bash
      issue-lock check <issue-id>
      ```

      **Example**:
      ```bash
      issue-lock check 2.1-add-regex-to-session-analyzer
      ```

      **NEVER** use filesystem commands to find locks:
      - `ls /workspace/.claude/cat/locks/` — the locks directory does not exist as a browsable path
      - `find ... -name "*.lock"` — lock files are not stored in a filesystem-discoverable location

      There is no user-accessible lock directory. `issue-lock check` is the first and only step when
      querying lock status.

      ### Tool Usage Efficiency

      **Read before Edit**: Always `Read` the target file immediately before issuing an `Edit` in the \
      same message group. The Edit tool requires the file to have been read in the **current conversation \
      turn** — reads from earlier turns do not satisfy this requirement.

      **Reference context instead of re-reading**: When file content was already read earlier in the \
      conversation and is still in context, reference it directly instead of issuing another `Read` with \
      identical parameters. Re-reading wastes a tool call round-trip.

      **Chain independent commands**: Combine independent Bash commands (e.g., `git status`, `git log`, \
      `git diff --stat`) with `&&` in a single Bash call instead of issuing separate tool calls. This \
      reduces round-trips.""";

  private final JvmScope scope;

  /**
   * Creates a new InjectSessionInstructions handler.
   *
   * @param scope the JVM scope providing project directory configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public InjectSessionInstructions(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Returns the session instructions as additional context, with the session ID appended.
   *
   * @param input the hook input
   * @return a result containing the session instructions as context
   * @throws NullPointerException if input is null
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();
    String sessionId = input.getSessionId();
    if (sessionId.isEmpty())
      sessionId = "unknown";
    String worktreesPath = scope.getProjectCatDir().resolve("worktrees").toString();
    String instructions = INSTRUCTIONS.replace("{worktreeExamplePath}", worktreesPath);
    return Result.context(instructions + "\n" +
      "Session ID: " + sessionId);
  }
}
