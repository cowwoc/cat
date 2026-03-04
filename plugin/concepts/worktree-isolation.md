<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Worktree Isolation and Path Verification

## Overview

CAT uses Git worktrees to provide isolated working directories for each issue. Worktree isolation protects the main
workspace from accidental modifications and allows concurrent issue work. This document defines verification requirements
to ensure agents always operate in the correct context.

## Mandatory Verification Checklist

**Prerequisite:** All steps below assume `WORKTREE_PATH`, `ISSUE_ID`, and `TARGET_BRANCH` have been received from
the `work-prepare` phase output. See [Worktree Context Variables](#worktree-context-variables) for authoritative
sources for each value.

Before using any path or config value derived from worktree context, agents **MUST** verify:

### Step 1: Verify Current Worktree Location

Immediately after entering a worktree, verify you are in the correct directory:

```bash
cd ${WORKTREE_PATH} && pwd
```

**Required output:** The shell is inside the issue worktree directory (e.g.,
`/home/node/.config/claude/projects/-workspace/cat/worktrees/2.1-issue-name`).

**Fail-fast rule:** If `pwd` shows the main workspace or any other directory, abort immediately with an error message
identifying the current location and expected worktree path.

### Step 2: Verify Current Branch

Check that you are on the correct issue branch:

```bash
git branch --show-current
```

**Required output:** The branch name matches the expected issue branch (typically the worktree directory name converted
to branch format, e.g., `2.1-issue-name`).

**Fail-fast rule:** If the branch is `main`, `master`, or any branch other than the expected issue branch, abort
immediately with an error message showing the current branch and expected branch.

**Rationale:** Working on the wrong branch will cause commits to go to the wrong place, corrupting both branches.

### Step 3: Read Effective Configuration Before Using Config Values

Before reading or relying on any behavioral configuration value (trust level, verify level, effort, etc.), **MUST**
read the effective configuration using the `get-config-output` tool:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output" effective
```

This returns JSON with all defaults applied (missing entries filled in automatically):
```json
{
  "trust": "medium",
  "verify": "all",
  "effort": "high",
  "patience": "medium",
  "displayWidth": 50,
  "fileWidth": 120,
  "completionWorkflow": "merge",
  "minSeverity": "low",
  "license": ""
}
```

**Fail-fast rule:** If the tool exits with a non-zero status, abort immediately with the error output. The tool itself
validates that `cat-config.json` exists and contains valid JSON.

**Note:** The target branch (`target_branch`) and issue ID are passed as explicit parameters to skill invocations,
not stored in `cat-config.json`. Do not attempt to read target branch names from this file.

### Step 4: Verify Base Branch Before Merge Operations

The `target_branch` is provided as a parameter when invoking merge skills. It is **not** stored in `cat-config.json`.

Before any merge or rebase operation:

1. **Verify the `target_branch` parameter was provided and is non-empty:**
   ```bash
   if [[ -z "${TARGET_BRANCH:-}" ]]; then
     echo "ERROR: target_branch parameter is missing" >&2
     echo "Ensure this value was passed through from /cat:work preparation output" >&2
     exit 1
   fi
   ```

2. **Verify the target branch exists** in the local repository:
   ```bash
   if ! git rev-parse "$TARGET_BRANCH" >/dev/null 2>&1; then
     echo "ERROR: Target branch not found: $TARGET_BRANCH" >&2
     exit 1
   fi
   ```

3. **Do not use hardcoded or guessed branch names.** Even if you "know" the main branch is `main`, use the value
   passed through from the preparation phase instead. Projects may use different naming conventions
   (e.g., `main`, `master`, `v2.1`).

**Fail-fast rule:** If the target branch parameter is empty or does not exist locally, fail immediately. Do not
attempt to fetch or guess alternatives.

**Rationale:** Wrong target branch = wrong merge target = silent data corruption. Example: Merging to `main` when the
project's target branch is `v1.10` silently breaks the version's work.

### Step 5: Verify Relative vs. Absolute Paths

When working in a worktree:

- **Use relative paths** for worktree-local operations (e.g., `plugin/skills/`, `.claude/cat/cat-config.json`)
- **Never use absolute paths to `/workspace/`** — these bypass worktree isolation and modify the main workspace instead

**Example — WRONG:**
```bash
# This modifies the main workspace, not the issue worktree
cat /workspace/plugin/skills/skill.md
cp /workspace/file.txt /workspace/file-backup.txt
```

**Example — CORRECT:**
```bash
# This uses the worktree's copy
cat plugin/skills/skill.md
cp file.txt file-backup.txt
```

**Fail-fast rule:** If you are about to use an absolute path to `/workspace/`, stop and reconsider. Use a relative
path instead. If an absolute path is truly necessary (e.g., accessing files outside the worktree), document why in
a comment and include clear error handling.

## Configuration Read Ordering

**MANDATORY:** Always read configuration **BEFORE** using its values.

### Correct Pattern

```bash
# Step 1: Verify location and branch
cd "${WORKTREE_PATH}" || exit 1
CURRENT_BRANCH=$(git branch --show-current) || exit 1

# Step 2: Read behavioral config values using the effective config tool
CONFIG=$("${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output" effective)
if [[ $? -ne 0 ]]; then
  echo "ERROR: Failed to read effective config: $CONFIG" >&2
  exit 1
fi
TRUST=$(echo "$CONFIG" | grep -o '"trust"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | sed 's/.*"trust"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')

# Step 3: Use target branch from parameter (not config file)
if [[ -z "${TARGET_BRANCH:-}" ]]; then
  echo "ERROR: target_branch parameter is missing" >&2
  exit 1
fi
git rebase "$TARGET_BRANCH" || exit 1
```

### Incorrect Pattern — DO NOT USE

```bash
# WRONG: Using hardcoded or assumed values
TARGET_BRANCH="main"  # Assumption, not from parameters
git rebase "$TARGET_BRANCH"

# WRONG: Using jq (not available in the plugin runtime environment)
TRUST=$(jq -r '.trust' .claude/cat/cat-config.json)

# WRONG: Manually parsing cat-config.json with grep/sed (fragile, no defaults)
TRUST=$(grep -o '"trust"[[:space:]]*:[[:space:]]*"[^"]*"' .claude/cat/cat-config.json \
  | sed 's/.*"trust"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')

# WRONG: Skipping config read and using a stale in-memory value
git merge "$TARGET_BRANCH"  # Where did TARGET_BRANCH come from?
```

## Worktree Context Variables

When a worktree is created, the following context is established:

| Variable | Source | Example | Usage |
|----------|--------|---------|-------|
| `WORKTREE_PATH` | `/cat:work` preparation output | `/home/node/.config/claude/.../cat/worktrees/2.1-issue` | Directory to `cd` into |
| `ISSUE_ID` | `/cat:work` preparation output | `2.1-issue-name` | Identifying the issue |
| `TARGET_BRANCH` | `/cat:work` preparation output | `v2.1` | Merge target |
| `TRUST` | `cat-config.json` field `"trust"` | `medium` | Approval gate behavior |
| `VERIFY` | `cat-config.json` field `"verify"` | `all` | Review level |

**Do not assume any of these values.** Always read them from authoritative sources:
- Directory path: from preparation phase output passed as parameter
- Branch name: `git branch --show-current` (for verification) or from preparation phase output (for operations)
- Behavioral config: use `get-config-output effective` tool (returns JSON with defaults applied)
- Target branch: from preparation phase `target_branch` parameter (not from cat-config.json)

## Abort Semantics

When a verification step fails, the agent **MUST abort the operation immediately** and provide an error message that
includes:

1. **What was checked** — the verification step that failed
2. **What was expected** — the value or state that should exist
3. **What was found** — the actual value or state observed
4. **How to fix it** — the action to recover (e.g., "Run `/cat:work` to create the worktree")

**Example — CORRECT:**
```bash
if [[ "$(git branch --show-current)" != "2.1-issue" ]]; then
  echo "ERROR: Cannot proceed — on wrong branch" >&2
  echo "Current branch: $(git branch --show-current)" >&2
  echo "Expected branch: 2.1-issue" >&2
  echo "Fix: Run 'git checkout 2.1-issue' or use /cat:work to start fresh" >&2
  exit 1
fi
```

**Example — WRONG:**
```bash
# Wrong: Silent fallback to assumption
BRANCH="${EXPECTED_BRANCH:-main}"  # Falls back to "main" if unset
git merge "$BRANCH"                # What branch was this?
```

## Related Documentation

- [version-paths.md](version-paths.md) — Path resolution for version directories
- [git-operations.md](git-operations.md) — Git command patterns and efficiency
- [agent-architecture.md](agent-architecture.md) — Agent execution model and worktree context
