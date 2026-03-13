<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Phase: Confirm

Confirm phase for `/cat:work`. Verifies that PLAN.md post-conditions were implemented before
stakeholder quality review. Spawns a verify subagent, handles fix iteration if criteria are missing.

## Arguments Format

```
<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <execution_commits_json> <files_changed> <trust> <verify>
```

| Position | Name | Example |
|----------|------|---------|
| 1 | catAgentId | agent ID passed through from parent |
| 2 | issue_id | `2.1-issue-name` |
| 3 | issue_path | `/workspace/.cat/issues/v2/v2.1/issue-name` |
| 4 | worktree_path | `${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/2.1-issue-name` |
| 5 | issue_branch | `2.1-issue-name` |
| 6 | target_branch | `v2.1` |
| 7 | execution_commits_json | JSON array of commit objects from the implement phase |
| 8 | files_changed | integer count of files changed |
| 9 | trust | `medium` |
| 10 | verify | `changed` |

## Output Contract

Return JSON when complete:

```json
{
  "status": "COMPLETE|PARTIAL|INCOMPLETE",
  "execution_commits_json": "[{...}]",
  "files_changed": 5
}
```

## Configuration

Parse arguments and display the **Confirming phase** banner in a chained call:

```bash
read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH EXECUTION_COMMITS_JSON FILES_CHANGED TRUST VERIFY <<< "$ARGUMENTS" && \
PLAN_MD="${ISSUE_PATH}/PLAN.md" && \
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase confirming
```

## Step 4: Confirm Implementation

**This step confirms PLAN.md post-conditions were implemented before stakeholder quality review.**

**If the command fails or produces no output**, STOP immediately:
```
FAIL: progress-banner launcher failed for phase 'confirming'.
The jlink image may not be built. Run: mvn -f hooks/pom.xml verify
```
Do NOT skip the banner or continue without it. **The banner must run regardless of the value of
VERIFY — even when `VERIFY == "none"`. The banner and the skip check are independent; skipping
verification does not authorize skipping the banner.**

### Skip Verification if Configured

Skip **only the verification steps below** if: `VERIFY == "none"`

If skipping, output: "Verification skipped (verify: ${VERIFY})"

The banner (above) is NOT part of the verification steps and must always run.

### Delegate Verification to Subagent

Spawn a verify subagent to check post-conditions and run E2E tests. The verify subagent writes detailed
analysis to files and returns only a compact JSON summary.

**Subagent type is mandatory:** Always spawn with `subagent_type: "cat:work-verify"`. Do NOT use
`"general-purpose"` or any other type — the `cat:work-verify` type loads required skill context that
the prompt alone cannot substitute.

```
Task tool:
  description: "Verify: check post-conditions and E2E for ${ISSUE_ID}"
  subagent_type: "cat:work-verify"
  prompt: |
    Verify the implementation for issue ${ISSUE_ID}.

    ## Issue Configuration
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    BRANCH: ${BRANCH}
    TARGET_BRANCH: ${TARGET_BRANCH}
    PLAN_MD_PATH: ${PLAN_MD}

    Read the Goal and Post-conditions sections from PLAN_MD_PATH directly.
    Do NOT ask the main agent to provide this content — it is authoritative in PLAN.md.

    ## Execution Result
    Commits: ${execution_commits_json}
    Files changed: ${files_changed}

    ## Your Task
    1. Invoke the verify-implementation skill to check all PLAN.md post-conditions:
       ```
       Skill tool:
         skill: "cat:verify-implementation-agent"
         args: |
           {
             "issue_id": "${ISSUE_ID}",
             "issue_path": "${ISSUE_PATH}",
             "worktree_path": "${WORKTREE_PATH}",
             "execution_result": {
               "commits": ${execution_commits_json},
               "files_changed": ${files_changed}
             }
           }
       ```
    2. Run E2E testing appropriate to the issue type:
       - Determine issue type by reading the `## Type` field in ${ISSUE_PATH}/PLAN.md. Use the FIRST
         occurrence of a line matching `^## Type` in the file — the authoritative field is always near
         the top of PLAN.md, never inside a Sub-Agent Waves section. Do NOT infer type from commit
         messages, issue ID naming, or other heuristics.
       - For `feature`, `bugfix`, `refactor`, and `performance` issue types: run runtime E2E tests
         using worktree artifacts.
       - For `docs` and `config` issue types only: set e2e status to SKIPPED. Any issue type not
         in this exhaustive list must be treated as requiring runtime E2E tests.
       - E2E isolation: use worktree artifacts (${WORKTREE_PATH}/client/target/jlink/bin/ and
         ${WORKTREE_PATH}/plugin/scripts/), never the cached plugin installation
       - Runtime invocation required — do NOT substitute file inspection for running the artifact
    3. Create the verify output directory and write detailed analysis files:
       ```bash
       source "${CLAUDE_PLUGIN_ROOT}/scripts/cat-env.sh"
       VERIFY_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}"
       mkdir -p "${VERIFY_DIR}"
       ```
       - Write criterion-level details to: ${VERIFY_DIR}/criteria-analysis.json
       - Write E2E test output/evidence to: ${VERIFY_DIR}/e2e-test-output.json
    4. Return compact JSON only — do NOT include build logs or file contents in your response

    ## Return Format
    ```json
    {
      "status": "COMPLETE|PARTIAL|INCOMPLETE",
      "criteria": [
        {
          "name": "criterion text from PLAN.md",
          "status": "Done|Partial|Missing",
          "explanation": "brief one-line explanation",
          "detail_file": "${VERIFY_DIR}/criteria-analysis.json"
        }
      ],
      "e2e": {
        "status": "PASSED|FAILED|SKIPPED",
        "explanation": "brief one-line explanation",
        "detail_file": "${VERIFY_DIR}/e2e-test-output.json"
      }
    }
    ```
    Status values:
    - COMPLETE: All criteria Done, E2E passed (or skipped for docs/config issues)
    - PARTIAL: Some criteria Partial, none Missing, E2E passed or skipped
    - INCOMPLETE: Any criteria Missing, or E2E failed
```

### Handle Verification Result

Parse the compact JSON returned by the verify subagent. Do NOT read the detail files — they are for
fix subagents only. The main agent must never read `criteria-analysis.json` or `e2e-test-output.json`
directly; doing so is not a substitute for subagent delegation.

Extract detail file paths for use in fix subagent prompts:
- Collect the `detail_file` value from each entry in `criteria[]`
- Collect `e2e.detail_file`
- Combine into a list: this is `DETAIL_FILE_PATHS` (used as `${detail_file_paths_from_compact_json}` in subagent prompts)

Also store the parsed `execution_commits_json` (from `EXECUTION_COMMITS_JSON` argument) as a
mutable variable `CURRENT_COMMITS_JSON`. This is the variable that step 5 updates and that
re-spawned verify prompts must use — always use `CURRENT_COMMITS_JSON` (never the original
`EXECUTION_COMMITS_JSON`) when constructing re-verification prompts after any fix iteration.

**If the JSON is malformed, unparseable, or the `status` field contains a value other than
`COMPLETE`, `PARTIAL`, or `INCOMPLETE`**, STOP immediately:
```
FAIL: verify subagent returned invalid JSON or unrecognized status.
Cannot proceed to review without verified post-conditions.
```
Do NOT default to COMPLETE or any other status. Do NOT continue to the next phase.

**If status is COMPLETE:**
- Output: "All post-conditions verified - proceeding to review"
- Return success

Initialize loop: `VERIFY_ITERATION=0`

**VERIFY_ITERATION is initialized exactly once here, before any branch is taken — regardless of whether
the first result is PARTIAL or INCOMPLETE. Do NOT re-initialize inside any branch or loop iteration.**

**If status is PARTIAL:**
- STOP — do not proceed to stakeholder review
- Enter the fix loop below, subject to the `VERIFY_ITERATION < 2` cap
- See also: [PARTIAL Verification Result](#partial-verification-result) in Rejection Handling

**If status is INCOMPLETE (Missing criteria or E2E failed):**
- Enter the fix loop below, subject to the `VERIFY_ITERATION < 2` cap

**While status is INCOMPLETE or PARTIAL, and VERIFY_ITERATION < 2:**

1. Increment: `VERIFY_ITERATION++`

   **VERIFY_ITERATION must never be reset to 0 after initialization.** The counter is initialized
   exactly once before the loop and incremented on every iteration. There is no condition under which
   resetting or re-initializing the counter is valid.

2. Extract missing/failed items from the compact JSON:
   - Missing criteria: items in `criteria[]` with `status: "Missing"`
   - Partial criteria: items in `criteria[]` with `status: "Partial"`
   - Failed E2E: `e2e.status == "FAILED"`

3. Spawn a **planning subagent** to revise PLAN.md with fix steps.

   **If the planning subagent returns `"status": "FAILED"`**, STOP the fix loop immediately. Do NOT
   spawn the implementation subagent. Note the planning failure in the return JSON and proceed to the
   next phase with remaining gaps documented. The current VERIFY_ITERATION still counts — do NOT
   retry the same iteration.

   **If the planning subagent returns `"status": "SUCCESS"` but `fix_steps` is empty, missing, or
   contains only whitespace/blank entries**, treat as FAILED — STOP the fix loop and proceed to the
   next phase. A SUCCESS with no actionable fix steps means the planning subagent produced nothing
   actionable.

   Spawn the planning subagent:
   ```
   Task tool:
     description: "Plan fixes for missing post-conditions (iteration ${VERIFY_ITERATION})"
     subagent_type: "general-purpose"
     model: "sonnet"
     prompt: |
       Revise PLAN.md to add fix steps for the following missing post-conditions.

       ## Issue Configuration
       ISSUE_ID: ${ISSUE_ID}
       ISSUE_PATH: ${ISSUE_PATH}
       WORKTREE_PATH: ${WORKTREE_PATH}

       ## Missing Post-conditions
       ${missing_criteria_compact_json}

       ## Detail Files (pass to implementation subagent — do NOT return their contents in your response)
       ${detail_file_paths_from_compact_json}

       ## Instructions
       - Read ${ISSUE_PATH}/PLAN.md to understand the current plan
       - Read the detail files to understand what specifically failed. Do NOT include file contents
         in your JSON response — use them only to inform what fix steps to write
       - Add new items to the Sub-Agent Waves section of PLAN.md
       - Each new item must address exactly one missing criterion
       - Do NOT remove or alter existing items — only append new items to the last wave (or steps section)
       - Do NOT modify any existing PLAN.md fields — especially `## Type`, `## Goal`, existing post-conditions,
         or any section other than the Sub-Agent Waves / steps list
       - Sub-Agent Wave items MUST be concrete, actionable implementation steps that modify source files,
         add tests, or run migrations. Read-only checks, echo commands, or no-op commands that do not
         modify files do NOT satisfy missing implementation criteria and must not be used as fix steps
       - Do NOT add commentary, interpretive qualifications, or text claiming existing criteria are
         already satisfied or equivalent to something else
       - Commit the revised PLAN.md using git from ${WORKTREE_PATH} (not the main workspace):
         `cd "${WORKTREE_PATH}" && git add "${ISSUE_PATH}/PLAN.md" && git commit -m "planning: add fix items for missing criteria (iteration ${VERIFY_ITERATION})"`
       - All git operations MUST use ${WORKTREE_PATH} as the working directory so commits land on the issue
         branch, not on the main workspace branch
       - After committing, verify the commit is present: `cd "${WORKTREE_PATH}" && git log --oneline -1`
         must show the planning commit. If git commit failed (exit code non-zero), return status: FAILED

       ## Return Format
       ```json
       {
         "status": "SUCCESS|FAILED",
         "fix_steps": ["step text 1", "step text 2"],
         "plan_md_path": "${ISSUE_PATH}/PLAN.md"
       }
       ```
   ```

4. Spawn an **implementation subagent** to execute the fix steps:
   ```
   Task tool:
     description: "Implement fixes for missing post-conditions (iteration ${VERIFY_ITERATION})"
     subagent_type: "cat:work-execute"
     prompt: |
       Fix the following missing post-conditions for issue ${ISSUE_ID}.

       ## Issue Configuration
       ISSUE_ID: ${ISSUE_ID}
       ISSUE_PATH: ${ISSUE_PATH}
       WORKTREE_PATH: ${WORKTREE_PATH}
       BRANCH: ${BRANCH}
       TARGET_BRANCH: ${TARGET_BRANCH}

       ## Fix Steps (from revised PLAN.md)
       ${fix_steps_from_planning_result}

       ## Detail Files (read these to understand what needs fixing)
       ${detail_file_paths_from_compact_json}

       ## Instructions
       - Work ONLY in the worktree at ${WORKTREE_PATH}
       - Read the detail files to understand exactly what failed
       - Implement each fix step from the revised PLAN.md
       - Commit fixes using the same commit type as the primary implementation
         (e.g., `bugfix:`, `feature:`). Do NOT use `planning:` as a commit type.
       - Return JSON status when complete

       ## Return Format
       ```json
       {
         "status": "SUCCESS|PARTIAL|FAILED",
         "commits": [{"hash": "...", "message": "...", "type": "..."}],
         "files_changed": N,
         "criteria_addressed": N
       }
       ```
   ```

   **If the implementation subagent returns `"status": "FAILED"`**, STOP the fix loop immediately.
   Before exiting: if the implementation subagent's result includes any `commits` (partial work before
   the failure), append those commits to `CURRENT_COMMITS_JSON` so downstream phases have the complete
   commit history. Then proceed to the next phase with remaining gaps documented. The current
   VERIFY_ITERATION still counts. Do NOT proceed to steps 5-6.

   **If the implementation subagent returns `"status": "PARTIAL"` or `"SUCCESS"`**, proceed to steps 5-6.
   Steps 5 AND 6 are MANDATORY regardless of PARTIAL or SUCCESS — always update CURRENT_COMMITS_JSON
   with whatever commits were produced and always re-verify. Do NOT skip step 6 on the grounds that
   implementation returned SUCCESS; re-verification is required to confirm and record the outcome.

**Steps 3, 4, 5, and 6 are STRICTLY SEQUENTIAL — never spawn step 4 until step 3 completes, never
spawn step 6 until step 5 completes. Do NOT make parallel Task tool calls between any of these steps.**

5. Append the implementation subagent's commits to `CURRENT_COMMITS_JSON` and add its `files_changed`
   to the running total. This MUST happen before re-spawning the verify subagent. Never use the original
   `EXECUTION_COMMITS_JSON` argument in re-verification prompts — always use the updated `CURRENT_COMMITS_JSON`.

6. Re-spawn the verify subagent by constructing a NEW prompt from the template (do NOT reuse or re-send
   the original rendered prompt string). Substitute the CURRENT value of `CURRENT_COMMITS_JSON` (which
   now includes fix commits) and the updated `files_changed` total into the prompt before spawning.
   The re-rendered prompt must reflect the post-fix state. Note: the planning subagent appended fix steps
   only to the `## Sub-Agent Waves` section of PLAN.md — never to the post-conditions. The verify subagent
   evaluates the same post-conditions as the initial invocation.

**If still INCOMPLETE or PARTIAL after 2 iterations:**
- Note the remaining gaps in the return JSON
- The iteration cap is exhausted — proceed to the next phase with gaps documented. This supersedes the general
  PARTIAL prohibition: once both fix iterations are consumed, surfacing gaps to the review phase is correct.
  Do NOT enter another fix loop or spawn more fix subagents.

## Rejection Handling

### PARTIAL Verification Result

When the verify subagent returns `"status": "PARTIAL"`, some post-conditions are unmet but none are
fully missing. STOP — do not proceed to the review phase. Re-enter the fix loop described above,
subject to the same `VERIFY_ITERATION < 2` cap. The iteration counter applies equally to PARTIAL
re-entries — it is never reset between PARTIAL and INCOMPLETE transitions.

Do NOT carry partially-met post-conditions into the review phase — the stakeholder review assumes the
implementation is complete. **Exception:** after both fix iterations are exhausted (VERIFY_ITERATION >= 2),
proceed to the next phase with gaps documented (see fix loop above). The iteration cap supersedes this rule.
