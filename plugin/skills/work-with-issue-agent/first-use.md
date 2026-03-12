<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work With Issue: Phase Orchestrator

Thin orchestrator for `/cat:work`. Delegates each work phase to its dedicated phase skill in sequence.
Each phase skill loads only its own content, reducing per-phase context load.

**Architecture:** This skill is invoked by `/cat:work` after issue discovery (Phase 1). The main agent
delegates each phase to a dedicated skill:
- Implement: `cat:work-implement-agent` (banners, lock verify, subagent delegation)
- Confirm: `cat:work-confirm-agent` (verify-implementation, fix iteration)
- Review: `cat:work-review-agent` (stakeholder review, deferred concern wizard)
- Merge: `cat:work-merge-agent` (squash, rebase, approval gate, merge execution)

## Issue Lifecycle States

An issue passes through three distinct states. Understanding these states prevents misidentifying whether an issue has
been merged.

| State | Description |
|-------|-------------|
| **Implementation running** | Confirm/review/merge phases are active. Worktree exists, lock held. |
| **Merge complete** | Merge-and-cleanup tool ran. Squashed commit on `TARGET_BRANCH`. Worktree may still exist briefly. |
| **Issue closed** | Worktree removed, lock released, branch deleted. |

**WARNING:** `STATE.md status: closed` means **implementation is finished** (State 1 done), NOT that the issue was
merged (State 2/3). Do NOT infer "merged and cleaned up" from STATE.md alone. To determine whether the issue was
merged, BOTH of the following must be true: (1) the issue branch no longer exists, AND (2) `TARGET_BRANCH` contains
the squashed commit. A missing branch alone is not sufficient — verify both conditions before concluding the issue
was merged. If either check cannot be confirmed, treat the issue as not yet merged and run the full merge workflow.

## MANDATORY STEPS

The following steps are **mandatory** and must not be skipped without explicit user permission. Mandatory steps do not
require user permission to execute — they are pre-approved as part of the `/cat:work` workflow. Steps marked **BLOCKING**
are additionally enforced by hooks or explicit STOP instructions that block progress mechanically if skipped.

- **Step 5: Review Phase (Stakeholder Review)** — always invoke `cat:stakeholder-review-agent` except for config-driven
  exceptions (VERIFY=none or TRUST=high); do not skip based on perceived simplicity or short feedback cycles
- **Step 7: Squash Commits by Topic Before Review** — always squash before the approval gate; do not proceed to
  Step 8 without completing this step
- **Step 8: Rebase onto Target Branch Before Approval Gate** — always rebase the squashed branch onto the current tip
  of the target branch before the approval gate; do not proceed to Step 9 without completing this step
- **Step 9 (sub-step): Skill-Builder Review** — always invoke `cat:skill-builder` for modified skill or
  command files before presenting the approval gate

## Arguments and Configuration

The main `/cat:work` skill invokes this with positional space-separated arguments:
`<issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <estimated_tokens> <trust> <verify>`

```bash
read ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH ESTIMATED_TOKENS TRUST VERIFY <<< "$ARGUMENTS"
```

## Path Validation

Before invoking any phase skill, validate that `ISSUE_PATH` is well-formed.

Check that `ISSUE_PATH` contains the substring `/.claude/`. If it does not, STOP immediately and display:

```
ERROR: issue_path does not contain '/.claude/' — possible path typo.
Expected: a path containing /.cat/issues/
Actual:   <value of ISSUE_PATH>
Did you mean: <ISSUE_PATH with any segment that looks like '.claire' or similar replaced by '.claude'>?
STOP. Fix the issue_path before re-invoking /cat:work.
```

Do not proceed to Phase 1 until this check passes.

## Phase 1: Implement

Invoke the implement phase skill:

```
Skill tool:
  skill: "cat:work-implement-agent"
  args: "${CAT_AGENT_ID} ${ISSUE_ID} ${ISSUE_PATH} ${WORKTREE_PATH} ${BRANCH} ${TARGET_BRANCH} ${ESTIMATED_TOKENS} ${TRUST} ${VERIFY}"
```

Capture the result. Assign variables from the returned JSON:

```
EXECUTION_COMMITS_JSON = commits array from implement result
FILES_CHANGED = files_changed integer from implement result
TOKENS_USED = tokens_used integer from implement result (only implement tracks this)
```

If the implement phase returns FAILED or BLOCKED, return that status immediately.

## Phase 2: Confirm

Invoke the confirm phase skill:

```
Skill tool:
  skill: "cat:work-confirm-agent"
  args: "${CAT_AGENT_ID} ${ISSUE_ID} ${ISSUE_PATH} ${WORKTREE_PATH} ${BRANCH} ${TARGET_BRANCH} ${EXECUTION_COMMITS_JSON} ${FILES_CHANGED} ${TRUST} ${VERIFY}"
```

Where `EXECUTION_COMMITS_JSON` is the JSON array of commits from the implement phase result,
and `FILES_CHANGED` is the integer count from the implement phase result.

If the confirm phase returns FAILED or BLOCKED, return that status immediately.

## Phase 3: Review

Build the `ALL_COMMITS_COMPACT` string in format `hash:type,hash:type` from the commits array.

Invoke the review phase skill:

```
Skill tool:
  skill: "cat:work-review-agent"
  args: "${CAT_AGENT_ID} ${ISSUE_ID} ${ISSUE_PATH} ${WORKTREE_PATH} ${BRANCH} ${TARGET_BRANCH} ${ALL_COMMITS_COMPACT} ${TRUST} ${VERIFY}"
```

Capture the result including `all_concerns`, `fixed_concerns`, `deferred_concerns`, and
updated `all_commits_compact` (review may add fix commits).

If the review phase added fix commits, append them to `EXECUTION_COMMITS_JSON` to build the
complete `COMMITS_JSON` array containing all commits from all phases.

If the review phase returns FAILED or BLOCKED, return that status immediately.

## Phase 4: Merge

Invoke the merge phase skill with the accumulated commits JSON:

```
Skill tool:
  skill: "cat:work-merge-agent"
  args: "${CAT_AGENT_ID} ${ISSUE_ID} ${ISSUE_PATH} ${WORKTREE_PATH} ${BRANCH} ${TARGET_BRANCH} ${COMMITS_JSON} ${TRUST} ${VERIFY}"
```

Where `COMMITS_JSON` is the full JSON array of all commits accumulated across implement, confirm,
and review phases.

Capture the final result. The merge skill handles the approval gate and returns when the user
approves merge, requests changes, or aborts.

## Return Result

Return the final status to the `/cat:work` skill:

```json
{
  "status": "SUCCESS|ABORTED|CHANGES_REQUESTED|FAILED",
  "issue_id": "${ISSUE_ID}",
  "commits": [...],
  "files_changed": N,
  "tokens_used": N,  // from implement phase only
  "merged": true
}
```

## Error Handling

If any phase fails:

1. Capture error message and phase name
2. Restore working directory: `cd "${CLAUDE_PROJECT_DIR}"`
3. Attempt lock release: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${ISSUE_ID}" "$CLAUDE_SESSION_ID"`
4. Return FAILED status with actual error details

```json
{
  "status": "FAILED",
  "phase": "implement|confirm|review|merge",
  "message": "actual error message",
  "issue_id": "${ISSUE_ID}"
}
```

**NEVER fabricate failure responses.** You must actually attempt the work before reporting failure.
