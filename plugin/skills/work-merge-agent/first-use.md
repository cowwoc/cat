<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Phase: Merge

Merge phase for `/cat:work`. Handles pre-merge squash and rebase, approval gate, then executes
commit squashing, branch merging, worktree cleanup, and state updates.

## MANDATORY STEPS

- **Step 7: Rebase onto Target Branch Before Approval Gate** — always rebase the branch onto the current tip
  of the target branch before the approval gate; do not proceed to Step 8 without completing this step
- **Step 8: Skill-Builder Review** — always invoke `cat:skill-builder` for modified skill or
  command files before presenting the approval gate
- **Step 9: Squash Commits by Topic Before Approval Gate** — always squash commits by topic immediately before
  presenting the approval gate; do not present the gate on an un-squashed branch. **This applies on EVERY
  presentation, including after user feedback: re-squash ALL commits before re-presenting the gate.**

## Arguments and Configuration

`<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <commits_json_path> <trust> <verify>`

```bash
read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH COMMITS_JSON_PATH TRUST VERIFY <<< "$ARGUMENTS"
PLAN_MD="${ISSUE_PATH}/PLAN.md"
```

```bash
COMMITS_JSON=$(cat "$COMMITS_JSON_PATH")
```

Return JSON: `{"status": "SUCCESS|ABORTED|CHANGES_REQUESTED|FAILED", "issue_id": "...", "commits": [...], "files_changed": N, "merged": true}`

## Issue Lifecycle States

An issue passes through three distinct states during the merge workflow. Misidentifying the state causes errors such
as refusing to squash commits on an active branch.

| State | Description | Authoritative Indicator |
|-------|-------------|-------------------------|
| **Implementation running** | Confirm/review/merge phases are active. The worktree exists and the lock is held. | Worktree present, lock file exists, branch exists |
| **Merge complete** | The merge-and-cleanup tool ran and produced a squashed commit on `TARGET_BRANCH`. The worktree may still exist briefly during cleanup. | The merge-and-cleanup tool returned `"status": "success"` in the current session |
| **Issue closed** | Worktree removed, lock released, branch deleted. | No worktree, no lock, no issue branch |

**CRITICAL:** `STATE.md status: closed` means **implementation is done**, NOT that the issue was merged. An issue can
have `STATE.md status: closed` while the worktree still exists and the merge-and-cleanup tool has not yet run. Always
verify merge status by checking the git branch state AND the merge-and-cleanup tool result — never by reading STATE.md
alone.

**Distinguishing State 1 from State 2:** Observable git indicators (worktree present, branch present, commit on
TARGET_BRANCH) are **insufficient** to distinguish State 1 from State 2 because a stale or missing lock file produces
identical observations. The only authoritative proof of State 2 is that the merge-and-cleanup tool returned
`"status": "success"` in the current session. If that result is not in the current session's context, treat the issue
as State 1 and run the full merge workflow.

## Step 7: Squash Commits by Topic Before Review (MANDATORY)

**MANDATORY: Delegate rebase, squash, and STATE.md closure verification to a squash subagent.** This step must not be
skipped — the approval gate (Step 9) checks that squash was executed and blocks proceeding if it was not.
This keeps the parent agent context lean by offloading git operations to a dedicated haiku-model subagent.

**Note:** It is expected and correct to squash commits on **the current issue's branch** (`${BRANCH}`) where
STATE.md shows `closed`. This means the implementation subagent finished its work (State 1 complete) and the merge
preparation phase is now running. The squash operation is part of transitioning from State 1 to State 2. Do NOT skip
or abort squash due to STATE.md showing `closed`. This authorization applies only to `${BRANCH}` — do NOT squash any
other branch based on this reasoning, even if that branch's STATE.md also shows `closed`.

Determine the primary commit message from the execution result (the most significant commit's message). If multiple
topics exist, use the most significant commit's message. Do NOT use generic messages like "squash commit".

**Before constructing the prompt below**, extract the primary commit message from the commits JSON.
Use the first implementation commit's `message` field. Substitute the actual message string in place of the
`PRIMARY_COMMIT_MESSAGE` value — do NOT pass the placeholder text literally.

```bash
# Example: extract the primary commit message from the commits JSON
PRIMARY_COMMIT_MESSAGE=$(echo "$COMMITS_JSON" | \
  grep -o '"message"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | \
  sed 's/"message"[[:space:]]*:[[:space:]]*"\(.*\)"/\1/')
```

Spawn the squash subagent:

```
Task tool:
  description: "Squash: rebase, squash commits, verify STATE.md"
  subagent_type: "cat:work-squash"
  model: "haiku"
  prompt: |
    Execute the squash phase for issue ${ISSUE_ID}.

    ## Configuration
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    TARGET_BRANCH: ${TARGET_BRANCH}
    PRIMARY_COMMIT_MESSAGE: ${PRIMARY_COMMIT_MESSAGE}

    Load and follow: @${CLAUDE_PLUGIN_ROOT}/agents/work-squash.md

    Return JSON per the output contract in the agent definition.
```

### Handle Squash Result

Parse the subagent result:

- **SUCCESS**: Extract `commits` array for use at the approval gate. Write a durable squash marker file to prove
  Step 7 completed (survives context compaction). The marker must contain the squashed commit hash as integrity
  proof — a marker without a valid commit hash is treated as absent:
  ```bash
  SQUASH_MARKER_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/sessions/${CLAUDE_SESSION_ID}"
  mkdir -p "${SQUASH_MARKER_DIR}"
  SQUASH_COMMIT_HASH=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
  echo "squashed:${SQUASH_COMMIT_HASH}" > "${SQUASH_MARKER_DIR}/squash-complete-${ISSUE_ID}"
  ```
  The marker file MUST be written only on the SUCCESS path after the squash subagent returns success. Do NOT
  write the marker file manually, conditionally, or from any step other than this SUCCESS handler.
  Continue to Step 8.
- **FAILED** (phase: rebase): Return FAILED status with conflict details. Do NOT proceed.
- **FAILED** (phase: squash or verify): Return FAILED status with error details. Do NOT proceed.

## Step 7: Rebase onto Target Branch Before Approval Gate (MANDATORY)

Before proceeding to the approval gate, rebase the issue branch onto the current tip of
the target branch. This ensures the diff shown at the approval gate reflects what the merge will
actually produce.

### Step 7a: Capture Old Fork Point

Record the current fork point before the rebase so the impact analysis can compare what changed:

```bash
cd "${WORKTREE_PATH}"
OLD_FORK_POINT=$(git merge-base HEAD "${TARGET_BRANCH}" 2>/dev/null)
if [[ -z "$OLD_FORK_POINT" ]]; then
  echo "ERROR: Could not determine fork point before rebase" >&2
  exit 1
fi
```

### Step 7b: Perform Rebase

**Invoke `cat:git-rebase-agent`:**
```
Skill("cat:git-rebase-agent", args="{WORKTREE_PATH} {TARGET_BRANCH}")
```

**If rebase reports CONFLICT:**
- Examine the conflicting files reported by cat:git-rebase-agent
- Record the list of conflicting files and the resolution strategy used for each (e.g., "accepted ours",
  "accepted theirs", "manual merge") in a variable `CONFLICT_RESOLUTIONS` for display at the approval gate
- Resolve each conflict. For each conflicted file, the resolution MUST incorporate changes from BOTH sides
  unless one side's changes are entirely superseded. Do NOT use accept-ours or accept-theirs as the resolution
  strategy for any file unless the other side's changes to that file are wholly redundant or inapplicable.
  Examine each conflict individually and preserve the intent of both sides
- Stage resolved files and continue the rebase
- Delete the backup branch created by cat:git-rebase-agent after resolution
- **MANDATORY:** Flag that conflicts were resolved so Step 9 displays them. Set `REBASE_HAD_CONFLICTS=true`
  and retain `CONFLICT_RESOLUTIONS` for the approval gate output
- Proceed to Step 7c (Impact Analysis)

**If rebase reports OK:**
- Delete the backup branch created by cat:git-rebase-agent
- Proceed to Step 7c (Impact Analysis)

**If rebase reports ERROR:**
- Output the error message
- Restore from the backup branch if needed
- STOP — do not proceed to approval gate until the error is resolved

### Step 7c: Post-Rebase Impact Analysis

After a successful rebase (OK or resolved CONFLICT), capture the new fork point and invoke the
impact analysis skill to determine whether upstream changes affect the current PLAN.md:

```bash
cd "${WORKTREE_PATH}"
NEW_FORK_POINT=$(git merge-base HEAD "${TARGET_BRANCH}" 2>/dev/null)
IMPACT_ANALYSIS_SKIPPED=false
if [[ -z "$NEW_FORK_POINT" ]]; then
  echo "WARNING: Could not determine fork point after rebase; skipping impact analysis" >&2
  IMPACT_ANALYSIS_SKIPPED=true
  # Continue to Step 9 without analysis — the flag is displayed at the approval gate
fi
```

If `NEW_FORK_POINT` equals `OLD_FORK_POINT`, the target branch had no new commits — skip impact
analysis and continue to Step 9.

Otherwise, compute the session analysis directory so the analysis file is written outside the worktree
and never committed, then invoke the impact analysis skill:

```bash
# Pass session dir so analysis file is written outside the worktree and never committed
SESSION_ANALYSIS_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/sessions/${CLAUDE_SESSION_ID}"
mkdir -p "${SESSION_ANALYSIS_DIR}"
```

```
IMPACT_JSON = Skill("cat:rebase-impact-agent", args="${ISSUE_PATH} ${WORKTREE_PATH} ${OLD_FORK_POINT} ${NEW_FORK_POINT} ${SESSION_ANALYSIS_DIR}")
```

Extract the severity from the JSON:

```bash
IMPACT_SEVERITY=$(echo "${IMPACT_JSON}" | grep -o '"severity"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | head -1 | sed 's/.*"severity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
```

**Route based on the returned severity:**

| `IMPACT_SEVERITY` value | Action |
|------------------------|--------|
| `NO_IMPACT` | Continue silently to Step 8 |
| `LOW` | Continue silently to Step 8 |
| `MEDIUM` | Auto-revise PLAN.md then continue to Step 8 (see below) |
| `HIGH` | Write proposal file then ask user for guidance (see below) |

**MEDIUM: Auto-Revision**

Read `EFFORT` from config and read the full analysis file before invoking the plan builder:

```bash
CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.cat/config.json"
EFFORT=$(grep '"effort"' "$CONFIG_FILE" | sed 's/.*"effort"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
if [[ -z "$EFFORT" ]]; then
  echo "ERROR: 'effort' key not found in $CONFIG_FILE" >&2
  exit 1
fi
ANALYSIS_PATH=$(echo "${IMPACT_JSON}" | grep -o '"analysis_path"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | sed 's/.*"analysis_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
```

Invoke `cat:plan-builder-agent` to mechanically revise the PLAN.md based on the upstream changes:

```
Skill("cat:plan-builder-agent", args="${CAT_AGENT_ID} ${EFFORT} revise ${ISSUE_PATH} rebase introduced upstream changes that affect PLAN.md — see ${ANALYSIS_PATH}")
```

If any implementation work was already committed on this branch before the rebase, spawn an implementation
subagent to apply the revised PLAN.md to the already-implemented code. This ensures in-progress work
remains consistent with the updated plan before continuing. The subagent receives the revised PLAN.md and
the analysis file path as context.

After the plan-builder (and any code-revision subagent) returns, continue to Step 8.

**HIGH: User Guidance Required**

Write `${ISSUE_PATH}/rebase-conflict-proposal.md` summarizing the conflict (analysis_path, summary from IMPACT_JSON,
options: revise plan / proceed / abort). Present the file path to the user and invoke `AskUserQuestion`
before continuing to Step 8.

## Step 8: Skill-Builder Review (MANDATORY — BLOCKING)

**MANDATORY:** When the issue modifies files in `plugin/skills/` or `plugin/commands/`, invoke `/cat:skill-builder`
to review each modified skill or command file before presenting the approval gate. This step must not be skipped —
do not proceed to the approval gate without completing skill-builder review for all modified skill or command files.

```bash
# Check whether any skill or command files were modified
git diff --name-only "${TARGET_BRANCH}..HEAD" | grep -E '^plugin/(skills|commands)/'
```

**If skill or command files were modified:** Invoke `/cat:skill-builder` with the path to each modified skill or
command. Review the output and address any priming issues or structural problems it identifies.

**If no skill or command files were modified:** Skip directly to Post-Skill-Builder Artifact Cleanup below.

### Post-Skill-Builder Artifact Cleanup (MANDATORY)

After skill-builder review completes (if invoked), clean up any temporary artifact files it created.
These files (findings.json, diff-validation-*.json) are intermediate outputs and must not be committed.

```bash
cd "${WORKTREE_PATH}"

# Remove skill-builder temporary artifacts
rm -f findings.json diff-validation-*.json benchmark-artifacts/ 2>/dev/null

# Remove from git index if tracked
git rm --cached findings.json diff-validation-*.json 2>/dev/null || true

# Verify cleanup
if git status --porcelain | grep -qE '(findings\.json|diff-validation)'; then
  echo "WARNING: Skill-builder artifacts remain — attempting cleanup" >&2
fi
```

**If skill-builder was NOT invoked** (no skill/command files modified): skip this cleanup step and proceed
directly to Step 9.

## Step 9: Squash Commits by Topic Before Approval Gate (MANDATORY)

Before presenting the approval gate, squash all commits by topic into one or more commits grouped by their purpose.
This ensures the diff shown at the approval gate reflects a properly organized commit structure.

Determine the primary commit message from the execution result (the most significant commit's message). Do NOT use
generic messages like "squash commit".

```bash
# Extract the primary commit message from the commits JSON
PRIMARY_COMMIT_MESSAGE=$(echo "$COMMITS_JSON" | \
  grep -o '"message"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | \
  sed 's/"message"[[:space:]]*:[[:space:]]*"\(.*\)"/\1/')
```

Invoke `cat:git-squash-agent`:
```
Skill("cat:git-squash-agent", args="${WORKTREE_PATH} ${TARGET_BRANCH} ${PRIMARY_COMMIT_MESSAGE}")
```

**After squash completes:** Extract the squashed commit hash for display at the approval gate.

```bash
FINAL_COMMIT=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
FINAL_DIFF_STAT=$(cd "${WORKTREE_PATH}" && git diff --stat ${TARGET_BRANCH}..HEAD)
```

**If squash fails:** Return FAILED status. Do NOT proceed to approval gate.

## Approval Gate (MANDATORY)

**trust == "high":** Skip approval gate — UNLESS `REBASE_HAD_CONFLICTS=true`, in which case present the conflict
resolutions to the user via AskUserQuestion before proceeding. Even at trust=high, silently merged rebase conflicts
require user acknowledgment because incorrect conflict resolution can silently drop upstream changes.

**trust == "low" or "medium":** STOP for user approval. Do NOT proceed to merge automatically.

Check for prior direct approval: scan conversation for a user message that is an **affirmative, unambiguous approval
of this specific issue's merge**. The message must:
1. Express positive intent (e.g., "approve", "go ahead", "yes, merge it") — not negated ("do not approve",
   "I don't want to merge")
2. Refer to the current issue (by name, ID, or clear contextual reference) — not a different issue
3. Be a standalone directive, not a question ("can you explain what approve means?" does not count)

If such a message is found, skip AskUserQuestion and proceed to Step 10. If uncertain whether the message
constitutes genuine approval, do NOT skip — present AskUserQuestion to obtain explicit confirmation.

**MANDATORY:** Patience matrix (Steps 5-6) MUST have already executed before the approval gate. The gate DISPLAYS
`ALL_CONCERNS`, `FIXED_CONCERNS`, `DEFERRED_CONCERNS` — it does NOT drive concern handling. Do NOT ask the user how
to handle concerns. If patience matrix hasn't run: STOP and return to Step 5 first.

### Present Changes Before Approval Gate (BLOCKING)

**MANDATORY: Render the diff and output the full change summary BEFORE invoking AskUserQuestion.**

Context compaction can occur at any point in a long session. When the conversation is compacted, the user's
visible context resets — they will only see output from the current turn onward. If AskUserQuestion is invoked
without first presenting the changes in the same turn, the user sees an approval gate with no visible context
about what they are approving.

**Required pre-gate output sequence (all mandatory, in this order):**

1. **Get diff** — invoke `cat:get-diff-agent` to display the changes:
   ```
   Skill tool:
     skill: "cat:get-diff-agent"
   ```

2. **Display commit summary** — list commits since target branch, and extract the issue goal in a single chained bash call:
   ```bash
   cd "${WORKTREE_PATH}" && git log --oneline ${TARGET_BRANCH}..HEAD && ISSUE_GOAL=$(grep -A1 "^## Goal" "${ISSUE_PATH}/PLAN.md" | tail -n1) && echo "Issue Goal: ${ISSUE_GOAL}"
   ```

3. **Display execution summary** (commits count, files changed)
4. **Display E2E testing summary** — what tests ran, what they verified, results (if skipped: state explicitly)
5. **Display impact analysis warning** (if `IMPACT_ANALYSIS_SKIPPED=true`) — display a prominent warning:
   `"WARNING: Post-rebase impact analysis was skipped because the fork point could not be determined. Upstream changes may affect PLAN.md but were not analyzed."` This ensures the user is informed before approving.
6. **Display rebase conflict resolutions** (if `REBASE_HAD_CONFLICTS=true`) — for each conflicting file:
   (a) list the resolution strategy from `CONFLICT_RESOLUTIONS`,
   (b) run `git diff ${TARGET_BRANCH}...HEAD -- <file>` to show the actual resolved diff for that file,
   so the user can independently verify the resolution matches the claimed strategy. This ensures upstream
   changes were not silently dropped during conflict resolution. Do NOT display only the self-reported
   strategy without the accompanying diff.
7. **Display ALL stakeholder concerns** regardless of severity — do NOT suppress MEDIUM or LOW

For fixed concerns: `Skill("cat:stakeholder-concern-box-agent", "${SEVERITY} ${STAKEHOLDER} ${CONCERN_DESCRIPTION} ${FILE_LOCATION}")`
For deferred: `Skill("cat:stakeholder-concern-box-agent", "${SEVERITY} ${STAKEHOLDER} ${CONCERN_DESCRIPTION} [deferred: benefit=${BENEFIT}, cost=${COST}, threshold=${THRESHOLD}] ${FILE_LOCATION}")`

8. **Recap last user change request** — scan the conversation for the most recent user message
   that requested a change, revision, or correction **to this specific issue** (e.g., "use the
   simpler approach", "also fix X", "change the test to cover Y"). Exclude unrelated requests
   (other issues, learn invocations, status queries). When uncertain whether a message qualifies,
   skip this item rather than displaying a potentially incorrect recap. If found, display:
   ```
   **Last change you requested for this issue:** <brief description of user's request>
   **What was done:** <specific action taken in response, based on commits or conversation following the request>
   ```
   If no issue-specific change request exists in the conversation history, or the outcome cannot
   be confirmed from the conversation, skip this item.

Invoke AskUserQuestion ONLY AFTER all eight items above are output in the current turn:
- If MEDIUM+ concerns: options = ["Approve and merge", "Fix remaining concerns", "Request changes", "Abort"]
- If no concerns or only LOW: options = ["Approve and merge", "Request changes", "Abort"]

**CRITICAL:** Wait for explicit selection. Empty `toolUseResult.answers` = no selection = re-present gate.
Unknown consent = No consent = STOP. Fail-fast principle applies.

**If approved:** Continue to Step 10

**If "Fix remaining concerns" selected:**

**Iteration cap:** Track the number of fix-review cycles with a counter (`FIX_ITERATION`), starting at 1.
The maximum number of fix-review iterations is **3**. If after 3 iterations MEDIUM+ concerns still remain,
do NOT offer "Fix remaining concerns" again. Instead, present only: ["Approve and merge (with known concerns)",
"Request changes", "Abort"]. This prevents unbounded fix-review loops where each fix introduces new concerns.

1. Extract MEDIUM+ concerns (severity, description, location, recommendation, detail_file)
2. Spawn `cat:work-execute` subagent to fix each MEDIUM+ concern. Pass `ISSUE_PATH` explicitly so the
   subagent can invoke `cat:collect-results-agent` and update STATE.md without constructing the path
   from ISSUE_ID (which gets the `v2/v2.1/` nesting wrong):
   ```
   Task tool:
     description: "Fix remaining concerns for ${ISSUE_ID}"
     subagent_type: "cat:work-execute"
     prompt: |
       Fix each MEDIUM+ concern in the worktree and commit with the same type as the primary
       implementation (e.g., `bugfix:`). Return JSON with commits and files_changed.

       ## Configuration
       ISSUE_ID: ${ISSUE_ID}
       ISSUE_PATH: ${ISSUE_PATH}
       WORKTREE_PATH: ${WORKTREE_PATH}
       TARGET_BRANCH: ${TARGET_BRANCH}

       ## Concerns to fix
       ${MEDIUM_PLUS_CONCERNS}
   ```
3. **MANDATORY: Re-run stakeholder review** after fixes:
   `Skill("cat:stakeholder-review-agent", "${ISSUE_ID} ${WORKTREE_PATH} ${VERIFY} ${ALL_COMMITS_COMPACT}")`
   The review MUST re-run to verify concerns resolved and detect new concerns introduced by fixes
4. **Re-squash all commits by topic** (MANDATORY, M560): Before returning to Step 9, invoke
   `cat:git-squash-agent` again with all commits (original + fix commits). Do NOT return to the approval
   gate without re-squashing — the approval gate must never see more commits than the previous squash attempt.
5. Increment `FIX_ITERATION`. Return to Step 9 approval gate with updated results

**If changes requested:** Return to user with feedback for iteration. Return status:
```json
{
  "status": "CHANGES_REQUESTED",
  "issue_id": "${ISSUE_ID}",
  "feedback": "user feedback text"
}
```

**If aborted:** Clean up and return ABORTED status:
```json
{
  "status": "ABORTED",
  "issue_id": "${ISSUE_ID}",
  "message": "User aborted merge"
}
```

## Step 10: Merge Phase

Display the **Merging phase** banner by running:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase merging
```

**If the command fails or produces no output**, STOP immediately:
```
FAIL: progress-banner launcher failed for phase 'merging'.
The jlink image may not be built. Run: mvn -f hooks/pom.xml verify
```
Do NOT skip the banner or continue without it.

**Exit the worktree directory before the merge operation:**

```bash
cd "${CLAUDE_PROJECT_DIR}"
```

**Pre-merge approval verification (when trust != "high"):**

Before the merge, verify that user approval was obtained in Step 9. This proactive check
eliminates wasted operations that would otherwise be blocked by the PreToolUse hook.

```
if TRUST != "high":
    # Verify Step 9 approval gate was completed
    # If no approval was obtained (e.g., Step 9 was skipped due to a logic error),
    # invoke AskUserQuestion now as a safety net:
    AskUserQuestion:
      question: "Ready to merge ${ISSUE_ID} to ${TARGET_BRANCH}?"
      options:
        - label: "Approve and merge"
          description: "Squash commits and merge to ${TARGET_BRANCH}"
        - label: "Abort"
          description: "Cancel the merge"
    # If user selects "Abort", return ABORTED status (same as Step 9 abort handling)
```

### Execute Merge

Check idempotency guards before invoking the tool. Chain the worktree and branch existence checks:

```bash
# Check worktree exists before removal and check branch exists before deletion in a single call
git worktree list --porcelain | grep -qxF "worktree ${WORKTREE_PATH}" && WORKTREE_EXISTS=true || WORKTREE_EXISTS=false; \
git show-ref --verify --quiet "refs/heads/${BRANCH}" && BRANCH_EXISTS=true || BRANCH_EXISTS=false
```

If `WORKTREE_EXISTS=false` and `BRANCH_EXISTS=false`, cleanup may have already completed in a previous run.
Before synthesizing success, verify the issue's commits are actually on TARGET_BRANCH:

```bash
# Verify the issue was actually merged by checking TARGET_BRANCH tip
MERGE_COMMIT=$(git -C "${CLAUDE_PROJECT_DIR}" rev-parse "${TARGET_BRANCH}" 2>/dev/null)
COMMIT_MSG=$(git -C "${CLAUDE_PROJECT_DIR}" log -1 --format=%s "${TARGET_BRANCH}" 2>/dev/null)
```

If `MERGE_COMMIT` is empty or `COMMIT_MSG` does not contain `ISSUE_ID` as an exact word match (case-insensitive,
bounded by start/end of string, whitespace, or punctuation — not as a substring of a longer issue ID),
STOP and return FAILED with
`"message": "Worktree and branch are both missing but merge cannot be confirmed on ${TARGET_BRANCH}. Manual investigation required."`.
The check must use word-boundary matching (e.g., `grep -iwF` on the full ID, or regex `\bISSUE_ID\b`) to prevent
false positives when one issue ID is a prefix of another (e.g., `v2.1-fix` must NOT match `v2.1-fix-bar`).
Do NOT use subjective judgment about whether the message "relates to" the issue. Additionally, search the last 5
commits on TARGET_BRANCH (not just `log -1`) to handle cases where the real merge is not the tip commit:
`git log -5 --format=%s "${TARGET_BRANCH}"`.

Only if the merge is confirmed, synthesize a success result with `"merge_commit": "<actual hash>"` using the
resolved `MERGE_COMMIT` value and skip to Step 11.

If `WORKTREE_EXISTS=false` and `BRANCH_EXISTS=true`, verify the branch was merged before deleting it:

```bash
COMMIT_MSG=$(git -C "${CLAUDE_PROJECT_DIR}" log -1 --format=%s "${TARGET_BRANCH}" 2>/dev/null)
```

If `COMMIT_MSG` does not contain `ISSUE_ID` as an exact word match (case-insensitive, word-boundary matching as
described above — not a substring of a longer ID), STOP and return FAILED with
`"message": "Worktree is missing but branch ${BRANCH} still exists and merge cannot be confirmed on ${TARGET_BRANCH}. Do NOT delete the branch — it may contain the only copy of the work. Manual investigation required."`.
Search the last 5 commits on TARGET_BRANCH (not just `log -1`). Do NOT delete the branch without confirmed merge.

Only if the merge is confirmed, delete the orphaned branch and synthesize success.

If `WORKTREE_EXISTS=true` and `BRANCH_EXISTS=false`, the branch may have been merged or force-deleted. Before
removing the worktree, verify the branch's commits are reachable from TARGET_BRANCH:

```bash
# Check if the most recent commit message on TARGET_BRANCH references the issue
COMMIT_MSG=$(git -C "${CLAUDE_PROJECT_DIR}" log -1 --format=%s "${TARGET_BRANCH}" 2>/dev/null)
```

If the merge cannot be confirmed (commit message does not contain `ISSUE_ID` as an exact word match,
case-insensitive, word-boundary matching — not a substring of a longer ID), STOP and return FAILED with
`"message": "Branch ${BRANCH} is missing but merge cannot be confirmed on ${TARGET_BRANCH}. The branch may have been force-deleted without merging. Manual investigation required."`.
Search the last 5 commits on TARGET_BRANCH (not just `log -1`).
Do NOT remove the worktree — it may contain the only remaining copy of the work.

Only if the merge is confirmed, remove the orphaned worktree and synthesize success.

Otherwise, invoke the merge-and-cleanup tool:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/merge-and-cleanup" \
  "${CLAUDE_PROJECT_DIR}" "${ISSUE_ID}" "${CLAUDE_SESSION_ID}" "${TARGET_BRANCH}" --worktree "${WORKTREE_PATH}"
```

The Java tool handles: fast-forward merge, worktree removal, branch deletion, backup branch cleanup,
and lock release in a single atomic operation.

| Output | Meaning | Agent Recovery Action |
|--------|---------|----------------------|
| `"status": "success"` (stdout JSON) | Merge and cleanup completed | Continue to Step 11 |
| `"status": "error"`: Target branch has diverged | Target has commits not in HEAD | Rebase onto target branch before merging |
| `"status": "error"`: Fast-forward merge not possible | History diverged | Rebase issue branch onto target branch first |
| `"status": "error"`: Worktree has uncommitted changes | Dirty worktree | Commit or stash changes in worktree first |

### Post-Merge Verification (BLOCKING — M447)

Before proceeding to Step 11, verify the merge actually occurred by checking that `TARGET_BRANCH`
now contains the squashed commit:

Before invoking the merge-and-cleanup tool, capture the pre-merge tip of TARGET_BRANCH:

```bash
PRE_MERGE_TIP=$(git -C "${CLAUDE_PROJECT_DIR}" rev-parse "${TARGET_BRANCH}" 2>/dev/null)
```

After the merge-and-cleanup tool returns success, verify the merge by comparing pre-merge and post-merge state:

```bash
# Verify the merge commit is reachable from TARGET_BRANCH
# git -C is intentional here: the worktree is already removed at this point,
# so we must run from the main project directory.
# IMPORTANT: Use rev-parse on TARGET_BRANCH, NOT HEAD — HEAD refers to whatever branch
# is currently checked out in the main workspace, which may not be TARGET_BRANCH.
POST_MERGE_TIP=$(git -C "${CLAUDE_PROJECT_DIR}" rev-parse "${TARGET_BRANCH}" 2>/dev/null)
if [[ -z "${POST_MERGE_TIP}" ]]; then
  echo "ERROR: Could not resolve ${TARGET_BRANCH} — branch may not exist." >&2
  echo "Do NOT invoke work-complete. Re-run Step 10."
  exit 1
fi
# Verify TARGET_BRANCH actually advanced — if the tip is unchanged, the merge did not happen
if [[ "${POST_MERGE_TIP}" == "${PRE_MERGE_TIP}" ]]; then
  echo "ERROR: Merge not confirmed — ${TARGET_BRANCH} tip is unchanged (${POST_MERGE_TIP})."
  echo "The merge-and-cleanup tool may have failed silently. Do NOT invoke work-complete."
  echo "Re-run Step 10 using the Task tool."
  exit 1
fi
# Verify the commit message on the new tip references the issue (exact word match)
MERGE_MSG=$(git -C "${CLAUDE_PROJECT_DIR}" log -1 --format=%s "${TARGET_BRANCH}" 2>/dev/null)
if ! echo "${MERGE_MSG}" | grep -iqw "${ISSUE_ID}"; then
  echo "ERROR: Merge not confirmed — new tip commit message does not reference ${ISSUE_ID}."
  echo "Do NOT invoke work-complete. Re-run Step 10."
  exit 1
fi
```

If verification fails: STOP — do NOT invoke `work-complete`. Re-execute Step 10.

## Step 11: Return Success

Return summary to the main `/cat:work` skill:

```json
{
  "status": "SUCCESS",
  "issue_id": "${ISSUE_ID}",
  "commits": [...],
  "files_changed": N,
  "tokens_used": N,
  "merged": true
}
```

## Rejection Handling

### User Rejects Approval Gate (Step 9)

When the user rejects the AskUserQuestion (e.g., by invoking `/cat:learn` or asking a question), the gate was
**NOT answered**. Re-present the full approval gate in the NEXT response: re-run `cat:get-diff`, re-display
commit summary/goal/E2E/review results, re-invoke AskUserQuestion. Do NOT proceed to merge, release the lock,
or invoke `work-complete` without explicit user selection. Unknown consent = No consent = STOP and re-present.

## Error Handling

If any phase fails:

1. Capture error message and phase name
2. Restore working directory: `cd "${CLAUDE_PROJECT_DIR}"`
3. **Classify the error as transient or permanent using this decision tree (in order):**
   1. If the user explicitly aborted or rejected: **permanent** — release the lock.
   2. If the error message contains `index.lock`, `shallow.lock`, or `Unable to create.*lock`: **transient**.
   3. If the error message contains `Connection refused`, `Connection timed out`, `SSL`, or `Could not resolve host`: **transient**.
   4. If the error message contains `CONFLICT` or `merge conflict`: **permanent** — release the lock.
   5. If the error references a missing branch (`not found`, `unknown revision`): **permanent** — release the lock.
   6. If the error references repository corruption (`bad object`, `corrupt`, `fatal: packed-refs`): **permanent** — release the lock.
   7. If none of the above match: **permanent** — release the lock (default to releasing to avoid indefinite lock retention).

   - **Transient errors** (rules 2-3): do NOT release the lock. Hold it so the user can retry.
   - **Permanent errors** (rules 1, 4-7): release the lock:
     `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${ISSUE_ID}" "$CLAUDE_SESSION_ID"`
4. Return FAILED status with actual error details, including whether the lock was retained or released

```json
{
  "status": "FAILED",
  "phase": "squash|rebase|merge",
  "message": "actual error message",
  "issue_id": "${ISSUE_ID}",
  "lock_retained": true
}
```

Set `"lock_retained": true` for transient errors (safe to retry) and `"lock_retained": false` for permanent errors
(lock was released).
