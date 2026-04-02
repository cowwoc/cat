# Plan

## Goal

Fix work-implement skill instructions to commit plan.md changes before spawning subagents. The
EnforceCommitBeforeSubagentSpawn hook blocks subagent spawning when uncommitted changes exist in the
worktree. Currently, the work-implement-agent skill updates plan.md during execution but doesn't
commit those changes before spawning implementation subagents, causing the hook to block the spawn.
The fix is to update the skill instructions to commit plan.md changes before spawning subagents,
keeping the hook in place.

## Research Findings

**The contradiction in `plugin/skills/work-implement-agent/first-use.md`:**

1. **Step 5 (line 222):** When `hasSteps=false`, invokes `cat:plan-builder-agent` which writes updated plan.md
   to `${ISSUE_PATH}/plan.md`. Sub-step 3 (line 256) says "After plan-builder-agent returns, re-read the
   updated plan.md in subsequent steps." — but does NOT instruct to commit plan.md.

2. **Commit-Before-Spawn Requirement (line 365):** Says "Before spawning ANY implementation subagent, commit
   all pending changes in the worktree." But then line 379 says "**CRITICAL: plan.md and index.json must NOT
   be committed here.**" and includes a Bash check that BLOCKs if plan.md is dirty.

3. **Result:** After plan-builder-agent updates plan.md, the worktree has uncommitted plan.md changes. The
   Commit-Before-Spawn section blocks on dirty plan.md, creating a deadlock.

**Fix approach:** Add a sub-step after plan-builder-agent returns (Step 5, sub-step 3) to commit plan.md.
Then update the Commit-Before-Spawn Requirement section to remove the prohibition on committing plan.md,
since plan.md IS expected to be dirty after Step 5 and MUST be committed before spawning. The prohibition
on committing index.json remains (index.json is updated by the subagent, not the main agent).

**Files to modify:**
- `plugin/skills/work-implement-agent/first-use.md` — the only file that needs changes

**No Java/hook changes needed:** The `EnforceCommitBeforeSubagentSpawn` hook
(`client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCommitBeforeSubagentSpawn.java`) checks
that the worktree is clean before spawning. This is correct behavior. The fix is in the skill instructions
that create the dirty state, not in the hook.

**No regression test needed for Markdown skill instructions:** The post-condition mentions a regression test,
but this is a Markdown instruction change, not a code change. The existing
`EnforceCommitBeforeSubagentSpawnTest.java` already tests the hook behavior. The fix is in skill
instructions (Markdown), which are not testable via Java unit tests. The E2E validation (running the
workflow) serves as the regression test.

## Pre-conditions

(none)

## Post-conditions

- [ ] work-implement skill instructions commit plan.md changes before spawning subagents
- [ ] No new issues introduced
- [ ] E2E: Execute work-implement workflow and confirm subagent spawning succeeds after plan.md
  modifications

## Jobs

### Job 1

Commit type: `bugfix:`

- Edit `plugin/skills/work-implement-agent/first-use.md`:

  **Change 1 — Add commit step after plan-builder-agent (around line 256):**

  Replace sub-step 3:
  ```
  3. After plan-builder-agent returns, re-read the updated plan.md in subsequent steps.
  ```

  With:
  ```
  3. After plan-builder-agent returns, commit the updated plan.md:

  ```bash
  cd "${WORKTREE_PATH}" && git add "${ISSUE_PATH}/plan.md" && git commit -m "planning: generate implementation steps for ${ISSUE_ID}"
  ```

  4. Re-read the updated plan.md in subsequent steps.
  ```

  (Renumber remaining sub-steps if any.)

  **Change 2 — Update Commit-Before-Spawn Requirement (around line 379):**

  Replace:
  ```
  **CRITICAL: plan.md and index.json must NOT be committed here.** Before committing, run:

  ```bash
  cd "${WORKTREE_PATH}" && git status --porcelain | awk '{print $NF}' | \
    while IFS= read -r filepath; do
      basename=$(basename "$filepath")
      if echo "$basename" | grep -Eqi '^plan\.md$|^state\.md$|^index\.json$'; then
        echo "BLOCKED: dirty planning file detected: $filepath"
        exit 1
      fi
    done
  ```

  If this check prints any `BLOCKED:` line, STOP immediately and return FAILED status.
  Do NOT stage, commit, or otherwise alter plan.md or index.json before spawning.

  Note: `-E` (extended regex) is required so that `|` acts as alternation, not a literal pipe
  character. Without `-E`, the pattern would only match a filename literally containing a pipe.
  ```

  With:
  ```
  **CRITICAL: index.json must NOT be committed here.** Before committing, run:

  ```bash
  cd "${WORKTREE_PATH}" && git status --porcelain | awk '{print $NF}' | \
    while IFS= read -r filepath; do
      basename=$(basename "$filepath")
      if echo "$basename" | grep -Eqi '^index\.json$'; then
        echo "BLOCKED: dirty planning file detected: $filepath"
        exit 1
      fi
    done
  ```

  If this check prints any `BLOCKED:` line, STOP immediately and return FAILED status.
  Do NOT stage, commit, or otherwise alter index.json before spawning.

  Note: `-E` (extended regex) is required so that `|` acts as alternation, not a literal pipe
  character. Without `-E`, the pattern would only match a filename literally containing a pipe.
  ```

  This removes plan.md and state.md from the blocking check. plan.md is expected to be dirty
  after Step 5 invokes plan-builder-agent, and the new sub-step 3 commits it. state.md is no
  longer used (replaced by index.json). Only index.json remains blocked because the subagent
  owns index.json updates.

  **Change 3 — Update the commit instruction for other dirty files (around line 398):**

  Replace:
  ```
  If the worktree is dirty with other files (non-plan.md, non-index.json), stage only those specific files by
  ```

  With:
  ```
  If the worktree is dirty with other files (non-index.json), stage only those specific files by
  ```

- Update `${ISSUE_PATH}/index.json`: set `"status": "closed"` and `"progress": 100`
