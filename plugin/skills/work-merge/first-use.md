<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Phase: Merge

Subagent skill for the merge phase of `/cat:work`. Handles commit squashing, branch merging,
worktree cleanup, and state updates.

## Input

The main agent provides:

```json
{
  "session_id": "uuid",
  "issue_id": "2.1-issue-name",
  "issue_path": "/workspace/.claude/cat/issues/v2/v2.1/issue-name",
  "worktree_path": "/workspace/.claude/cat/worktrees/2.1-issue-name",
  "branch": "2.1-issue-name",
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

Squash each category into a single commit by calling `git-squash-quick.sh` directly:

**NEVER use `git rebase -i`** (requires interactive input) or manual `git reset --soft` (captures stale working
directory state). Always use `git-squash-quick.sh` which uses `commit-tree` to create commits from committed tree
objects.

Derive the commit message from the COMMITS input JSON. The merge subagent is responsible for
constructing a valid message before calling the script:
- Use the first implementation commit's message as the base (it already has the correct type prefix)
- If multiple implementation commits exist, append a summary of additional work after the base message
  (e.g., first commit "feature: add parser" + test commit → "feature: add parser with tests")
- The message MUST match the format `type: description` where type is one of: feature, bugfix,
  refactor, test, performance, config, planning, docs. The description must start with a non-whitespace
  character. The script rejects invalid messages.

```bash
# Example: Extract commit message from COMMITS JSON
PRIMARY_MESSAGE=$(echo "$COMMITS" | python3 -c "import sys, json; commits = json.load(sys.stdin); print(commits[0]['message'])")

# Squash implementation commits
"${CLAUDE_PLUGIN_ROOT}/scripts/git-squash-quick.sh" "${BASE_BRANCH}" "$PRIMARY_MESSAGE" "${WORKTREE_PATH}"

# Target: 1-2 commits max
# - Implementation commit (all feature/bugfix/test work)
# - Config commit (optional, if config changes exist)
```

**Handle script result:**

The script validates the commit message format before any git operations. If validation fails, it
exits with status 1 and prints a plain text error to stderr (not JSON). Ensure your commit message
follows the format `type: description` before calling the script.

If validation passes, the script returns JSON to stdout:

| Status | Meaning | Agent Action |
|--------|---------|--------------|
| OK | Squash succeeded | Continue to Step 2 |
| REBASE_CONFLICT | Conflict during pre-squash rebase | Return CONFLICT status with conflicting files from JSON |
| VERIFY_FAILED | Content changed during squash | Restore from backup branch in JSON, report ERROR |
| ERROR | Rebase or other failure | Report error message from JSON, do not proceed |

If work-with-issue already squashed commits in Step 5, skip this step entirely.

### Step 2: Update STATE.md

Before merge, ensure STATE.md is updated in the implementation commit:

```yaml
- **Status:** closed
- **Progress:** 100%
- **Completed:** {date}
- **Resolution:** implemented
```

### Step 3: Rebase task branch onto base

Rebase the task branch onto the base branch:

```bash
cd ${WORKTREE_PATH}

# Fetch latest base branch state
git fetch origin ${BASE_BRANCH} 2>/dev/null || true

# Rebase onto current base
git rebase ${BASE_BRANCH}
```

**Note:** Before Step 5 (worktree removal), ensure your shell is NOT inside the worktree directory.

**If rebase has conflicts:** Return CONFLICT status. Do NOT fall back to merge commit.

### Step 4: Handle Rebase Conflicts (if any)

If rebase fails with conflicts:
1. Count conflicting files
2. If > 3 files: Return CONFLICT, require manual intervention
3. If <= 3 files: Attempt resolution (prefer task branch changes), then `git rebase --continue`

**NEVER fall back to merge commit.** Linear history is mandatory.

### Step 5: Remove Worktree, Merge, and Cleanup

```bash
# Exit worktree before removing it
cd /workspace
git worktree remove ${WORKTREE_PATH} --force &&
  git merge --ff-only ${BRANCH} &&
  git branch -d ${BRANCH} &&
  "${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" release "${CLAUDE_PROJECT_DIR}" "${ISSUE_ID}" "${SESSION_ID}"
```

### Step 6: Auto-Complete Decomposed Parent

After merging, check if this issue is a sub-issue of a decomposed parent. If all sibling
sub-issues are now implemented and tested, mark the parent as completed.

**LIMITATION:** This auto-completion only checks sub-issue status, NOT parent acceptance criteria.
If parent has acceptance criteria beyond sub-issues being completed, auto-closure may be premature.
When /cat:work selects the parent later, it must verify parent PLAN.md acceptance criteria per
decompose-issue § Closing Decomposed Parents.

```bash
# Find parent by checking all issues in the same version for "Decomposed Into" sections
# that reference this issue name
VERSION_DIR=$(dirname "${ISSUE_PATH}")
ISSUE_NAME=$(basename "${ISSUE_PATH}")

for parent_dir in "$VERSION_DIR"/*/; do
  parent_state="$parent_dir/STATE.md"
  [[ ! -f "$parent_state" ]] && continue

  # Check if this parent lists our issue in "Decomposed Into"
  if grep -q "^## Decomposed Into" "$parent_state" && grep -q "$ISSUE_NAME" "$parent_state"; then
    # Found our parent - check if ALL sub-issues are closed
    all_complete=true
    while IFS= read -r subissue; do
      subissue=$(echo "$subissue" | sed 's/^- //' | cut -d' ' -f1 | tr -d '()')
      [[ -z "$subissue" ]] && continue
      subissue_state="$VERSION_DIR/$subissue/STATE.md"
      if [[ -f "$subissue_state" ]]; then
        st=$(grep -oP '(?<=\*\*Status:\*\* ).*' "$subissue_state" | head -1 | tr -d ' ')
        if [[ "$st" != "closed" ]]; then
          all_complete=false
          break
        fi
      else
        all_complete=false
        break
      fi
    done < <(sed -n '/^## Decomposed Into/,/^##/p' "$parent_state" | grep -E '^\- ')

    if [[ "$all_complete" == "true" ]]; then
      # Update parent STATE.md to completed
      parent_name=$(basename "$parent_dir")
      sed -i 's/\*\*Status:\*\* .*/\*\*Status:\*\* closed/' "$parent_state"
      sed -i 's/\*\*Progress:\*\* .*/\*\*Progress:\*\* 100%/' "$parent_state"
      echo "Auto-closed decomposed parent: $parent_name"
    fi
    break  # Only one parent possible
  fi
done
```

### Step 7: Update Changelog

If minor version is now complete, update CHANGELOG.md.

### Step 8: Return Result

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
