<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Phase: Merge

Merge phase for `/cat:work`. Handles pre-merge squash and rebase, approval gate, then executes
commit squashing, branch merging, worktree cleanup, and state updates.

## MANDATORY STEPS

- **Step 7: Post-Condition Gate Check** — verify all post-conditions are Done before squashing/rebasing
- **Step 8: Squash Commits by Topic Before Review** — squash commits before rebase and instruction-builder review
- **Step 9: Rebase onto Target Branch** — rebase before approval gate; do not skip
- **Step 10: Instruction-Builder Review** — invoke `cat:instruction-builder` for modified skill/command files before gate
- **Step 11: Squash Before Approval Gate** — squash immediately before presenting gate. Re-squash ALL commits
  on EVERY presentation, including after user feedback.
  - **Background Task Completion** — ALL background tasks — including any reviewer subagents
    spawned during the review phase — must have returned via `<task-notification>` before invoking the
    structured approval tool.

## Arguments and Configuration

`<issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <commits_json_path> <trust> <caution>`

```bash
read ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH COMMITS_JSON_PATH TRUST CAUTION <<< "$ARGUMENTS"
PLAN_MD="${ISSUE_PATH}/plan.md"
COMMITS_JSON=$(cat "$COMMITS_JSON_PATH")
```

Return JSON: `{"status": "SUCCESS|ABORTED|CHANGES_REQUESTED|FAILED", "issue_id": "...", "commits": [...], "files_changed": N, "merged": true}`

## Issue Lifecycle States

| State | Description | Authoritative Indicator |
|-------|-------------|-------------------------|
| **Implementation running** | Worktree exists, lock held | Worktree present, lock file, branch exists |
| **Merge complete** | merge-and-cleanup returned `"status": "success"` in current session | Tool result in context |
| **Issue closed** | Worktree removed, lock released, branch deleted | No worktree/lock/branch |

**CRITICAL:** `index.json status: closed` means implementation is done, NOT that the issue was merged. An issue can
have `index.json status: closed` while the worktree still exists and merge has not run. The only authoritative proof
of State 2 (merge complete) is that merge-and-cleanup returned `"status": "success"` in the current session. If that
result is not in context, treat as State 1 and run the full merge workflow.

## Step 7: Post-Condition Gate Check (MANDATORY — BLOCKING)

```bash
VERIFY_DIR="${WORKTREE_PATH}/.cat/work/verify/${CAT_SESSION_ID}"
CRITERIA_FILE="${VERIFY_DIR}/criteria-analysis.json"
```

If `CRITERIA_FILE` does not exist (e.g., `CAUTION == "low"` or confirm phase was skipped):

```
NOTE: criteria-analysis.json not found — post-condition gate check skipped
```

Output the note and continue to Step 8 without blocking.

If file exists, check for unmet criteria:
```bash
UNMET=$(grep -E '"status"[[:space:]]*:[[:space:]]*"(Partial|Missing)"' "${CRITERIA_FILE}" || true)
```

If `UNMET` is empty, all criteria are Done — continue to Step 8 without blocking.

If `UNMET` is non-empty, extract criterion name + status pairs and display a blocking error:

```bash
# Extract criterion name + status pairs for the unmet items.
# For each criterion with Partial or Missing status, find the "name" field in the surrounding
# context block. Use -EB5 to get up to 5 lines of context before each status match, then use
# awk to pair name and status values within each context block regardless of field order.
UNMET_DETAILS=$(grep -EB5 '"status"[[:space:]]*:[[:space:]]*"(Partial|Missing)"' "${CRITERIA_FILE}" \
  | awk '
    /^--$/ { if (name != "" && status != "") print "  - " name " [" status "]"; name=""; status="" }
    /"name"[[:space:]]*:/ { match($0, /"name"[[:space:]]*:[[:space:]]*"([^"]+)"/, a); name=a[1] }
    /"status"[[:space:]]*:/ { match($0, /"status"[[:space:]]*:[[:space:]]*"([^"]+)"/, a); status=a[1] }
    END { if (name != "" && status != "") print "  - " name " [" status "]" }
  ')
```

Display a blocking error and return FAILED — do NOT proceed to squash, rebase, or the approval gate:

```
BLOCKED: Approval gate cannot be presented — unmet post-conditions detected.

The confirm phase reported the following criteria as not fully satisfied:
${UNMET_DETAILS}

Required action:
1. Return to the implementation phase and address each unmet criterion.
2. Re-run /cat:work to complete confirm → review → merge in sequence.
3. Do NOT manually squash commits or force-present the approval gate.

The gate protects users from approving incomplete work. Fix the implementation first.
```

Return FAILED status:
```json
{"status": "FAILED", "phase": "pre-gate-check", "message": "Approval gate blocked: unmet post-conditions (see above)", "issue_id": "${ISSUE_ID}", "lock_retained": true}
```

## Step 8: Squash Commits by Topic Before Review (MANDATORY)

**MANDATORY:** Delegate to a squash subagent. The approval gate (Step 11) blocks if this step was skipped.

It is correct and expected to squash commits on `${BRANCH}` when index.json shows `closed` — this means the
implementation subagent finished (State 1 complete) and merge preparation is running. This authorization applies
only to `${BRANCH}`.

Extract the primary commit message:
```bash
PRIMARY_COMMIT_MESSAGE=$(echo "$COMMITS_JSON" | \
  grep -o '"message"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | \
  sed 's/"message"[[:space:]]*:[[:space:]]*"\(.*\)"/\1/')
```

Spawn the squash subagent:
```
Task tool:
  description: "Squash: rebase, squash commits, verify index.json"
  subagent_type: "cat:work-squash"
  prompt: |
    Execute the squash phase for issue ${ISSUE_ID}.
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    TARGET_BRANCH: ${TARGET_BRANCH}
    PRIMARY_COMMIT_MESSAGE: ${PRIMARY_COMMIT_MESSAGE}
    Load and follow: @${CAT_PLUGIN_ROOT}/agents/common/work-squash.md
    Return JSON per the output contract in the agent definition.
```

### Handle Squash Result

- **SUCCESS**: Extract `commits` array. Write squash marker (MANDATORY — gate blocks if absent):
  ```bash
  SQUASH_COMMIT_HASH=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
  "${CAT_PLUGIN_ROOT}/client/bin/write-session-marker" "${WORKTREE_PATH}" "${ISSUE_ID}" "squashed:${SQUASH_COMMIT_HASH}"
  if [[ $? -ne 0 ]]; then
    echo "ERROR: write-session-marker failed. Do NOT proceed to Step 9." >&2
    exit 1
  fi
  ```
  Continue to Step 9.

- **FAILED** (any phase): Return FAILED status. Do NOT proceed.

**write-session-marker allowlist** — ONLY authorized at:
- **Step 8 SUCCESS**: write `squashed:<hash>`
- **Step 11 SUCCESS**: update `squashed:<hash>`
- **Step 12 "Approve and merge"**: write `approved`
- **Step 12 trust=high auto-merge (HIGH_TRUST_PAUSE=false)**: write `approved`
- **Step 12 "Fix remaining concerns"**: write `approved:invalidated`
- **Step 12 "Request changes"**: write `approved:invalidated`
- **Step 12 "Abort"**: write `approved:invalidated`
- **Step 13 merge failure**: write `approved:invalidated`

Invoking `write-session-marker` from any other step or for any other value is PROHIBITED.

**Single-file marker model:** Each write OVERWRITES previous content. The marker values form a state progression:
`(empty)` → `squashed:<hash>` → `approved` → `approved:invalidated`. They are NOT independent named markers.

## Step 9: Rebase onto Target Branch (MANDATORY)

### Step 9a: Capture Old Fork Point

```bash
cd "${WORKTREE_PATH}"
OLD_FORK_POINT=$(git merge-base HEAD "${TARGET_BRANCH}" 2>/dev/null)
if [[ -z "$OLD_FORK_POINT" ]]; then echo "ERROR: Could not determine fork point" >&2; exit 1; fi
```

### Step 9b: Perform Rebase

```
Skill("cat:git-rebase", args="{WORKTREE_PATH} {TARGET_BRANCH}")
```

- **CONFLICT**: Follow the numbered steps in **## Handling Conflicts** in the git-rebase skill. Record
  `CONFLICT_RESOLUTIONS`. The deterministic launcher returns `CONFLICT` after aborting the rebase, so first start
  a native rebase session (use `OLD_FORK_POINT` from Step 9a and pinned `target_branch` from the JSON response),
  then run the conflict-resolution steps. Set `REBASE_HAD_CONFLICTS=true`. Delete backup branch. Proceed to Step 9c.
- **OK**: Delete backup branch. Proceed to Step 9c.
- **ERROR**: Output error, restore from backup if needed. STOP.

### Step 9c: Post-Rebase Impact Analysis

```bash
NEW_FORK_POINT=$(git merge-base HEAD "${TARGET_BRANCH}" 2>/dev/null)
IMPACT_ANALYSIS_SKIPPED=false
if [[ -z "$NEW_FORK_POINT" ]]; then IMPACT_ANALYSIS_SKIPPED=true; fi
```

If `NEW_FORK_POINT == OLD_FORK_POINT`: no new upstream commits — skip impact analysis, continue to Step 10.

Otherwise:
```bash
SESSION_ANALYSIS_DIR="${WORKTREE_PATH}/.cat/work/sessions/${CAT_SESSION_ID}"
mkdir -p "${SESSION_ANALYSIS_DIR}"
```
```
IMPACT_JSON = Skill("cat:git-rebase", args="${ISSUE_PATH} ${WORKTREE_PATH} ${OLD_FORK_POINT} ${NEW_FORK_POINT} ${SESSION_ANALYSIS_DIR}")
```
```bash
IMPACT_SEVERITY=$(echo "${IMPACT_JSON}" | grep -o '"severity"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | head -1 | sed 's/.*"severity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
```

| `IMPACT_SEVERITY` | Action |
|-------------------|--------|
| `NO_IMPACT` / `LOW` | Continue to Step 10 |
| `MEDIUM` | Auto-revise plan.md then continue to Step 10 |
| `HIGH` | Write proposal file, ask user via the runtime's structured input tool |

**MEDIUM:** Read EFFORT from config, then invoke:
```
Skill("cat:plan-builder", args="${EFFORT} revise ${ISSUE_PATH} rebase introduced upstream changes — see ${ANALYSIS_PATH}")
```
If implementation was already committed, spawn a code-revision subagent to apply the revised plan.md.

**HIGH:** Write `${ISSUE_PATH}/rebase-conflict-proposal.md` summarizing the conflict, then present via the runtime's
structured input tool.

## Step 10: Instruction-Builder Review (MANDATORY — BLOCKING)

```bash
git diff --name-only "${TARGET_BRANCH}..HEAD" | grep -E '^plugin/(skills|commands)/'
```

If skill/command files were modified: invoke `/cat:instruction-builder` for each. Address any issues found.

If none modified: skip to artifact cleanup.

### Post-Instruction-Builder Artifact Cleanup (MANDATORY)

```bash
cd "${WORKTREE_PATH}"
rm -f findings.json diff-validation-*.json instruction-test-artifacts/ 2>/dev/null
git rm --cached findings.json diff-validation-*.json 2>/dev/null || true
```

## Step 11: Squash Before Approval Gate (MANDATORY)

**Pre-squash guard:** Read the squash marker and extract its hash. Compare to current HEAD:
- If `HEAD == marker hash`: branch is already squashed at the same state — skip squash, proceed to gate.
- Otherwise: re-squash AND update marker with new hash.

```bash
PRIMARY_COMMIT_MESSAGE=$(echo "$COMMITS_JSON" | \
  grep -o '"message"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | \
  sed 's/"message"[[:space:]]*:[[:space:]]*"\(.*\)"/\1/')
```
```
Skill("cat:git-squash", args="${WORKTREE_PATH} ${TARGET_BRANCH} ${PRIMARY_COMMIT_MESSAGE}")
```

After squash, update marker:
```bash
FINAL_COMMIT=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
FINAL_DIFF_STAT=$(cd "${WORKTREE_PATH}" && git diff --stat ${TARGET_BRANCH}..HEAD)
"${CAT_PLUGIN_ROOT}/client/bin/write-session-marker" "${WORKTREE_PATH}" "${ISSUE_ID}" "squashed:${FINAL_COMMIT}"
```

If squash fails: return FAILED. Do NOT proceed to gate.

## Step 12: Approval Gate (MANDATORY)

**trust == "high":** Read the review result file to determine whether to auto-merge or pause.

```bash
REVIEW_DIR="${WORKTREE_PATH}/.cat/work/review/${CAT_SESSION_ID}"
REVIEW_RESULT_FILE="${REVIEW_DIR}/${ISSUE_ID}-result.json"

HIGH_TRUST_PAUSE="false"
if [[ -f "${REVIEW_RESULT_FILE}" ]]; then
  PERSISTED_STATUS=$(grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' "${REVIEW_RESULT_FILE}" | \
    head -1 | sed 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
  PERSISTED_HAS_HIGH=$(grep -o '"has_high_or_critical"[[:space:]]*:[[:space:]]*[^,}]*' "${REVIEW_RESULT_FILE}" | \
    sed 's/.*"has_high_or_critical"[[:space:]]*:[[:space:]]*\([^,}]*\)/\1/' | tr -d ' ')

  if [[ "$PERSISTED_STATUS" == "CONCERNS_FOUND" || "$PERSISTED_HAS_HIGH" == "true" ]]; then
    HIGH_TRUST_PAUSE="true"
  fi
fi
# If REVIEW_RESULT_FILE absent: caution=low was configured, no review ran → treat as APPROVED → auto-merge.
# If REBASE_HAD_CONFLICTS=true: always pause regardless of review result.
if [[ "${REBASE_HAD_CONFLICTS:-false}" == "true" ]]; then
  HIGH_TRUST_PAUSE="true"
fi
```

If `HIGH_TRUST_PAUSE == "false"`:
- Auto-merge path: proceed directly to Step 13 without invoking the structured approval tool.
- Still run the pre-merge approval verification (marker check) to maintain audit trail.
- Set `APPROVAL_MARKER=true` automatically (no user gate needed for trust=high clean review).

If `HIGH_TRUST_PAUSE == "true"`:
- Present concerns and conflict resolutions as in the standard gate.
- Offer: `["Approve and merge", "Fix concerns", "Abort"]`
- Process responses per the existing gate logic.

**trust == "low" or "medium":** STOP for explicit user approval.

### Pre-Gate Background Task Completion (MANDATORY — BLOCKING)

ALL background tasks (started with `run_in_background: true`) — including any reviewer subagents spawned during the
review phase — must have delivered `<task-notification>` before presenting pre-gate output or the structured
approval tool. Do NOT assume completion based on time or conversation turns.

**Reviewer completion check before approval (MANDATORY):**

This freshness check protects the approval gate. It is evaluated before the user is asked to approve merge. Do not
reuse this check after approval to force a stakeholder-review rerun solely because a merge-time rebase changed the
commit SHA while replaying the same approved patch onto a newer target branch.

`ISSUE_ID` is the issue identifier passed as a parameter to `work-merge` (the same `issue_id` used throughout
the merge skill for lock files and state). `CAUTION` is already parsed from the `$ARGUMENTS` binding at the top of
this skill.

```bash
REVIEW_DIR="${WORKTREE_PATH}/.cat/work/review/${CAT_SESSION_ID}"
REVIEW_RESULT_FILE="${REVIEW_DIR}/${ISSUE_ID}-result.json"
```

If `REVIEW_RESULT_FILE` exists:
- Read the file and extract the `status` and `reviewed_head_sha` fields:
  ```bash
  PERSISTED_STATUS=$(grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' "${REVIEW_RESULT_FILE}" | \
    head -1 | sed 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
  PERSISTED_HEAD_SHA=$(grep -o '"reviewed_head_sha"[[:space:]]*:[[:space:]]*"[^"]*"' "${REVIEW_RESULT_FILE}" | \
    head -1 | sed 's/.*"reviewed_head_sha"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
  CURRENT_HEAD_SHA=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
  ```
- If `PERSISTED_STATUS` is absent or empty: STOP with error:
  ```
  ERROR: Review result file at ${REVIEW_RESULT_FILE} exists but contains no valid status.
  All reviewer subagents must complete before the approval gate can be presented.
  Re-run /cat:work to retry the review phase.
  ```
- If `PERSISTED_STATUS == "FAILED"`: STOP with error:
  ```
  ERROR: Review phase reported FAILED status. One or more reviewer subagents did not return a result.
  All reviewer subagents must complete before the approval gate can be presented.
  Re-run /cat:work to retry the review phase.
  ```
- If `PERSISTED_HEAD_SHA` is absent or empty: STOP with error:
  ```
  ERROR: Review result file at ${REVIEW_RESULT_FILE} contains no reviewed_head_sha.
  The review phase must run after the latest implementation change before the approval gate can be presented.
  Re-run /cat:work to retry the review phase.
  ```
- If `PERSISTED_HEAD_SHA != CURRENT_HEAD_SHA`: STOP with error:
  ```
  ERROR: Review result is stale. Reviewed HEAD ${PERSISTED_HEAD_SHA}, but current HEAD is ${CURRENT_HEAD_SHA}.
  Re-run stakeholder review after the latest implementation change before presenting the approval gate.
  Re-run /cat:work to retry the review phase.
  ```

If `REVIEW_RESULT_FILE` does not exist AND `CAUTION != "low"` (i.e., review was not explicitly skipped):
- STOP with error:
  ```
  ERROR: Review result file not found at ${REVIEW_RESULT_FILE}.
  The review phase must complete successfully before the approval gate is shown.
  Ensure the review phase ran and all reviewer subagents returned results.
  Re-run /cat:work to retry.
  ```

### Present Changes Before Approval Gate (BLOCKING)

Output all of the following in the current turn, in this order, before invoking the structured approval tool:

1. **Diff** — `Skill("cat:get-diff", "${ISSUE_PATH}")`
2. **Commit summary and issue goal:**
   ```bash
   cd "${WORKTREE_PATH}" && git log --oneline ${TARGET_BRANCH}..HEAD && \
   ISSUE_GOAL=$(grep -A1 "^## Goal" "${ISSUE_PATH}/plan.md" | tail -n1) && echo "Issue Goal: ${ISSUE_GOAL}"
   ```
3. **Execution summary** (commit count, files changed)
4. **E2E testing summary** (what ran, what was verified, results; if skipped: state explicitly)
5. **Impact analysis warning** (if `IMPACT_ANALYSIS_SKIPPED=true`): display prominent warning that upstream changes
   were not analyzed
6. **Rebase conflict resolutions** (if `REBASE_HAD_CONFLICTS=true`): for each file, show the resolution strategy
   from `CONFLICT_RESOLUTIONS` AND run `git diff ${TARGET_BRANCH}...HEAD -- <file>` so the user can verify the
   actual resolved diff. Do NOT show only the self-reported strategy without the accompanying diff.
7. **All stakeholder concerns** (ALL severities — do NOT suppress MEDIUM or LOW):
   - Fixed: `"${CAT_PLUGIN_ROOT}/client/bin/get-stakeholder-concern-box" "${SEVERITY}" "${STAKEHOLDER}" "${FILE}" "${CONCERN}"`
   - Deferred: include `[deferred: benefit=..., cost=..., threshold=...]`
8. **Last change request recap** — scan for most recent user message requesting a change to this issue. If found:
   ```
   Last change you requested for this issue: <brief description>
   What was done: <action taken, based on commits or conversation>
   ```
   If no issue-specific change request exists or outcome cannot be confirmed, skip this item.

Invoke the runtime-specific structured approval tool ONLY AFTER all eight items are output.

Use the active runtime's tool:
- Claude Code: invoke `structured user-choice prompt`.
- Codex: invoke `request_user_input` only when the current session already exposes it. Codex agents cannot switch
  collaboration modes themselves; `request_user_input` is available only in Plan mode.

If no structured input tool is available:
- Claude Code: fail closed; do not write the approval marker and do not accept chat approval.
- Codex Default mode: ask the user verbally with the same option labels. Only a case-insensitive exact match for
  one presented option is accepted. Do not accept `"yes"`, `"ok"`, `"proceed"`, `"go ahead"`, or paraphrases.

Approval options:
- MEDIUM+ concerns: options = ["Approve and merge", "Fix remaining concerns", "Request changes", "Abort"]
- No concerns or only LOW: options = ["Approve and merge", "Request changes", "Abort"]

For Codex `request_user_input`, use one question with `id: "approval_gate"`, header `"Merge"`, the same option
labels, and short descriptions. If the tool adds a free-form/Other path, treat that response as NOT APPROVED
unless it maps to an exact presented option.

For Codex verbal approval in Default mode, ask:

```text
Reply with exactly one option:
- Approve and merge
- Request changes
- Abort
```

If MEDIUM+ concerns exist, include `Fix remaining concerns` in the same list. The next user response must be a
case-insensitive exact match for one listed option to be accepted. For example, `approve and merge` maps to
`Approve and merge`, but `approve`, `yes`, and `go ahead` do not.

**CRITICAL:** Empty response = no selection = GATE REJECTED. Re-present entire gate.
Conversational signals ("continue", "ok", "yes", "proceed", "go ahead") are NOT valid approvals.

**Gate result detection:**
- Structured or Codex verbal response empty or null → GATE REJECTED
- Structured or Codex verbal response is not a case-insensitive exact match for a presented option → GATE REJECTED
- Structured or Codex verbal response is a case-insensitive exact match for a presented option → GATE ACCEPTED

Codex verbal approval matching must use exact option matching after case-folding only:
```bash
SELECTED_OPTION=""
for OPTION in "${APPROVAL_OPTIONS[@]}"; do
  if [[ "${USER_RESPONSE,,}" == "${OPTION,,}" ]]; then
    SELECTED_OPTION="$OPTION"
    break
  fi
done
if [[ -z "$SELECTED_OPTION" ]]; then
  echo "GATE REJECTED: response must exactly match one presented option"
fi
```

**If GATE REJECTED:** Re-display full context and re-invoke the structured approval tool. Do NOT proceed to Step 13.

**If "Approve and merge" selected:**
- Verify squash precondition — read marker and confirm it starts with `squashed:`:
  ```bash
  MARKER_VALUE=$("${CAT_PLUGIN_ROOT}/client/bin/read-session-marker" "${WORKTREE_PATH}" "${ISSUE_ID}" 2>/dev/null || echo "")
  if [[ ! "$MARKER_VALUE" =~ ^squashed: ]]; then
    echo "ERROR: Cannot approve — squash marker missing or invalid. Current: '${MARKER_VALUE}'. Return to Step 11."
    # Do NOT write 'approved'. Return to Step 11.
  fi
  ```
  If check fails: return to Step 11. Do NOT write `approved` or proceed to Step 13.
- Write approval marker:
  ```bash
  "${CAT_PLUGIN_ROOT}/client/bin/write-session-marker" "${WORKTREE_PATH}" "${ISSUE_ID}" "approved"
  ```
- Set `APPROVAL_MARKER=true`. Continue to Step 13.

**If "Fix remaining concerns" selected:**

Write invalidation marker FIRST (before spawning fix subagent):
```bash
"${CAT_PLUGIN_ROOT}/client/bin/write-session-marker" "${WORKTREE_PATH}" "${ISSUE_ID}" "approved:invalidated"
```

**Iteration cap:** Track `FIX_ITERATION` (starts at 1, max 3). After 3 iterations with MEDIUM+ concerns still
remaining, offer only: ["Approve and merge (with known concerns)", "Request changes", "Abort"].

1. Extract MEDIUM+ concerns
2. Spawn `cat:work-execute` subagent to fix each concern. Pass `ISSUE_PATH` explicitly:
   ```
   Task tool:
     description: "Fix remaining concerns for ${ISSUE_ID}"
     subagent_type: "cat:work-execute"
     prompt: |
       Fix each MEDIUM+ concern. Commit with same type as primary implementation.
       SCOPE RESTRICTION: Only modify files related to listed concerns. One concern per commit.
       ISSUE_ID: ${ISSUE_ID}
       ISSUE_PATH: ${ISSUE_PATH}
       WORKTREE_PATH: ${WORKTREE_PATH}
       TARGET_BRANCH: ${TARGET_BRANCH}
       ## Concerns to fix
       ${MEDIUM_PLUS_CONCERNS}
   ```
3. Re-squash ALL commits (MANDATORY, M560): invoke `cat:git-squash` before re-running stakeholder review.
4. Re-run stakeholder review on the newly squashed HEAD. The persisted review result `reviewed_head_sha` must match
   the current HEAD before the approval gate can be presented:
   `Skill("cat:stakeholder-review", "${ISSUE_ID} ${WORKTREE_PATH} ${CAUTION} ${TARGET_BRANCH} ${ALL_COMMITS_COMPACT}")`
5. Increment `FIX_ITERATION`. Return to Step 11.

**If changes requested:** Return to user with feedback for iteration. Return status:
```json
{
  "status": "CHANGES_REQUESTED",
  "issue_id": "${ISSUE_ID}",
  "feedback": "user feedback text"
}
```
Return `{"status": "CHANGES_REQUESTED", "issue_id": "${ISSUE_ID}", "feedback": "user feedback text"}`

**If aborted:** Clean up and return ABORTED status:
```json
{
  "status": "ABORTED",
  "issue_id": "${ISSUE_ID}",
  "message": "User aborted merge"
}
```
Return `{"status": "ABORTED", "issue_id": "${ISSUE_ID}", "message": "User aborted merge"}`

## Step 13: Merge Phase

```bash
"${CAT_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase merging
```
If banner fails: STOP with error. Do NOT skip.

**Pre-merge approval verification (when trust != "high"):**

Read the durable marker to verify approval survives context compaction:
```
if TRUST != "high":
    MARKER_VALUE=$("${CAT_PLUGIN_ROOT}/client/bin/read-session-marker" "${WORKTREE_PATH}" "${ISSUE_ID}" 2>/dev/null | tail -1)
    if APPROVAL_MARKER != true and MARKER_VALUE != "approved":
        # Re-present full approval gate context, then re-invoke the structured approval tool:
        Structured approval tool: "Ready to merge ${ISSUE_ID} to ${TARGET_BRANCH}?"
          options: ["Approve and merge", "Abort"]
        # On "Abort": return ABORTED. On "Approve and merge": set APPROVAL_MARKER=true, continue.
```

### Post-Approval Target Advancement

After the approval marker is written, target-branch movement is handled as part of merge execution, not as a reason to
restart stakeholder review. If `${TARGET_BRANCH}` advances after approval and the issue branch must be rebased before
it can fast-forward merge:

- Do not compare the post-rebase HEAD to the persisted `reviewed_head_sha` for the purpose of blocking merge.
- Do not rerun `cat:stakeholder-review` solely because the rebase changed the commit SHA.
- Do not overwrite the durable `approved` marker with a new `squashed:<hash>` marker during this post-approval path.
- Preserve the approval only when the rebase is mechanical: no conflicts, no manual edits, and no semantic change to
  the approved patch.
- If the rebase has conflicts, requires manual edits, changes the approved patch semantics, or introduces any
  implementation change beyond replaying the approved commits onto the newer target, write `approved:invalidated` and
  return to the appropriate review or approval path.

### Execute Merge

Capture pre-merge tip before invoking the tool:
```bash
PRE_MERGE_TIP=$(git -C "${CAT_PROJECT_DIR}" rev-parse "${TARGET_BRANCH}" 2>/dev/null)
```

Check idempotency guards:
```bash
git worktree list --porcelain | grep -qxF "worktree ${WORKTREE_PATH}" && WORKTREE_EXISTS=true || WORKTREE_EXISTS=false
git show-ref --verify --quiet "refs/heads/${BRANCH}" && BRANCH_EXISTS=true || BRANCH_EXISTS=false
```

**Idempotency cases** (verify merged issue state without relying on commit messages):

- `WORKTREE_EXISTS=false` and `BRANCH_EXISTS=false`: check `TARGET_BRANCH` for the issue's `index.json` with
  `"status": "closed"` and `"resolution": "implemented"`. If confirmed: return synthesized SUCCESS and skip the
  post-merge verification block because the merge already completed in an earlier attempt. If not: return FAILED.
- `WORKTREE_EXISTS=false` and `BRANCH_EXISTS=true`: check `TARGET_BRANCH` for closed implemented issue state. If
  confirmed: delete orphaned branch, return synthesized SUCCESS, and skip the post-merge verification block. If not:
  return FAILED — do NOT delete branch.
- `WORKTREE_EXISTS=true` and `BRANCH_EXISTS=false`: check `TARGET_BRANCH` for closed implemented issue state. If
  confirmed: remove orphaned worktree, return synthesized SUCCESS, and skip the post-merge verification block. If not:
  return FAILED — do NOT remove worktree.

Otherwise, invoke the merge tool and capture its JSON output:
```bash
STABLE_MERGE_PLUGIN_ROOT="${CAT_PLUGIN_ROOT}"
PROJECT_RUNTIME_ROOT="${CAT_PROJECT_DIR}/client/distribution/target/runtime/${CAT_RUNTIME}"
if [[ "${STABLE_MERGE_PLUGIN_ROOT}" == "${WORKTREE_PATH}"* ]]; then
  if [[ -x "${PROJECT_RUNTIME_ROOT}/client/bin/merge-and-cleanup" ]]; then
    STABLE_MERGE_PLUGIN_ROOT="${PROJECT_RUNTIME_ROOT}"
  else
    echo "ERROR: merge cleanup runtime is inside ${WORKTREE_PATH}, and no stable project runtime exists at ${PROJECT_RUNTIME_ROOT}." >&2
    echo "Re-run merge from a stable CAT runtime outside the worktree before cleanup." >&2
    exit 1
  fi
fi

MERGE_RESULT=$("${STABLE_MERGE_PLUGIN_ROOT}/client/bin/merge-and-cleanup" \
  "${CAT_PROJECT_DIR}" "${ISSUE_ID}" "${CAT_SESSION_ID}" "${TARGET_BRANCH}" --worktree "${WORKTREE_PATH}" --merge-only
)
```

| Output | Action |
|--------|--------|
| `"status": "success"` | Continue to post-merge verification |
| `"status": "error"`: branch diverged | Rebase onto target, preserve approval for mechanical rebase, then retry |
| `"status": "error"`: dirty worktree | Commit/stash changes first |

**On any merge error that requires manual intervention:** Invalidate approval marker, then return FAILED:
```bash
"${CAT_PLUGIN_ROOT}/client/bin/write-session-marker" "${WORKTREE_PATH}" "${ISSUE_ID}" "approved:invalidated"
```

### Post-Merge Verification Before Cleanup (BLOCKING)

Run this block only after invoking `merge-and-cleanup --merge-only` in the current Step 13 attempt. Idempotency cases
that return synthesized SUCCESS already verified the target branch's closed issue state and must not run this block
without current-session `MERGE_RESULT` JSON.

```bash
POST_MERGE_TIP=$(git -C "${CAT_PROJECT_DIR}" rev-parse "${TARGET_BRANCH}" 2>/dev/null)
if [[ -z "${POST_MERGE_TIP}" ]]; then echo "ERROR: Cannot resolve ${TARGET_BRANCH}" >&2; exit 1; fi
if [[ "${POST_MERGE_TIP}" == "${PRE_MERGE_TIP}" ]]; then
  echo "ERROR: ${TARGET_BRANCH} tip unchanged — merge did not occur. Re-run Step 13." >&2; exit 1
fi
MERGED_COMMIT=$(echo "${MERGE_RESULT}" | grep -o '"merged_commit"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | head -1 | sed 's/.*"merged_commit"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
if [[ -z "${MERGED_COMMIT}" ]]; then
  echo "ERROR: merge-and-cleanup did not report merged_commit. Re-run Step 13." >&2; exit 1
fi
if [[ "${POST_MERGE_TIP}" != "${MERGED_COMMIT}"* ]]; then
  echo "ERROR: ${TARGET_BRANCH} tip ${POST_MERGE_TIP} does not match merged commit ${MERGED_COMMIT}. Re-run Step 13." >&2
  exit 1
fi
```

If merge verification fails: STOP — do NOT clean up the worktree or invoke `work-complete`. Re-execute Step 13.

### Cleanup Merged Issue

Only after merge verification succeeds, run cleanup from the stable merge runtime:

```bash
CLEANUP_RESULT=$("${STABLE_MERGE_PLUGIN_ROOT}/client/bin/merge-and-cleanup" \
  "${CAT_PROJECT_DIR}" "${ISSUE_ID}" "${CAT_SESSION_ID}" "${TARGET_BRANCH}" \
  --worktree "${WORKTREE_PATH}" --cleanup-only --expected-commit "${MERGED_COMMIT}"
)
```

Cleanup is idempotent. If `MERGE_RESULT` succeeded but cleanup fails, report the issue as merged with cleanup
incomplete. Do NOT retry the merge. Re-run only the cleanup command after resolving the cleanup error.

If cleanup reports an unsafe runtime/cwd/plugin-root path inside `${WORKTREE_PATH}`, STOP and rerun cleanup from a
stable CAT runtime outside the worktree. Do not manually remove the worktree first; the cleanup tool verifies the
target branch commit before deleting artifacts.

## Step 14: Return Success

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

**User rejects approval gate:** The gate was NOT answered. Re-present full context (diff, commit summary, goal, E2E,
concerns) and re-invoke the structured approval tool in the NEXT response. Unknown consent = No consent = STOP and
re-present.

**Approval gate interruption:** Any non-option message = GATE REJECTED. Answer the question, then re-present
the full gate with all options. Only explicit structured input option selection or a Codex verbal response that is a
case-insensitive exact match for a presented option counts as valid approval.

Invalid signals: "continue", "ok", "yes", "proceed", "go ahead" — these are NOT approvals. See
`plugin/rules/common/approval-gate-protocol.md` for the authoritative list.

## Error Handling

Classify errors in order:
1. User explicitly aborted/rejected → **permanent**
2. Error contains `index.lock`, `shallow.lock`, `Unable to create.*lock` → **transient**
3. Error contains `Connection refused`, `Connection timed out`, `SSL`, `Could not resolve host` → **transient**
4. Error contains `CONFLICT` or `merge conflict` → **permanent**
5. Error references missing branch (`not found`, `unknown revision`) → **permanent**
6. Error references corruption (`bad object`, `corrupt`, `fatal: packed-refs`) → **permanent**
7. None of the above → **permanent** (default to releasing lock)

- **Transient**: hold lock (user can retry)
- **Permanent**: release lock: `"${CAT_PLUGIN_ROOT}/client/bin/issue-lock" release "${ISSUE_ID}" "$CAT_SESSION_ID"`

```json
{
  "status": "FAILED",
  "phase": "squash|rebase|merge",
  "message": "actual error message",
  "issue_id": "${ISSUE_ID}",
  "lock_retained": true
}
```

`"lock_retained": true` for transient errors, `false` for permanent (lock was released).
