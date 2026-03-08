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
| 3 | issue_path | `/workspace/.claude/cat/issues/v2/v2.1/issue-name` |
| 4 | worktree_path | `${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat/worktrees/2.1-issue-name` |
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
Do NOT skip the banner or continue without it.

### Skip Verification if Configured

Skip if: `VERIFY == "none"`

If skipping, output: "Verification skipped (verify: ${VERIFY})"

### Delegate Verification to Subagent

Spawn a verify subagent to check post-conditions and run E2E tests. The verify subagent writes detailed
analysis to files and returns only a compact JSON summary:

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
       - For feature/bugfix/refactor/performance issues: run runtime E2E tests using worktree artifacts
       - For docs/config issues (no runtime behavior changes): set e2e status to SKIPPED
       - E2E isolation: use worktree artifacts (${WORKTREE_PATH}/client/target/jlink/bin/ and
         ${WORKTREE_PATH}/plugin/scripts/), never the cached plugin installation
       - Runtime invocation required — do NOT substitute file inspection for running the artifact
    3. Create the verify output directory and write detailed analysis files:
       ```bash
       source "${CLAUDE_PLUGIN_ROOT}/scripts/cat-env.sh"
       VERIFY_DIR="${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/${CLAUDE_SESSION_ID}/cat/verify"
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

Parse the compact JSON returned by the verify subagent. Do NOT read the detail files — they are for fix subagents.

**If status is COMPLETE:**
- Output: "All post-conditions verified - proceeding to review"
- Return success

**If status is PARTIAL:**
- STOP — do not proceed to stakeholder review
- Re-enter the INCOMPLETE fix loop below to address unmet post-conditions before review
- See also: [PARTIAL Verification Result](#partial-verification-result) in Rejection Handling

**If status is INCOMPLETE (Missing criteria or E2E failed):**

Initialize loop: `VERIFY_ITERATION=0`

**While status is INCOMPLETE and VERIFY_ITERATION < 2:**

1. Increment: `VERIFY_ITERATION++`

2. Extract missing/failed items from the compact JSON:
   - Missing criteria: items in `criteria[]` with `status: "Missing"`
   - Failed E2E: `e2e.status == "FAILED"`

3. Spawn a **planning subagent** to revise PLAN.md with fix steps:
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

       ## Detail Files (read these to understand what failed)
       ${detail_file_paths_from_compact_json}

       ## Instructions
       - Read ${ISSUE_PATH}/PLAN.md to understand the current plan
       - Read the detail files to understand what specifically failed
       - Add new items to the Sub-Agent Waves section of PLAN.md
       - Each new item must address exactly one missing criterion
       - Do NOT remove or alter existing items — only append new items to the last wave (or steps section)
       - Commit the revised PLAN.md: `planning: add fix items for missing criteria (iteration ${VERIFY_ITERATION})`

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

5. Re-spawn the verify subagent (same prompt as initial invocation above) to re-check post-conditions.

6. Update `execution_commits_json` and `files_changed` to include the new fix commits before re-verifying.

**If still INCOMPLETE after 2 iterations:**
- Note the remaining gaps in metrics
- Continue to next phase with gaps noted

## Rejection Handling

### PARTIAL Verification Result

When the verify subagent returns `"status": "PARTIAL"`, some post-conditions are unmet but none are
fully missing. STOP — do not proceed to the review phase. Re-enter the INCOMPLETE fix loop
(described above) to address unmet post-conditions first.

Do NOT carry partially-met post-conditions into the review phase — the stakeholder review assumes the
implementation is complete.
