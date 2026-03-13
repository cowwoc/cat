---
name: work-merge-agent
description: "Internal merge phase (invoked by /cat:work-with-issue) - pre-merge squash/rebase, approval gate, then executes merge and cleanup."
user-invocable: false
argument-hint: "<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <commits_json> <trust> <verify>"
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Phase: Merge

Merge phase for `/cat:work`. Handles pre-merge squash and rebase, approval gate, then executes
commit squashing, branch merging, worktree cleanup, and state updates.

## MANDATORY STEPS

- **Step 7: Squash Commits by Topic Before Review** — always squash before the approval gate; do not proceed to
  Step 8 without completing this step
- **Step 8: Rebase onto Target Branch Before Approval Gate** — always rebase the squashed branch onto the current tip
  of the target branch before the approval gate; do not proceed to Step 9 without completing this step
- **Step 9, Pre-Gate Skill-Builder Review** — always invoke `cat:skill-builder` for modified skill or
  command files before presenting the approval gate
- **Step 9, Re-squash and Re-rebase after Skill-Builder** — if skill-builder added commits, always
  re-squash and re-rebase before the approval gate; do not present the approval gate on an un-squashed or
  un-rebased branch

## Arguments and Configuration

`<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <commits_json> <trust> <verify>`

```bash
read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH COMMITS_JSON TRUST VERIFY <<< "$ARGUMENTS"
PLAN_MD="${ISSUE_PATH}/PLAN.md"
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
  ENCODED_PROJECT_DIR=$(printf '%s' "${CLAUDE_PROJECT_DIR}" | sed 's|/|%2F|g; s| |%20|g')
  SQUASH_MARKER_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/.cat"
  mkdir -p "${SQUASH_MARKER_DIR}"
  SQUASH_COMMIT_HASH=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
  echo "squashed:${SQUASH_COMMIT_HASH}" > "${SQUASH_MARKER_DIR}/squash-complete-${ISSUE_ID}"
  ```
  The marker file MUST be written only on the SUCCESS path after the squash subagent returns success. Do NOT
  write the marker file manually, conditionally, or from any step other than this SUCCESS handler.
  Continue to Step 8.
- **FAILED** (phase: rebase): Return FAILED status with conflict details. Do NOT proceed.
- **FAILED** (phase: squash or verify): Return FAILED status with error details. Do NOT proceed.

## Step 8: Rebase onto Target Branch Before Approval Gate (MANDATORY)

Before presenting the approval gate, rebase the squashed issue branch onto the current tip of
the target branch. This ensures the diff shown at the approval gate reflects what the merge will
actually produce.

### Step 8a: Capture Old Fork Point

Record the current fork point before the rebase so the impact analysis can compare what changed:

```bash
cd "${WORKTREE_PATH}"
OLD_FORK_POINT=$(git merge-base HEAD "${TARGET_BRANCH}" 2>/dev/null)
if [[ -z "$OLD_FORK_POINT" ]]; then
  echo "ERROR: Could not determine fork point before rebase" >&2
  exit 1
fi
```

### Step 8b: Perform Rebase

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
- Update the squash marker to reflect the new HEAD after rebase (same as the OK path above):
  ```bash
  ENCODED_PROJECT_DIR=$(printf '%s' "${CLAUDE_PROJECT_DIR}" | sed 's|/|%2F|g; s| |%20|g')
  SQUASH_MARKER_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/.cat"
  REBASED_HASH=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
  echo "squashed:${REBASED_HASH}" > "${SQUASH_MARKER_DIR}/squash-complete-${ISSUE_ID}"
  ```
- Proceed to Step 8c (Impact Analysis)

**If rebase reports OK:**
- Delete the backup branch created by cat:git-rebase-agent
- Update the squash marker to reflect the new HEAD after rebase (rebase changes the commit hash):
  ```bash
  ENCODED_PROJECT_DIR=$(printf '%s' "${CLAUDE_PROJECT_DIR}" | sed 's|/|%2F|g; s| |%20|g')
  SQUASH_MARKER_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/.cat"
  REBASED_HASH=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
  echo "squashed:${REBASED_HASH}" > "${SQUASH_MARKER_DIR}/squash-complete-${ISSUE_ID}"
  ```
- Proceed to Step 8c (Impact Analysis)

**If rebase reports ERROR:**
- Output the error message
- Restore from the backup branch if needed
- STOP — do not proceed to approval gate until the error is resolved

### Step 8c: Post-Rebase Impact Analysis

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
ENCODED_PROJECT_DIR=$(printf '%s' "${CLAUDE_PROJECT_DIR}" | sed 's|/|%2F|g; s| |%20|g')
SESSION_ANALYSIS_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/.cat"
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
| `NO_IMPACT` | Continue silently to Step 9 |
| `LOW` | Continue silently to Step 9 |
| `MEDIUM` | Auto-revise PLAN.md then continue to Step 9 (see below) |
| `HIGH` | Write proposal file then ask user for guidance (see below) |

**MEDIUM: Auto-Revision**

Read `EFFORT` from config and read the full analysis file before invoking the plan builder:

```bash
CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.cat/cat-config.json"
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

After the plan-builder (and any code-revision subagent) returns, continue to Step 9.

**HIGH: User Guidance Required**

Write `${ISSUE_PATH}/rebase-conflict-proposal.md` summarizing the conflict (analysis_path, summary from IMPACT_JSON,
options: revise plan / proceed / abort). Present the file path to the user and invoke `AskUserQuestion`
before continuing to Step 9.

## Step 9: Approval Gate (MANDATORY)

**CRITICAL: This step is MANDATORY when trust != "high".**

**Enforced by hook M480:** PreToolUse hook on Task tool blocks work-merge spawn when trust=medium/low
and no explicit user approval is detected in session history.

### Pre-Gate Squash Verification (BLOCKING)

Before presenting the approval gate, verify that Step 7 (Squash by Topic) was executed in the **current session**
by checking for the durable squash marker file written by Step 7:

```bash
ENCODED_PROJECT_DIR=$(printf '%s' "${CLAUDE_PROJECT_DIR}" | sed 's|/|%2F|g; s| |%20|g')
SQUASH_MARKER="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/.cat/squash-complete-${ISSUE_ID}"
if [[ ! -f "${SQUASH_MARKER}" ]]; then
  echo "ERROR: Squash marker not found — Step 7 was not completed in this session." >&2
  echo "Return to Step 7 and complete the squash by topic before proceeding." >&2
  exit 1
fi
# Validate marker content: must be "squashed:<commit-hash>" where the hash is a valid git object
MARKER_CONTENT=$(cat "${SQUASH_MARKER}")
MARKER_HASH="${MARKER_CONTENT#squashed:}"
if [[ "${MARKER_CONTENT}" != squashed:* ]] || [[ -z "${MARKER_HASH}" ]]; then
  echo "ERROR: Squash marker has invalid format — expected 'squashed:<commit-hash>'." >&2
  echo "Return to Step 7 and re-run the squash." >&2
  exit 1
fi
if ! git -C "${WORKTREE_PATH}" cat-file -e "${MARKER_HASH}" 2>/dev/null; then
  echo "ERROR: Squash marker references non-existent commit ${MARKER_HASH}." >&2
  echo "Return to Step 7 and re-run the squash." >&2
  exit 1
fi
# Verify marker hash matches current HEAD — after rebase or amend, the marker must have been updated
CURRENT_HEAD=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
if [[ "${MARKER_HASH}" != "${CURRENT_HEAD}" ]]; then
  echo "ERROR: Squash marker hash (${MARKER_HASH}) does not match current HEAD (${CURRENT_HEAD})." >&2
  echo "The marker was not updated after rebase or amend. Return to Step 7 and re-run the squash." >&2
  exit 1
fi
```

**CRITICAL:** `STATE.md status: closed` does NOT prove Step 7 ran. STATE.md reflects implementation completion
(State 1), not squash execution. Do NOT infer that Step 7 was already completed from STATE.md showing `closed`.
Only the presence of the squash marker file (`squash-complete-${ISSUE_ID}`) in the session directory counts as
proof Step 7 ran. Do NOT rely on session context alone, as context compaction may remove invocation history.

**If Step 7 was skipped (marker file absent):** STOP — return to Step 7 and complete the squash by topic before
proceeding.

**If Step 7 was completed (marker file present):** Proceed to approval gate below.

### Pre-Gate Skill-Builder Review (MANDATORY — BLOCKING)

**MANDATORY:** When the issue modifies files in `plugin/skills/` or `plugin/commands/`, invoke `/cat:skill-builder`
to review each modified skill or command file before presenting the approval gate. This step must not be skipped —
do not proceed to the approval gate without completing skill-builder review for all modified skill or command files.

```bash
# Check whether any skill or command files were modified
git diff --name-only "${TARGET_BRANCH}..HEAD" | grep -E '^plugin/(skills|commands)/'
```

**If skill or command files were modified:** Before invoking `/cat:skill-builder`, capture the current HEAD so new
commits can be detected after it completes:

```bash
PRE_SKILLBUILDER_HEAD=$(git -C "${WORKTREE_PATH}" rev-parse HEAD)
```

Invoke `/cat:skill-builder` with the path to each modified skill or command. Review the output and address any
priming issues or structural problems it identifies before proceeding to the approval gate.

### Re-squash and Re-rebase after Skill-Builder (MANDATORY — BLOCKING)

**MANDATORY:** After skill-builder completes, check whether it added any new commits to the branch. If it did,
re-squash and re-rebase are REQUIRED before presenting the approval gate. Do NOT present the approval gate on an
un-squashed or un-rebased branch — the user would be approving a state that does not reflect the actual merge.

**If no skill or command files were modified:** Skip this entire section and proceed to Step 9.1.

```bash
# Check for new commits since skill-builder ran
CURRENT_HEAD=$(git -C "${WORKTREE_PATH}" rev-parse HEAD)
NEW_COMMITS=$( [ "${CURRENT_HEAD}" != "${PRE_SKILLBUILDER_HEAD}" ] && echo "yes" || echo "" )
```

**If no new commits exist** (skill-builder made no changes): skip re-squash and re-rebase, continue to Step 9.1.

**If new commits exist (skill-builder added commits):**

1. **Re-invoke `cat:git-squash-agent`** with the same primary commit message used in Step 7:
   ```
   Skill("cat:git-squash-agent", args="${WORKTREE_PATH} ${TARGET_BRANCH} ${PRIMARY_COMMIT_MESSAGE}")
   ```
   Update the squash marker after re-squashing:
   ```bash
   ENCODED_PROJECT_DIR=$(printf '%s' "${CLAUDE_PROJECT_DIR}" | sed 's|/|%2F|g; s| |%20|g')
   SQUASH_MARKER_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/.cat"
   RE_SQUASH_HASH=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
   echo "squashed:${RE_SQUASH_HASH}" > "${SQUASH_MARKER_DIR}/squash-complete-${ISSUE_ID}"
   ```
   If `cat:git-squash-agent` returns failure, STOP immediately — do NOT proceed to re-rebase.
   Return FAILED status with the squash error details (same pattern as Step 7's FAILED handler).

2. **Re-run Step 8 (`cat:git-rebase-agent`)** to rebase onto the target branch after re-squashing:
   ```
   Skill("cat:git-rebase-agent", args="${WORKTREE_PATH} ${TARGET_BRANCH}")
   ```
   Update the squash marker to reflect the new HEAD after rebase:
   ```bash
   RE_REBASED_HASH=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
   echo "squashed:${RE_REBASED_HASH}" > "${SQUASH_MARKER_DIR}/squash-complete-${ISSUE_ID}"
   ```
   If `cat:git-rebase-agent` reports CONFLICT: STOP — do NOT proceed to the approval gate. Return FAILED status
   with the conflict details.
   If `cat:git-rebase-agent` reports ERROR: STOP — do NOT proceed to the approval gate. Return FAILED status
   with the error details.
   (Unlike Step 8b, do not attempt conflict resolution here — fail fast, as conflicts at this stage indicate an
   unexpected problem introduced by skill-builder commits.)

**CRITICAL:** Do NOT present the approval gate until BOTH re-squash AND re-rebase have completed successfully.

### Step 9.1: Post-Skill-Builder Artifact Cleanup (MANDATORY)

After skill-builder-agent completes its review (if invoked in the Pre-Gate Skill-Builder Review step), clean up all
temporary artifact files it created. These files (findings.json, diff-validation-*.json) are intermediate outputs of
the adversarial TDD loop and must not be committed to the issue branch.

**Why:** skill-builder-agent creates temporary files during its red-team → blue-team → diff-validation cycle to track
hardening iterations. These files have no value in the final codebase and must be removed before the implementation is
committed.

```bash
cd "${WORKTREE_PATH}"

# Remove skill-builder temporary artifacts that should not be committed
rm -f findings.json diff-validation-*.json 2>/dev/null

# Remove from git index if tracked
git rm --cached findings.json diff-validation-*.json 2>/dev/null || true
rm -f findings.json diff-validation-*.json 2>/dev/null

# Verify no skill-builder artifacts remain in git staging or working directory
if git status --porcelain | grep -qE '(findings\.json|diff-validation-.*\.json)'; then
  echo "ERROR: Skill-builder artifacts still present after cleanup — cannot proceed." >&2
  exit 1
fi

# If the removal changed the index (artifacts were committed during the adversarial TDD loop),
# amend the squashed commit to exclude them. This is safe because the squash just ran in Step 7
# and has not been pushed.
if ! git diff --cached --quiet 2>/dev/null; then
  git commit --amend --no-edit
  # Update squash marker to reflect the new HEAD after amend — the amend changes the commit hash,
  # so the marker must be rewritten to stay consistent with the current branch tip.
  ENCODED_PROJECT_DIR=$(printf '%s' "${CLAUDE_PROJECT_DIR}" | sed 's|/|%2F|g; s| |%20|g')
  SQUASH_MARKER_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/.cat"
  NEW_SQUASH_HASH=$(git rev-parse HEAD)
  echo "squashed:${NEW_SQUASH_HASH}" > "${SQUASH_MARKER_DIR}/squash-complete-${ISSUE_ID}"
fi
```

**If skill-builder was NOT invoked** (no skill/command files modified): skip this cleanup step entirely and proceed
directly to Step 9.2.

### Step 9.2: Approval Gate

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
4. Increment `FIX_ITERATION`. Return to Step 9 approval gate with updated results

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
