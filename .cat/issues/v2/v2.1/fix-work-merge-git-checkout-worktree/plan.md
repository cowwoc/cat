# Plan

## Goal

Fix the merge step in the work-merge-agent so it uses `git -C "${CLAUDE_PROJECT_DIR}" merge --ff-only`
instead of relying on `cd /workspace` before running `git merge --ff-only`. The root cause is that
`plugin/concepts/merge-and-cleanup.md` Section 7 shows a `git merge --ff-only` pattern
which fails when the agent runs `git checkout <target-branch>` inside a worktree (the target branch is
already checked out in the main workspace, causing a fatal error that is silently ignored, and the
subsequent merge lands on the wrong branch).

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/concepts/merge-and-cleanup.md` Section 7 no longer shows `cd /workspace` before `git merge --ff-only`
- [ ] Section 7 uses `git -C "${CLAUDE_PROJECT_DIR}" merge --ff-only {source-branch}` as the correct pattern
- [ ] No hardcoded `/workspace` path references remain in the merge workflow sections of the conceptual doc
- [ ] E2E verification: review the updated conceptual doc and confirm it no longer guides an agent toward
  `git checkout <target-branch>` before merging

## Research Findings

The problematic code is in `plugin/concepts/merge-and-cleanup.md` Section 7, subsection
"Merging When Target Branch Is Checked Out in Main Workspace":

```bash
# Work from main workspace directly (target branch already checked out)
cd /workspace

# Verify you're on the target branch
git branch --show-current  # Should show target branch (e.g., v2.1)

# Merge source branch using fast-forward only (enforces linear history)
git merge --ff-only {source-branch}
```

Problems:
1. `cd /workspace` hardcodes the workspace path (violates worktree isolation)
2. `git checkout <target-branch>` inside a worktree fails fatally because the target branch is already checked
   out in the main workspace; the error is silently ignored, so the subsequent merge runs on the wrong branch
3. The `git branch --show-current` verification check is redundant when using `git -C` targeting the main workspace

Correct pattern: Use `git -C "${CLAUDE_PROJECT_DIR}"` to target the main workspace directly,
without needing a `cd` or checkout.

Section 8 also contains `cd /workspace` references that should be updated to use
`git -C "${CLAUDE_PROJECT_DIR}"` or `${CLAUDE_PROJECT_DIR}`.

## Jobs

### Job 1

- In `plugin/concepts/merge-and-cleanup.md`, replace the "Merging When Target Branch Is Checked Out in Main
  Workspace" code block in Section 7 with the correct `git -C "${CLAUDE_PROJECT_DIR}"` pattern:
  ```bash
  # Merge from main workspace directly using -C flag (no cd or checkout needed)
  git -C "${CLAUDE_PROJECT_DIR}" merge --ff-only {source-branch}
  # If fast-forward not possible, rebase the source branch first:
  #   cd ${WORKTREE_PATH} && git rebase {target-branch}, then retry
  ```
  Remove the `cd /workspace`, `git branch --show-current`, and old `git merge --ff-only` lines from that subsection.
- In Section 8 (Worktree Cleanup), replace every occurrence of `cd /workspace` with
  `cd "${CLAUDE_PROJECT_DIR}"` to eliminate hardcoded paths. The surrounding warning text ("change directory
  BEFORE removing the worktree") must be preserved unchanged; only the `cd /workspace` token changes.
- Verify no remaining `cd /workspace` or hardcoded `/workspace` references exist in any merge workflow section
  (Sections 1-11) of `plugin/concepts/merge-and-cleanup.md`.
- Commit: `config: fix merge pattern to use git -C instead of cd /workspace`
  (commit type is `config:` per CLAUDE.md for `plugin/concepts/` files)
- In the same commit, update `.cat/issues/v2/v2.1/fix-work-merge-git-checkout-worktree/index.json`:
  set `"status": "closed"` and `"progress": "100%"`.
