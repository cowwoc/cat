---
description: Merge phase for /cat:work - squashes commits, merges to main, cleans up
user-invocable: false
---

# Work Phase: Merge

Subagent skill for the merge phase of `/cat:work`. Handles commit squashing, branch merging,
worktree cleanup, and state updates.

## Input

The main agent provides:

```json
{
  "session_id": "uuid",
  "task_id": "2.1-task-name",
  "task_path": "/workspace/.claude/cat/issues/v2/v2.1/task-name",
  "worktree_path": "/workspace/.worktrees/2.1-task-name",
  "branch": "2.1-task-name",
  "base_branch": "v2.1",
  "commits": [
    {"hash": "abc123", "message": "feature: add parser", "type": "feature"},
    {"hash": "def456", "message": "test: add parser tests", "type": "test"}
  ],
  "auto_remove_worktrees": true
}
```

## Output Contract

Return JSON on success:

```json
{
  "status": "MERGED|CONFLICT|ERROR",
  "merge_commit": "abc123def",
  "squashed_commits": [
    {"type": "feature", "message": "feature: add parser with tests", "hash": "new123"}
  ],
  "branch_deleted": true,
  "worktree_removed": true,
  "state_updated": true,
  "changelog_updated": true,
  "lock_released": true
}
```

Return JSON on conflict:

```json
{
  "status": "CONFLICT",
  "conflicting_files": ["src/Parser.java", "src/Lexer.java"],
  "message": "Merge conflict - manual resolution required",
  "resolution_options": ["accept_ours", "accept_theirs", "manual"]
}
```

## Process

### Step 1: Squash Commits by Type

Group commits into categories:
- Implementation: feature, bugfix, refactor, test, docs
- Infrastructure: config

Squash each category into a single commit:

```bash
# Interactive rebase to squash
git rebase -i ${BASE_BRANCH}

# Target: 1-2 commits max
# - Implementation commit (all feature/bugfix/test work)
# - Config commit (optional, if config changes exist)
```

### Step 2: Update STATE.md

Before merge, ensure STATE.md is updated in the implementation commit:

```yaml
- **Status:** completed
- **Progress:** 100%
- **Completed:** {date}
- **Resolution:** implemented
```

### Step 3: Rebase and Merge to Base Branch (M393)

**CRITICAL: Rebase onto current base branch BEFORE merging to ensure linear history.**

The base branch may have advanced while work was in progress. Without rebasing first,
`--ff-only` will fail and we'd need a merge commit (non-linear history).

```bash
# Return to main workspace
cd /workspace

# Fetch latest base branch state
git fetch origin ${BASE_BRANCH} 2>/dev/null || true

# Rebase task branch onto current base (from main workspace, not worktree)
git rebase ${BASE_BRANCH} ${BRANCH}

# Checkout base branch
git checkout ${BASE_BRANCH}

# Merge task branch with linear history (now guaranteed to fast-forward)
git merge --ff-only ${BRANCH}
```

**If rebase has conflicts:** Return CONFLICT status. Do NOT fall back to merge commit.

### Step 4: Handle Rebase Conflicts (if any)

If rebase fails with conflicts:
1. Count conflicting files
2. If > 3 files: Return CONFLICT, require manual intervention
3. If <= 3 files: Attempt resolution (prefer task branch changes), then `git rebase --continue`

**NEVER fall back to merge commit.** Linear history is mandatory.

### Step 5: Cleanup Worktree

**CRITICAL (M324): Change to main workspace BEFORE removing worktree.**

```bash
cd /workspace
git worktree remove ${WORKTREE_PATH} --force
```

### Step 6: Delete Branch

```bash
git branch -d ${BRANCH}
```

### Step 7: Release Lock

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" release "${CLAUDE_PROJECT_DIR}" "${TASK_ID}" "${SESSION_ID}"
```

### Step 8: Update Changelog

If minor version is now complete, update CHANGELOG.md.

### Step 9: Return Result

Output the JSON result with all cleanup status.

## Fail-Fast Conditions

- Merge conflict with > 3 files: Return CONFLICT
- Worktree removal fails: Log warning, continue
- Lock release fails: Log warning, continue

## Context Loaded

This skill loads:
- merge-and-cleanup.md (merge workflow)
- commit-types.md (commit format rules)
- templates/changelog.md (changelog format)

Main agent does NOT need to load these - subagent handles internally.
