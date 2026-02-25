<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work With Issue: Direct Phase Orchestration

Execute all work phases (implement, confirm, review, merge) with the main agent directly orchestrating each phase.
Shows progress banners at phase transitions while maintaining clean user output.

**Architecture:** This skill is invoked by `/cat:work` after issue discovery (Phase 1). The main agent
directly orchestrates all phases:
- Implement: Spawn implementation subagent
- Confirm: Invoke verify-implementation skill
- Review: Invoke stakeholder-review skill
- Merge: Spawn merge subagent

This eliminates nested subagent spawning (which is architecturally impossible) and enables proper
skill invocation at the main agent level.

## Arguments Format

The main `/cat:work` skill invokes this with positional space-separated arguments:

```
<issue_id> <issue_path> <worktree_path> <branch> <base_branch> <estimated_tokens> <trust> <verify> <auto_remove>
```

| Position | Name | Example |
|----------|------|---------|
| 1 | issue_id | `2.1-issue-name` |
| 2 | issue_path | `/workspace/.claude/cat/issues/v2/v2.1/issue-name` |
| 3 | worktree_path | `/workspace/.claude/cat/worktrees/2.1-issue-name` |
| 4 | branch | `2.1-issue-name` |
| 5 | base_branch | `v2.1` |
| 6 | estimated_tokens | `45000` |
| 7 | trust | `medium` |
| 8 | verify | `changed` |
| 9 | auto_remove | `true` |

## Progress Banners

Progress banners are generated on-demand by invoking the ProgressBanner CLI tool.

**Phase symbols:** `○` Pending | `●` Complete | `◉` Active | `✗` Failed

**Banner pattern by phase:**
- Preparing: `◉ ○ ○ ○ ○`
- Implementing: `● ◉ ○ ○ ○`
- Confirming: `● ● ◉ ○ ○`
- Reviewing: `● ● ● ◉ ○`
- Merging: `● ● ● ● ◉`

---

## Configuration

Extract configuration from the positional ARGUMENTS string. The arguments are space-separated.
Since some paths may theoretically contain spaces but in practice CAT paths never do,
split on whitespace:

```bash
# Parse positional arguments
read ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH BASE_BRANCH ESTIMATED_TOKENS TRUST VERIFY AUTO_REMOVE <<< "$ARGUMENTS"
```

## Step 1: Display Preparing Banner

Display the **Preparing phase** banner by running:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase preparing
```

**If the command fails or produces no output**, STOP immediately:
```
FAIL: progress-banner launcher failed for phase 'preparing'.
The jlink image may not be built. Run: mvn -f client/pom.xml verify
```
Do NOT skip the banner or continue without it.

This indicates Phase 1 (prepare) has completed and work phases are starting.

## Step 2: Verify Lock Ownership

**Before any execution, verify the lock for this issue belongs to the current session.**

```bash
LOCK_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/locks/${ISSUE_ID}.lock"

if [[ -z "${CLAUDE_SESSION_ID:-}" ]]; then
  echo "ERROR: CLAUDE_SESSION_ID environment variable is not set"
  exit 1
fi

if [[ ! -f "$LOCK_FILE" ]]; then
  echo "ERROR: No lock file found for ${ISSUE_ID}. Issue was not properly prepared."
  exit 1
fi

# Extract session_id value from the lock JSON using grep/sed (no jq available)
LOCK_SESSION=$(grep -o '"session_id"[[:space:]]*:[[:space:]]*"[^"]*"' "$LOCK_FILE" | \
  head -1 | sed 's/"session_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')

if [[ "$LOCK_SESSION" == "$CLAUDE_SESSION_ID" ]]; then
  echo "OK: Lock verified for current session"
else
  echo "ERROR: Lock for ${ISSUE_ID} belongs to session ${LOCK_SESSION}, not ${CLAUDE_SESSION_ID}"
  exit 1
fi
```

If lock ownership verification fails, STOP immediately and return FAILED status. Do NOT proceed
to execution — another session owns this issue.

## Step 3: Implement Phase

Display the **Implementing phase** banner by running:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase implementing
```

**If the command fails or produces no output**, STOP immediately:
```
FAIL: progress-banner launcher failed for phase 'implementing'.
The jlink image may not be built. Run: mvn -f hooks/pom.xml verify
```
Do NOT skip the banner or continue without it.

### Read PLAN.md and Identify Skills

Read the execution steps from PLAN.md to understand what needs to be done:

```bash
# Read PLAN.md execution steps
PLAN_MD="${ISSUE_PATH}/PLAN.md"
EXECUTION_STEPS=$(sed -n '/## Execution Steps/,/^## /p' "$PLAN_MD" | head -n -1)
ISSUE_GOAL=$(sed -n '/## Goal/,/^## /p' "$PLAN_MD" | head -n -1 | tail -n +2)
```

Scan execution steps for skill references that require spawning capability:

- `/cat:shrink-doc` - Document compression (spawns compare-docs subagent)
- `/cat:compare-docs` - Document equivalence validation (spawns validation subagent)
- `/cat:stakeholder-review` - Code review (spawns reviewer subagents)

**If execution steps reference these skills**, invoke them NOW at the main agent level using the Skill tool.

Example: If PLAN.md says "Step 1: Invoke /cat:shrink-doc on file.md", then:

```
Skill tool:
  skill: "cat:shrink-doc"
  args: "path/to/file.md"
```

**Complete each skill fully before delegation.** Pre-invoked skills may have built-in
iteration loops, validation gates, or multi-step workflows. Run each skill to its documented
completion state before passing results to the implementation subagent. Do NOT pass intermediate
or failed results to the subagent for manual fixing — that bypasses the skill's quality gates.

Capture the output from these skills - the implementation subagent will need the results.

### Delegation Prompt Construction

**Pass PLAN.md execution steps verbatim without interpretive summarization.**

When constructing the delegation prompt below, include execution steps from PLAN.md exactly as written.
Do NOT add ad-hoc "Important Notes" or aggregate language that might conflict with PLAN.md's structure.

**Why:** If PLAN.md distinguishes Step 2 (path construction) from Step 3 (documentation references),
that distinction is intentional. Adding aggregate language like "Replace ALL occurrences" can prime
the subagent to treat distinct steps as a single operation, causing incomplete execution.

**Pattern:**
- ✅ Include `${EXECUTION_STEPS}` directly from PLAN.md
- ✅ Trust PLAN.md structure - distinct steps should remain distinct
- ❌ Do NOT add interpretive summaries or aggregate instructions
- ❌ Do NOT synthesize "Important Notes" that restate steps differently

### Spawn Implementation Subagent

Spawn a subagent to implement the issue:

```
Task tool:
  description: "Execute: implement ${ISSUE_ID}"
  subagent_type: "cat:work-execute"
  prompt: |
    Execute the implementation for issue ${ISSUE_ID}.

    ## Issue Configuration
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    BRANCH: ${BRANCH}
    BASE_BRANCH: ${BASE_BRANCH}
    ESTIMATED_TOKENS: ${ESTIMATED_TOKENS}
    TRUST_LEVEL: ${TRUST}

    ## Issue Goal (from PLAN.md)
    ${ISSUE_GOAL}

    ## Execution Steps (from PLAN.md)
    ${EXECUTION_STEPS}

    ## Pre-Invoked Skill Results
    [If skills were pre-invoked above, include their output here]

    ## Critical Requirements
    - Work ONLY in the worktree at ${WORKTREE_PATH}
    - Verify you are on branch ${BRANCH} before making changes
    - Follow execution steps from PLAN.md EXACTLY
    - If steps say to invoke a skill that was pre-invoked above, use the provided results
    - Update STATE.md in the SAME commit as implementation (status: closed, progress: 100%)
    - Run tests if applicable
    - Commit your changes using the commit type from PLAN.md (e.g., `feature:`, `bugfix:`, `docs:`). The commit message must follow the format: `<type>: <descriptive summary>`. Example: `feature: add user authentication with JWT tokens`. Do NOT use generic messages like 'squash commit' or 'fix'.

    ## Return Format
    Return JSON when complete:
    ```json
    {
      "status": "SUCCESS|PARTIAL|FAILED|BLOCKED",
      "tokens_used": <actual>,
      "percent_of_context": <actual>,
      "compaction_events": 0,
      "commits": [
        {"hash": "abc123", "message": "feature: description", "type": "feature"}
      ],
      "files_changed": <actual>,
      "issue_metrics": {},
      "discovered_issues": [],
      "verification": {
        "build_passed": true,
        "tests_passed": true,
        "test_count": 15
      }
    }
    ```

    If you encounter a blocker, return:
    ```json
    {
      "status": "BLOCKED",
      "message": "Description of blocker",
      "blocker": "What needs to be resolved"
    }
    ```

    CRITICAL: You are the implementation agent - implement directly, do NOT spawn another subagent.
```

### Handle Execution Result

Parse the subagent result:

- **SUCCESS/PARTIAL**: Store metrics, proceed to verification
- **FAILED**: Return FAILED status with error details
- **BLOCKED**: Return FAILED with blocker info

### Verify Commit Messages

After execution completes, verify that the subagent used the correct commit messages and amend any mismatches before
proceeding to stakeholder review.

**Note the expected commit message before spawning the subagent:**

The delegation prompt specifies the commit message format the subagent should use. The expected commit type is
determined per-commit based on what the orchestrator specified in the delegation prompt. Issues may produce multiple
commit types (e.g., `feature:` for implementation + `docs:` for documentation). Each commit's type prefix should match
what the orchestrator instructed for that specific deliverable.

**Get actual commit messages from git:**

```bash
git -C ${WORKTREE_PATH} log --format="%H %s" ${BASE_BRANCH}..HEAD
```

This returns lines of: `<commit-hash> <commit-subject>`.

**Error handling:** If git log fails (non-zero exit code), log a warning and skip verification. Verification failures
should not block the workflow.

**Compare against subagent-reported messages:**

1. Check if the execution result's `commits[]` array is empty. If empty, skip verification.
2. Check if git log returned no commits. If no commits, skip verification.
3. For each commit in the `commits[]` array:
   - Extract the reported `hash` and `message` values
   - Find the corresponding line in git log output by matching the hash
   - If hash not found in git log output, treat as HIGH severity (subagent reporting error)
   - If found, compare the reported message against the actual commit subject from git log
   - Verify the commit message uses the expected type prefix specified in the delegation prompt

**If commit count mismatch detected:**

If the number of commits in `commits[]` differs from the number of lines in git log output:
- Extra commits in git log (not in reported array): Log WARNING - note them but do not amend (not actionable)
- Missing commits (in reported array but not in git log): Log HIGH severity - indicates subagent reporting error

**If message mismatch detected:**

When a mismatch is detected, the orchestrator MUST amend the commit(s) to use the correct message:

For single commit:
```bash
git -C ${WORKTREE_PATH} commit --amend -m "<correct message>"
```

For multiple commits: Use interactive rebase or sequential amend from oldest to newest to fix each incorrect message.

Track all amendments and include in the approval gate summary:

```
## Commit Message Verification
⚠ Mismatches detected and corrected:
  - af069982: "<placeholder>" → "feature: add verification step"
  - b1234abc: "<placeholder>" → "bugfix: correct parameter validation"
```

**If all messages match:**
- Continue silently to Step 4

## Step 4: Confirm Implementation

**This step confirms PLAN.md post-conditions were implemented before stakeholder quality review.**

Display the **Confirming phase** banner by running:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase confirming
```

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

Read the PLAN.md post-conditions and goal:

```bash
PLAN_CONTENT=$(cat "${ISSUE_PATH}/PLAN.md")
```

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
    BASE_BRANCH: ${BASE_BRANCH}

    ## Execution Result
    Commits: ${execution_commits_json}
    Files changed: ${files_changed}

    ## PLAN.md Content
    ${PLAN_CONTENT}

    ## Your Task
    1. Invoke the verify-implementation skill to check all PLAN.md post-conditions:
       ```
       Skill tool:
         skill: "cat:verify-implementation"
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
       mkdir -p "${WORKTREE_PATH}/.claude/cat/verify"
       ```
       - Write criterion-level details to: ${WORKTREE_PATH}/.claude/cat/verify/criteria-analysis.json
       - Write E2E test output/evidence to: ${WORKTREE_PATH}/.claude/cat/verify/e2e-test-output.json
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
          "detail_file": ".claude/cat/verify/criteria-analysis.json"
        }
      ],
      "e2e": {
        "status": "PASSED|FAILED|SKIPPED",
        "explanation": "brief one-line explanation",
        "detail_file": ".claude/cat/verify/e2e-test-output.json"
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
- Continue to Step 5

**If status is PARTIAL:**
- Note partial status in metrics
- Continue to Step 5

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
       - Add new "Fix" steps to the Execution Steps section of PLAN.md
       - Each fix step must address exactly one missing criterion
       - Do NOT remove or alter existing steps — only append new fix steps
       - Commit the revised PLAN.md: `planning: add fix steps for missing criteria (iteration ${VERIFY_ITERATION})`

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
       BASE_BRANCH: ${BASE_BRANCH}

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
- Continue to Step 5 with gaps noted in the approval gate summary

## Step 5: Review Phase

Display the **Reviewing phase** banner by running:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase reviewing
```

**If the command fails or produces no output**, STOP immediately:
```
FAIL: progress-banner launcher failed for phase 'reviewing'.
The jlink image may not be built. Run: mvn -f hooks/pom.xml verify
```
Do NOT skip the banner or continue without it.

### Skip Review if Configured

Skip if: `VERIFY == "none"` or `TRUST == "high"`

If skipping, output: "Review skipped (verify: ${VERIFY}, trust: ${TRUST})"

### Invoke Stakeholder Review

**Proceed automatically without asking the user.** The review phase is a mandatory workflow step, not an optional
operation. Do NOT ask for permission to run it, even though it spawns reviewer subagents. Asking for permission here
interrupts the workflow unnecessarily — the user already approved the workflow by invoking `/cat:work`.

**CRITICAL: Invoke stakeholder-review at main agent level** (do NOT delegate to subagent):

Encode commits in compact format: `hash:type,hash:type` (e.g., `abc123:bugfix,def456:test`).
Build COMMITS_COMPACT from the execution result's commits array.

```
Skill tool:
  skill: "cat:stakeholder-review"
  args: "${ISSUE_ID} ${WORKTREE_PATH} ${VERIFY} ${COMMITS_COMPACT}"
```

The stakeholder-review skill will spawn its own reviewer subagents and return aggregated results.

### Handle Review Result

Parse review result and filter false positives (concerns from reviewers that read base branch instead of worktree).

**False Positive Classification:**

A false positive is a concern raised because the reviewer read the wrong branch (base branch vs worktree). Stakeholder
reviewers run inside the worktree with pre-fetched file content, so false positives should be rare and only occur when
a reviewer ignores the provided file contents and reads from its default working directory.

**Pre-existing concerns are NOT false positives.** If a reviewer raises a concern about code that existed before the
current issue began, that is a real concern — apply the patience cost/benefit framework (below) to decide whether to
fix it inline or defer it as a new issue. Never classify a pre-existing concern as a false positive simply because the
code was not changed in this issue.

**Parse Review Result:**

The `cat:stakeholder-review` skill returns a JSON object. Extract the following fields:

- `REVIEW_STATUS` = `review_result.review_status` (e.g., `"REVIEW_PASSED"` or `"CONCERNS_FOUND"`)
- `ALL_CONCERNS` = `review_result.concerns[]` — the full list of concern objects returned by the review

Store `ALL_CONCERNS` for use in the auto-fix loop and the approval gate. Each concern object has fields:
`severity`, `stakeholder`, `location`, `explanation`, `recommendation`, and optionally `detail_file`.

**Read auto-fix level and patience from config:**

```bash
# Read reviewThreshold and patience from .claude/cat/cat-config.json
# Default is "low" (fix all concerns automatically) if config is missing or field is absent
AUTOFIX_THRESHOLD="low"
# Default is "medium" if config is missing or field is absent
PATIENCE_LEVEL="medium"

CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/cat-config.json"
if [[ -f "$CONFIG_FILE" ]]; then
    # Extract reviewThreshold simple string value using grep/sed (no jq available)
    AUTOFIX_RAW=$(grep -o '"reviewThreshold"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONFIG_FILE" | head -1 | \
        sed 's/.*"reviewThreshold"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    if [[ -n "$AUTOFIX_RAW" ]]; then
        AUTOFIX_THRESHOLD="$AUTOFIX_RAW"
    fi

    # Extract patience simple string value using grep/sed (no jq available)
    PATIENCE_RAW=$(grep -o '"patience"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONFIG_FILE" | head -1 | \
        sed 's/.*"patience"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    if [[ -n "$PATIENCE_RAW" ]]; then
        PATIENCE_LEVEL="$PATIENCE_RAW"
    fi
fi

# Determine minimum severity to auto-fix based on AUTOFIX_THRESHOLD:
# "low"      -> auto-fix CRITICAL, HIGH, MEDIUM, and LOW (default)
# "medium"   -> auto-fix CRITICAL, HIGH, and MEDIUM
# "high"     -> auto-fix CRITICAL and HIGH
# "critical" -> auto-fix CRITICAL only
```

**Auto-fix loop for concerns (based on configured autofix threshold):**

Initialize loop counter: `AUTOFIX_ITERATION=0`

**While concerns exist at or above the configured auto-fix threshold and AUTOFIX_ITERATION < 3:**

The auto-fix threshold is determined by `AUTOFIX_THRESHOLD`:
- `"low"`: loop while CRITICAL, HIGH, MEDIUM, or LOW concerns exist (default)
- `"medium"`: loop while CRITICAL, HIGH, or MEDIUM concerns exist
- `"high"`: loop while CRITICAL or HIGH concerns exist
- `"critical"`: loop while CRITICAL concerns exist

1. Increment iteration counter: `AUTOFIX_ITERATION++`
2. Extract concerns at or above the auto-fix threshold from `ALL_CONCERNS` and construct the following variables:

   **`concerns_formatted`** — a numbered Markdown list, one entry per filtered concern. For each concern, use the
   format:
   ```
   ### Concern N: SEVERITY - brief description
   - Stakeholder: [stakeholder]
   - Location: [location or "(no location)"]
   - Explanation: [explanation or "(field missing — see detail_file for full context)"]
   - Recommendation: [recommendation or "(field missing — see detail_file for full context)"]
   ```
   If a concern is missing `severity`, treat it as `MEDIUM`. If `explanation` or `recommendation` are absent,
   substitute `"(field missing — see detail_file for full context)"`.

   **`detail_file_paths`** — a newline-separated list of absolute paths to concern detail files. For each filtered
   concern, if `detail_file` is present and non-empty, prepend `${WORKTREE_PATH}/` to form an absolute path, then
   include it only if the file exists on disk. Omit concerns with no `detail_file` or a non-existent file — this is
   normal when a reviewer found no detailed concerns worth recording.

3. Spawn a planning subagent to analyze the concerns and produce a fix strategy:
   ```
   Task tool:
     description: "Plan fixes for review concerns (iteration ${AUTOFIX_ITERATION})"
     subagent_type: "cat:work-execute"
     model: "sonnet"
     prompt: |
       Analyze the following stakeholder review concerns for issue ${ISSUE_ID} and produce a fix strategy.

       ## Issue Configuration
       ISSUE_ID: ${ISSUE_ID}
       WORKTREE_PATH: ${WORKTREE_PATH}
       BRANCH: ${BRANCH}
       BASE_BRANCH: ${BASE_BRANCH}

       ## Concerns to Analyze
       ${concerns_formatted}

       ## Concern Detail Files
       For comprehensive analysis, read these detail files (if present):
       ${detail_file_paths}

       ## Instructions
       - Read the concern detail files to understand the full context of each concern
       - For each concern, determine:
         1. What specific code changes are needed
         2. Which files need to be modified
         3. What the correct implementation should look like
       - Produce a concrete fix plan listing exact changes needed for each concern
       - Do NOT implement the fixes yet — only plan them

       ## Return Format
       Return a fix plan in this format:
       ```
       ## Fix Plan

       ### Concern 1: [severity] [brief description]
       - File: [file path]
       - Change: [what needs to change and why]
       - Approach: [how to implement the fix]

       ### Concern 2: ...
       ```
   ```
4. Spawn implementation subagent to execute the fix plan:
   ```
   Task tool:
     description: "Fix review concerns (iteration ${AUTOFIX_ITERATION})"
     subagent_type: "cat:work-execute"
     prompt: |
       Fix the following stakeholder review concerns for issue ${ISSUE_ID}.

       ## Issue Configuration
       ISSUE_ID: ${ISSUE_ID}
       WORKTREE_PATH: ${WORKTREE_PATH}
       BRANCH: ${BRANCH}
       BASE_BRANCH: ${BASE_BRANCH}

       ## HIGH+ Concerns to Fix
       ${concerns_formatted}

       ## Concern Detail Files
       For comprehensive analysis, read these detail files (if present):
       ${detail_file_paths}

       ## Fix Plan (from planning step)
       ${fix_plan_from_planning_subagent}

       ## Instructions
       - Work in the worktree at ${WORKTREE_PATH}
       - Fix each concern according to the fix plan and recommendation
       - Read the concern detail files for full context on each concern
       - Commit your fixes using the same commit type as the primary implementation
         (e.g., `bugfix:`, `feature:`). These commits will be squashed into the main
         implementation commit in Step 6. Do NOT use `test:` as an independent commit
         type for concern fixes.
       - Return JSON status when complete

       ## Return Format
       ```json
       {
         "status": "SUCCESS|PARTIAL|FAILED",
         "commits": [{"hash": "...", "message": "...", "type": "..."}],
         "files_changed": N,
         "concerns_addressed": N
       }
       ```
   ```
5. Re-run stakeholder review (encode all commits in compact format `hash:type,hash:type`):
   ```
   Skill tool:
     skill: "cat:stakeholder-review"
     args: "${ISSUE_ID} ${WORKTREE_PATH} ${VERIFY} ${ALL_COMMITS_COMPACT}"
   ```
6. Parse new review result
7. If concerns at or above the configured auto-fix threshold remain, continue loop (if under iteration limit)

**If concerns at or above the threshold persist after 3 iterations:**
- Note that auto-fix reached iteration limit
- Store all remaining concerns for display at approval gate
- Continue to out-of-scope classification below

**If no concerns remain at or above the threshold (or no concerns at all):**
- Store concerns for display at approval gate
- Continue to out-of-scope classification below

**NOTE:** "REVIEW_PASSED" means stakeholder review passed, NOT user approval to merge.
User approval is a SEPARATE gate in Step 7.

### Evaluate Remaining Concerns (Cost/Benefit)

After the auto-fix loop, apply a cost/benefit analysis to determine whether to fix each remaining concern inline or
defer it.

**Step 1: Read the list of files changed by this issue:**

```bash
git -C "${WORKTREE_PATH}" diff --name-only "${BASE_BRANCH}..HEAD"
```

**Step 2: For each remaining concern, calculate benefit and cost:**

**Benefit** = severity weight of the concern:
- CRITICAL = 10
- HIGH = 6
- MEDIUM = 3
- LOW = 1

**Cost** = estimated scope of out-of-scope changes needed to address the concern. "Out-of-scope changes" means
modifications to files or code NOT already changed by the current issue:
- 0: Fix is entirely within files already changed by this issue (no out-of-scope changes)
- 1: Minor out-of-scope changes (~1-10 lines in 1 additional file)
- 4: Moderate out-of-scope changes (~10-30 lines or 2+ additional files)
- 10: Significant out-of-scope changes (~30+ lines or architectural changes across files)

**Step 3: Apply the decision rule based on patience:**

Determine the `patience_multiplier` from `PATIENCE_LEVEL`:
- `low` (fix aggressively): multiplier = 0.5 — fix if `benefit >= cost × 0.5`
- `medium` (balanced): multiplier = 2 — fix if `benefit >= cost × 2`
- `high` (stay focused): multiplier = 5 — fix if `benefit >= cost × 5`

For each concern: fix inline if `benefit >= cost × patience_multiplier`

**Key implications:**
- Cost=0 (fix within already-changed files): Always fix regardless of patience (any benefit >= 0)
- High severity + low cost: Fix at all patience levels
- Low severity + high cost: Defer at all patience levels
- Medium cases: patience determines the decision

The non-linear cost scale (0, 1, 4, 10) reflects that larger changes have disproportionately higher cost (more risk,
more review surface, more context required). The patience multipliers (0.5, 2, 5) give clear differentiation: low
patience fixes 13/16 combinations, medium fixes 8/16, high fixes 6/16.

**Step 4: Act on the evaluation:**

**Concerns that pass the threshold** (benefit >= cost × patience_multiplier):
- Add back into the auto-fix loop for fixing
- These additional loop iterations count toward the existing `AUTOFIX_ITERATION < 3` limit

**Concerns that don't pass the threshold** (benefit < cost × patience_multiplier):
- Defer by creating issues via `/cat:add`:
  - `patience: medium` → create in the current version backlog:
    ```
    Skill tool:
      skill: "cat:add"
      args: "add issue: [brief title derived from the concern description]"
    ```
  - `patience: high` → create in a later version backlog:
    ```
    Skill tool:
      skill: "cat:add"
      args: "add issue to next major version: [brief title derived from the concern description]"
    ```
- Mark these concerns as "deferred" in the approval gate display

## Step 6: Rebase and Squash Commits Before Review

**MANDATORY: Delegate rebase, squash, and STATE.md closure verification to a squash subagent.** This keeps the parent
agent context lean by offloading git operations to a dedicated haiku-model subagent.

Determine the primary commit message from the execution result (the most significant commit's message). If multiple
topics exist, use the most significant commit's message. Do NOT use generic messages like "squash commit".

**Before constructing the prompt below**, extract the primary commit message from the execution result's `commits`
array. Use the first implementation commit's `message` field. Substitute the actual message string in place of the
`PRIMARY_COMMIT_MESSAGE` value — do NOT pass the placeholder text literally.

```bash
# Example: extract the primary commit message from the execution result JSON
PRIMARY_COMMIT_MESSAGE=$(echo "$EXECUTION_RESULT" | \
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
    BASE_BRANCH: ${BASE_BRANCH}
    PRIMARY_COMMIT_MESSAGE: ${PRIMARY_COMMIT_MESSAGE}

    Load and follow: @${CLAUDE_PLUGIN_ROOT}/agents/work-squash.md

    Return JSON per the output contract in the agent definition.
```

### Handle Squash Result

Parse the subagent result:

- **SUCCESS**: Extract `commits` array for use at the approval gate. Continue to Step 7.
- **FAILED** (phase: rebase): Return FAILED status with conflict details. Do NOT proceed.
- **FAILED** (phase: squash or verify): Return FAILED status with error details. Do NOT proceed.

## Step 7: Approval Gate

**CRITICAL: This step is MANDATORY when trust != "high".**

**Enforced by hook M480:** PreToolUse hook on Task tool blocks work-merge spawn when trust=medium/low
and no explicit user approval is detected in session history.

### Pre-Gate Squash Verification (BLOCKING)

Before presenting the approval gate, verify that Step 6 (Rebase and Squash) was executed. Squashing by topic
may produce 1-2 commits (e.g., one implementation commit + one config commit), so commit count alone does not
determine completion. Instead, confirm that `/cat:git-squash` was invoked in Step 6.

**If Step 6 was skipped:** STOP — return to Step 6 and complete the rebase and squash before proceeding.

**If Step 6 was completed:** Proceed to approval gate below.

### Pre-Gate Skill-Builder Review (BLOCKING)

When the issue modifies files in `plugin/skills/` or `plugin/commands/`, invoke `/cat:skill-builder` to review
each modified skill or command file before presenting the approval gate.

```bash
# Check whether any skill or command files were modified
git diff --name-only "$(cat "$(git rev-parse --git-dir)/cat-base")..HEAD" | grep -E '^plugin/(skills|commands)/'
```

**If skill or command files were modified:** Invoke `/cat:skill-builder` with the path to each modified skill or
command. Review the output and address any priming issues or structural problems it identifies before proceeding
to the approval gate.

**If no skill or command files were modified:** Skip this check and proceed to the approval gate below.

### If trust == "high"

Skip approval gate. Continue directly to Step 8 (merge).

### If trust == "low" or trust == "medium"

**STOP HERE for user approval.** Do NOT proceed to merge automatically.

### Check for Prior Direct Approval

Before presenting AskUserQuestion, check whether the user has already typed an explicit approval in the current
conversation. Scan recent conversation messages for user messages containing both "approve" and "merge" (e.g.,
"approve and merge", "approve merge", "approved merge").

**If direct approval is detected:** Skip AskUserQuestion and proceed directly to Step 8 (merge). The
PreToolUse hook reads the session JSONL file and will recognize the user's direct message as approval.

**If no direct approval is detected:** Continue to the approval gate below.

### Present Changes Before Approval Gate (BLOCKING)

**MANDATORY: Render the diff and output the full change summary BEFORE invoking AskUserQuestion.**

Context compaction can occur at any point in a long session. When the conversation is compacted, the user's
visible context resets — they will only see output from the current turn onward. If AskUserQuestion is invoked
without first presenting the changes in the same turn, the user sees an approval gate with no visible context
about what they are approving.

**Required pre-gate output sequence (all mandatory, in this order):**

1. **Get diff** — invoke `cat:get-diff` to display the changes:
   ```
   Skill tool:
     skill: "cat:get-diff"
   ```

2. **Display commit summary** — list commits since base branch:
   ```bash
   git -C ${WORKTREE_PATH} log --oneline ${BASE_BRANCH}..HEAD
   ```

3. **Display issue goal** (from PLAN.md)

4. **Display execution summary** (commits count, files changed)

5. **Display E2E testing summary** (see below)

6. **Display review results with ALL concern details** (see below)

Only after all six items above have been output in the current conversation turn may you invoke AskUserQuestion.

**MANDATORY: Display E2E testing summary before the approval gate.** Explain what E2E tests were run, what they
verified, and what the results were. This helps the user determine whether the feature works in its real environment.
If E2E testing was skipped (no E2E criteria in PLAN.md, or non-feature issue), state that explicitly.

**MANDATORY: Display ALL stakeholder concerns before the approval gate**, regardless of severity.
Users need full visibility into review findings to make informed merge decisions.

Display fixed and deferred concerns in separate groups:

**Fixed concerns** (addressed in this issue based on cost/benefit analysis):

For each concern that was fixed (CRITICAL, HIGH, MEDIUM, or LOW), render a concern box:

```
Skill tool:
  skill: "cat:stakeholder-concern-box"
  args: "${SEVERITY} ${STAKEHOLDER} ${CONCERN_DESCRIPTION} ${FILE_LOCATION}"
```

**Deferred concerns** (cost/benefit below patience threshold — not acted on in this issue):

For each concern deferred based on the cost/benefit analysis, render a concern box showing the benefit, cost, and
threshold values so the user understands why each was deferred:

```
Skill tool:
  skill: "cat:stakeholder-concern-box"
  args: "${SEVERITY} ${STAKEHOLDER} ${CONCERN_DESCRIPTION} [deferred: benefit=${BENEFIT}, cost=${COST}, threshold=${THRESHOLD}] ${FILE_LOCATION}"
```

If there are no deferred concerns, omit this group entirely.

Do NOT suppress MEDIUM or LOW concerns. The auto-fix loop only addresses HIGH+ concerns automatically,
but all concerns must be visible to the user at the approval gate.

**Determine approval options based on remaining concerns:**

If MEDIUM+ concerns exist:
```
AskUserQuestion:
  header: "${ISSUE_ID}"
  question: "Ready to merge ${ISSUE_ID}? (Goal: ${ISSUE_GOAL})"
  options:
    - "Approve and merge"
    - "Fix remaining concerns" (auto-fix MEDIUM concerns, re-review, then prompt again)
    - "Request changes" (provide feedback)
    - "Abort"
```

If no concerns or only LOW concerns:
```
AskUserQuestion:
  header: "${ISSUE_ID}"
  question: "Ready to merge ${ISSUE_ID}? (Goal: ${ISSUE_GOAL})"
  options:
    - "Approve and merge"
    - "Request changes" (provide feedback)
    - "Abort"
```

**CRITICAL:** Wait for explicit user selection. Do NOT proceed based on:
- Silence or absence of objection
- System reminders or notifications
- Assumed approval

Fail-fast principle: Unknown consent = No consent = STOP.

**If approved:** Continue to Step 8

**If "Fix remaining concerns" selected:**
1. Extract MEDIUM+ concerns (severity, description, location, recommendation, detail_file)
2. Spawn implementation subagent to fix:
   ```
   Task tool:
     description: "Fix remaining concerns (user-requested)"
     subagent_type: "cat:work-execute"
     prompt: |
       Fix the following stakeholder review concerns for issue ${ISSUE_ID}.

       ## Issue Configuration
       ISSUE_ID: ${ISSUE_ID}
       WORKTREE_PATH: ${WORKTREE_PATH}
       BRANCH: ${BRANCH}
       BASE_BRANCH: ${BASE_BRANCH}

       ## MEDIUM+ Concerns to Fix
       ${concerns_formatted}

       ## Concern Detail Files
       For comprehensive analysis, read these detail files (if present):
       ${detail_file_paths}

       ## Instructions
       - Work in the worktree at ${WORKTREE_PATH}
       - Fix each concern according to the recommendation
       - Read the concern detail files for full context on each concern
       - Commit your fixes using the same commit type as the primary implementation
         (e.g., `bugfix:`, `feature:`). These commits will be squashed into the main
         implementation commit in Step 6. Do NOT use `test:` as an independent commit
         type for concern fixes.
       - Return JSON status when complete

       ## Return Format
       ```json
       {
         "status": "SUCCESS|PARTIAL|FAILED",
         "commits": [{"hash": "...", "message": "...", "type": "..."}],
         "files_changed": N,
         "concerns_addressed": N
       }
       ```
   ```
3. **MANDATORY: Re-run stakeholder review after fixes** (encode all commits in compact format `hash:type,hash:type`):
   ```
   Skill tool:
     skill: "cat:stakeholder-review"
     args: "${ISSUE_ID} ${WORKTREE_PATH} ${VERIFY} ${ALL_COMMITS_COMPACT}"
   ```
   **The review MUST be re-run to:**
   - Verify the concerns were actually resolved
   - Detect new concerns introduced by the fixes
   - Provide updated results to the user at the approval gate
4. Return to Step 7 approval gate with updated results

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

## Step 8: Merge Phase

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

**Exit the worktree directory before spawning the merge subagent:**

```bash
# Move to /workspace before spawning merge subagent
# Prevents parent shell corruption when the subagent removes the worktree
cd /workspace
```

Spawn a merge subagent:

```
Task tool:
  description: "Merge: squash, merge, cleanup"
  subagent_type: "cat:work-merge"
  prompt: |
    Execute the merge phase for issue ${ISSUE_ID}.

    ## Configuration
    SESSION_ID: $CLAUDE_SESSION_ID
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    BRANCH: ${BRANCH}
    BASE_BRANCH: ${BASE_BRANCH}
    COMMITS: ${commits_json}
    AUTO_REMOVE_WORKTREES: ${AUTO_REMOVE}

    Load and follow: @${CLAUDE_PLUGIN_ROOT}/skills/work-merge/SKILL.md

    Return JSON per the output contract in the skill.
```

### Handle Merge Result

Parse merge result:

- **MERGED**: Continue to Step 9
- **CONFLICT**: Return FAILED with conflict details
- **ERROR**: Return FAILED with error

## Step 9: Return Success

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

## Error Handling

If any phase fails:

1. Capture error message and phase name
2. Attempt lock release: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${ISSUE_ID}" "$CLAUDE_SESSION_ID"`
3. Return FAILED status with actual error details

```json
{
  "status": "FAILED",
  "phase": "execute|review|merge",
  "message": "actual error message",
  "issue_id": "${ISSUE_ID}"
}
```

**NEVER fabricate failure responses.** You must actually attempt the work before reporting failure.

## Success Criteria

- [ ] All phases orchestrated at main agent level
- [ ] Skills requiring spawning (shrink-doc, compare-docs, stakeholder-review) invoked directly
- [ ] Approval gates respected based on trust level
- [ ] Progress banners displayed at phase transitions
- [ ] Lock released on completion or error
- [ ] Results collected and returned as JSON
