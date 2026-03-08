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
- **Step 9 (sub-step): Skill-Builder Review** — always invoke `cat:skill-builder` for modified skill or
  command files before presenting the approval gate

## Arguments and Configuration

`<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <commits_json> <trust> <verify>`

```bash
read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH COMMITS_JSON TRUST VERIFY <<< "$ARGUMENTS"
PLAN_MD="${ISSUE_PATH}/PLAN.md"
```

Return JSON: `{"status": "SUCCESS|ABORTED|CHANGES_REQUESTED|FAILED", "issue_id": "...", "commits": [...], "files_changed": N, "merged": true}`

## Step 7: Squash Commits by Topic Before Review (MANDATORY)

**MANDATORY: Delegate rebase, squash, and STATE.md closure verification to a squash subagent.** This step must not be
skipped — the approval gate (Step 9) checks that squash was executed and blocks proceeding if it was not.
This keeps the parent agent context lean by offloading git operations to a dedicated haiku-model subagent.

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

- **SUCCESS**: Extract `commits` array for use at the approval gate. Continue to Step 8.
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
- Resolve each conflict
- Stage resolved files and continue the rebase
- Delete the backup branch created by cat:git-rebase-agent after resolution
- Proceed to Step 8c (Impact Analysis)

**If rebase reports OK:**
- Delete the backup branch created by cat:git-rebase-agent
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
if [[ -z "$NEW_FORK_POINT" ]]; then
  echo "WARNING: Could not determine fork point after rebase; skipping impact analysis" >&2
  # Continue to Step 9 without analysis
fi
```

If `NEW_FORK_POINT` equals `OLD_FORK_POINT`, the target branch had no new commits — skip impact
analysis and continue to Step 9.

Otherwise, compute the session analysis directory so the analysis file is written outside the worktree
and never committed, then invoke the impact analysis skill:

```bash
# Pass session dir so analysis file is written outside the worktree and never committed
ENCODED_PROJECT_DIR=$(printf '%s' "${CLAUDE_PROJECT_DIR}" | sed 's|/|%2F|g; s| |%20|g')
SESSION_ANALYSIS_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/.claude/cat"
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
CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/cat-config.json"
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

Before presenting the approval gate, verify that Step 7 (Squash by Topic) was executed. Squashing by topic
may produce 1-2 commits (e.g., one implementation commit + one config commit), so commit count alone does not
determine completion. Instead, confirm that `/cat:git-squash` was invoked in Step 7.

**If Step 7 was skipped:** STOP — return to Step 7 and complete the squash by topic before proceeding.

**If Step 7 was completed:** Proceed to approval gate below.

### Pre-Gate Skill-Builder Review (MANDATORY — BLOCKING)

**MANDATORY:** When the issue modifies files in `plugin/skills/` or `plugin/commands/`, invoke `/cat:skill-builder`
to review each modified skill or command file before presenting the approval gate. This step must not be skipped —
do not proceed to the approval gate without completing skill-builder review for all modified skill or command files.

```bash
# Check whether any skill or command files were modified
git diff --name-only "${TARGET_BRANCH}..HEAD" | grep -E '^plugin/(skills|commands)/'
```

**If skill or command files were modified:** Invoke `/cat:skill-builder` with the path to each modified skill or
command. Review the output and address any priming issues or structural problems it identifies before proceeding
to the approval gate.

**If no skill or command files were modified:** Skip this check and proceed to the approval gate below.

**trust == "high":** Skip approval gate, continue to Step 10.

**trust == "low" or "medium":** STOP for user approval. Do NOT proceed to merge automatically.

Check for prior direct approval: scan conversation for user messages containing both "approve" and "merge". If found,
skip AskUserQuestion and proceed to Step 10 (PreToolUse hook will recognize the direct message as approval).

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

4. **Display execution summary** (commits count, files changed)
5. **Display E2E testing summary** — what tests ran, what they verified, results (if skipped: state explicitly)
6. **Display ALL stakeholder concerns** regardless of severity — do NOT suppress MEDIUM or LOW

For fixed concerns: `Skill("cat:stakeholder-concern-box-agent", "${SEVERITY} ${STAKEHOLDER} ${CONCERN_DESCRIPTION} ${FILE_LOCATION}")`
For deferred: `Skill("cat:stakeholder-concern-box-agent", "${SEVERITY} ${STAKEHOLDER} ${CONCERN_DESCRIPTION} [deferred: benefit=${BENEFIT}, cost=${COST}, threshold=${THRESHOLD}] ${FILE_LOCATION}")`

7. **Recap last user change request** — scan the conversation for the most recent user message
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

Invoke AskUserQuestion ONLY AFTER all seven items above are output in the current turn:
- If MEDIUM+ concerns: options = ["Approve and merge", "Fix remaining concerns", "Request changes", "Abort"]
- If no concerns or only LOW: options = ["Approve and merge", "Request changes", "Abort"]

**CRITICAL:** Wait for explicit selection. Empty `toolUseResult.answers` = no selection = re-present gate.
Unknown consent = No consent = STOP. Fail-fast principle applies.

**If approved:** Continue to Step 10

**If "Fix remaining concerns" selected:**
1. Extract MEDIUM+ concerns (severity, description, location, recommendation, detail_file)
2. Spawn `cat:work-execute` subagent: fix each MEDIUM+ concern in `${WORKTREE_PATH}`, commit with
   same type as primary implementation (e.g., `bugfix:`), return JSON with commits and files_changed
3. **MANDATORY: Re-run stakeholder review** after fixes:
   `Skill("cat:stakeholder-review-agent", "${ISSUE_ID} ${WORKTREE_PATH} ${VERIFY} ${ALL_COMMITS_COMPACT}")`
   The review MUST re-run to verify concerns resolved and detect new concerns introduced by fixes
4. Return to Step 9 approval gate with updated results

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

If `WORKTREE_EXISTS=false` and `BRANCH_EXISTS=false`, cleanup was already completed in a previous run —
synthesize a success result with `"merge_commit": "already merged — read from: git -C /workspace log --format=%H -1 ${TARGET_BRANCH}"` and skip to Step 11.

If `WORKTREE_EXISTS=false` and `BRANCH_EXISTS=true`, delete the orphaned branch and synthesize success.

If `WORKTREE_EXISTS=true` and `BRANCH_EXISTS=false`, remove the orphaned worktree and synthesize success.

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

```bash
# Verify the merge commit is reachable from TARGET_BRANCH
# git -C is intentional here: the worktree is already removed at this point,
# so we must run from the main project directory. Chain both operations:
SQUASH_HASH=$(git -C "${CLAUDE_PROJECT_DIR}" rev-parse HEAD 2>/dev/null) && \
MERGED=$(git -C "${CLAUDE_PROJECT_DIR}" branch --contains "${SQUASH_HASH}" 2>/dev/null \
  | grep -c "${TARGET_BRANCH}" || true) && \
if [[ "${MERGED}" -eq 0 ]]; then
  echo "ERROR: Merge not confirmed — ${SQUASH_HASH} is not reachable from ${TARGET_BRANCH}."
  echo "The merge phase may have failed silently. Do NOT invoke work-complete."
  echo "Re-run Step 10 using the Task tool."
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
3. Attempt lock release: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${ISSUE_ID}" "$CLAUDE_SESSION_ID"`
4. Return FAILED status with actual error details

```json
{
  "status": "FAILED",
  "phase": "squash|rebase|merge",
  "message": "actual error message",
  "issue_id": "${ISSUE_ID}"
}
```
