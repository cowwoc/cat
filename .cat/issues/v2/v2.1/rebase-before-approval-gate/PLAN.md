# Plan: rebase-before-approval-gate

## Goal
Add a rebase step (new Step 8) between the squash phase (Step 7) and the approval gate in
`plugin/skills/work-with-issue-agent/first-use.md`, so the squashed issue branch is always
rebased onto the current tip of the target branch before the user sees the diff. This ensures the
approval gate diff reflects the exact result of the pending merge, not a stale fork point.

## Satisfies
None

## Current Behavior
After squashing commits (Step 7), the workflow immediately presents the approval gate. If the target
branch has received new commits since the issue branch was forked, the diff shown at the approval
gate compares the issue branch against the old fork point rather than the current target branch tip.
This means the diff can include phantom changes already on the target branch, and users approve a
diff that doesn't match what the merge will actually produce.

## Target Behavior
After squash and before the approval gate, the workflow:
1. Invokes `cat:git-rebase-agent` to rebase the squashed commit onto the current target branch tip
2. If rebase conflicts occur, resolves them before proceeding (cat:git-rebase-agent provides conflict
   details and backup branches)
3. Updates `cat-branch-point` to `git rev-parse {TARGET_BRANCH}` after a successful rebase
4. Proceeds to the approval gate — the diff now accurately shows only the issue's own changes

## Alternatives Considered

### A: Merge target branch into issue branch (merge commit)
Rejected: creates a merge commit in the issue history, complicating the final squash-and-merge.
Rebase produces a clean single-commit history.

### B: Rebase at worktree creation time only (work-prepare phase)
Rejected: the target branch can advance between worktree creation and the approval gate. Only
rebasing immediately before the approval gate guarantees an up-to-date diff.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Rebase conflicts may block the workflow if the target branch has diverged
- **Mitigation:** Delegate to `cat:git-rebase-agent` which provides conflict detection, backup
  branches, and guided recovery. Conflicts must be resolved before the merge anyway.

## Files to Modify
- `plugin/skills/work-with-issue-agent/first-use.md` — insert new Step 8 (rebase step) between
  old Step 7 (squash) and old Step 8 (approval gate); renumber all subsequent steps

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Read `plugin/skills/work-with-issue-agent/first-use.md` to understand exact wording of all
  step headers and cross-references before making any edits
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`
- Insert the new Step 8 section immediately after the end of the old Step 7 section (line ~1226,
  before `## Step 8: Approval Gate`). Use this exact content:
  ```
  ## Step 8: Rebase onto Target Branch Before Approval Gate (MANDATORY)

  Before presenting the approval gate, rebase the squashed issue branch onto the current tip of
  the target branch. This ensures the diff shown at the approval gate reflects what the merge will
  actually produce.

  **Invoke `cat:git-rebase-agent`:**
  ```
  Skill("cat:git-rebase-agent", args="{SESSION_ID}")
  ```
  Pass the current worktree path and `{TARGET_BRANCH}` as the target.

  **If rebase reports CONFLICT:**
  - Examine the conflicting files reported by cat:git-rebase-agent
  - Resolve each conflict
  - Stage resolved files and continue the rebase
  - Delete the backup branch created by cat:git-rebase-agent after resolution
  - Continue to Step 9 (Approval Gate)

  **If rebase reports OK:**
  - Update `cat-branch-point` to the new base:
    ```bash
    git rev-parse {TARGET_BRANCH} > "{WORKTREE_PATH}/cat-branch-point"
    ```
  - Delete the backup branch created by cat:git-rebase-agent
  - Continue to Step 9 (Approval Gate)

  **If rebase reports ERROR:**
  - Output the error message
  - Restore from the backup branch if needed
  - STOP — do not proceed to approval gate until the error is resolved
  ```
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`
- Renumber ALL subsequent steps and ALL cross-references throughout the file:
  - Old Step 8 (Approval Gate) → Step 9
  - Old Step 9 (Merge Phase) → Step 10
  - Old Step 10 (Return Success) → Step 11
  - Update every inline reference to "Step 8", "Step 9", "Step 10" throughout the file body
  - Update the overview block at the top of the file (lines ~29–32) where Step 8 is referenced
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`
- Update STATE.md: status=closed, progress=100%
  - Files: `.cat/issues/v2/v2.1/rebase-before-approval-gate/STATE.md`

## Post-conditions
- [ ] `plugin/skills/work-with-issue-agent/first-use.md` contains a new Step 8 between the
  squash step (Step 7) and the approval gate (now Step 9)
- [ ] All step numbers in the file are sequential (Steps 1–11) with no gaps or duplicates
- [ ] Every cross-reference to old Steps 8, 9, 10 is updated to Steps 9, 10, 11 respectively
- [ ] The new step delegates to `cat:git-rebase-agent`
- [ ] The new step updates `cat-branch-point` after a successful rebase
- [ ] The new step requires conflict resolution before proceeding to the approval gate
- [ ] E2E: After squashing commits on an issue branch that lags the target branch, the approval
  gate diff shows only the issue's own changes (no phantom changes from target branch advances)
- [ ] All tests pass: `mvn -f client/pom.xml test`
