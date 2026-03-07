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

## MANDATORY STEPS

The following steps are **mandatory** and must not be skipped without explicit user permission. Mandatory steps do not
require user permission to execute — they are pre-approved as part of the `/cat:work` workflow. Steps marked **BLOCKING**
are additionally enforced by hooks or explicit STOP instructions that block progress mechanically if skipped.

- **Step 5: Review Phase (Stakeholder Review)** — always invoke `cat:stakeholder-review-agent` except for config-driven
  exceptions (VERIFY=none or TRUST=high); do not skip based on perceived simplicity or short feedback cycles
- **Step 7: Squash Commits by Topic Before Review** — always squash before the approval gate; do not proceed to
  Step 8 without completing this step
- **Step 8: Rebase onto Target Branch Before Approval Gate** — always rebase the squashed branch onto the current tip
  of the target branch before the approval gate; do not proceed to Step 9 without completing this step
- **Step 9 (sub-step): Skill-Builder Review** — always invoke `cat:skill-builder` for modified skill or
  command files before presenting the approval gate

## Arguments Format

The main `/cat:work` skill invokes this with positional space-separated arguments:

```
<issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <estimated_tokens> <trust> <verify>
```

| Position | Name | Example |
|----------|------|---------|
| 1 | issue_id | `2.1-issue-name` |
| 2 | issue_path | `/workspace/.claude/cat/issues/v2/v2.1/issue-name` |
| 3 | worktree_path | `${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat/worktrees/2.1-issue-name` |
| 4 | issue_branch | `2.1-issue-name` |
| 5 | target_branch | `v2.1` |
| 6 | estimated_tokens | `45000` |
| 7 | trust | `medium` |
| 8 | verify | `changed` |

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
read ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH ESTIMATED_TOKENS TRUST VERIFY <<< "$ARGUMENTS"
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
source "${CLAUDE_PLUGIN_ROOT}/scripts/cat-env.sh"
LOCK_FILE="${LOCKS_DIR}/${ISSUE_ID}.lock"

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

### Read PLAN.md and Invoke Main Agent Waves

```bash
PLAN_MD="${ISSUE_PATH}/PLAN.md"
```

Read the `## Main Agent Waves` section from PLAN.md:

```bash
MAIN_AGENT_WAVES=$(sed -n '/^## Main Agent Waves/,/^## /p' "$PLAN_MD" | head -n -1)
```

**If `## Main Agent Waves` is present and non-empty:** extract each bullet item
(`- /cat:skill-name args`) and invoke the corresponding skill NOW at the main agent level
using the Skill tool.

Example: If `## Main Agent Waves` contains `- /cat:optimize-doc path/to/file.md`, then:

```
Skill tool:
  skill: "cat:optimize-doc-agent"
  args: "path/to/file.md"
```

**Complete each skill fully before delegation.** Pre-invoked skills may have built-in
iteration loops, validation gates, or multi-step workflows. Run each skill to its documented
completion state before passing results to the implementation subagent. Do NOT pass intermediate
or failed results to the subagent for manual fixing — that bypasses the skill's quality gates.

Capture the output from these skills - the implementation subagent will need the results.

### Detect Parallel Execution Waves

Read PLAN.md directly to detect `## Sub-Agent Waves` sections and count the number of `### Wave N` subsections.

For each wave subsection found, count the top-level bullet items (`- `) that don't start with indentation. Ignore
sub-items (indented bullets with `  - `).

**Simplified detection in Bash:**

```bash
# Count number of ### Wave N sections in PLAN.md
# Count ### Wave N sections under ## Sub-Agent Waves
WAVES_COUNT=$(grep -c "^### Wave " "$PLAN_MD" 2>/dev/null || echo 0)
```

Where `$PLAN_MD` is the path to the issue's PLAN.md file.

**If waves are empty or only one wave is present (`WAVES_COUNT` is 0 or 1):** proceed to single-subagent execution
(see below). Parse execution items from `## Sub-Agent Waves` / `### Wave 1`.

**If two or more waves are present (`WAVES_COUNT` >= 2):** use parallel execution (see Parallel Subagent Execution
below). Extract the wave sections from PLAN.md and count items in each wave.

### Mid-Work PLAN.md Revision

If requirements change during implementation (user feedback, discovered constraints, or scope adjustments), invoke
`cat:plan-builder-agent` to revise the PLAN.md before continuing execution.

Read effort from config:

```bash
CONFIG_FILE="/workspace/.claude/cat/cat-config.json"
EFFORT=$(grep '"effort"' "$CONFIG_FILE" | sed 's/.*"effort"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
if [[ -z "$EFFORT" ]]; then
  echo "ERROR: 'effort' key not found in $CONFIG_FILE" >&2
  exit 1
fi
```

Invoke:

```
Skill tool:
  skill: "cat:plan-builder-agent"
  args: "${CAT_AGENT_ID} ${EFFORT} revise ${ISSUE_PATH} <description of what changed>"
```

After revision, re-read the updated PLAN.md and adjust remaining execution accordingly.

### Delegation Prompt Construction

**Subagents read PLAN.md directly — do NOT relay its content into prompts.**

Pass `PLAN_MD_PATH` so the subagent can read the Goal and Sub-Agent Waves/Steps sections itself.
Do NOT extract and paste those sections into the prompt — that is the content relay anti-pattern.

**Why:** Subagents that receive a `PLAN_MD_PATH` and read PLAN.md themselves always see the authoritative
content, preserving PLAN.md's structure exactly (distinct steps remain distinct, no re-summarization).
Pasting content into prompts creates a stale copy that can diverge, wastes tokens, and risks
interpretive distortion.

**Pattern:**
- ✅ Pass `PLAN_MD_PATH: ${PLAN_MD}` and instruct the subagent to read Goal and Execution sections itself
- ✅ Trust PLAN.md structure — subagents read it directly, no relay needed
- ❌ Do NOT inline `${ISSUE_GOAL}` or Sub-Agent Waves content into the prompt
- ❌ Do NOT add interpretive summaries or aggregate instructions that restate PLAN.md differently

### Commit-Before-Spawn Requirement

**BLOCKING:** Before spawning ANY implementation subagent (single or parallel), commit all pending changes in
the worktree. This is enforced by the `EnforceCommitBeforeSubagentSpawn` hook, which blocks Task spawning of
`cat:work-execute` when the worktree is dirty.

**Why:** Each subagent is spawned with `isolation: "worktree"`, creating a separate git worktree branched from
the current HEAD of the issue branch. Uncommitted changes in the main agent's worktree are NOT visible in the
subagent's worktree. All changes must be committed before spawning so the subagent sees the complete state.

```bash
cd "${WORKTREE_PATH}" && git status --porcelain  # Must be empty before spawning
cd "${WORKTREE_PATH}" && git add -A && git commit -m "planning: update PLAN.md before delegation"  # if needed
```

### Single-Subagent Execution (no groups or only one group)

Spawn a subagent to implement the issue:

```
Task tool:
  description: "Execute: implement ${ISSUE_ID}"
  subagent_type: "cat:work-execute"
  isolation: "worktree"
  prompt: |
    Execute the implementation for issue ${ISSUE_ID}.

    ## Issue Configuration
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    BRANCH: ${BRANCH}
    TARGET_BRANCH: ${TARGET_BRANCH}
    ESTIMATED_TOKENS: ${ESTIMATED_TOKENS}
    TRUST_LEVEL: ${TRUST}
    PLAN_MD_PATH: ${PLAN_MD}

    Read the Goal section and Sub-Agent Waves (or Execution Steps) from PLAN_MD_PATH directly.
    Do NOT ask the main agent to provide this content — it is authoritative in PLAN.md.

    ## Pre-Invoked Skill Results
    [If skills were pre-invoked above, include their output here]

    ## First Action (MANDATORY)
    Before doing ANYTHING else, verify the branch:
    ```bash
    git branch --show-current  # Must output: ${BRANCH}
    ```
    If the branch does not match ${BRANCH}, STOP and return BLOCKED immediately.

    ## Critical Requirements
    - You are working in an isolated worktree. Your changes will be merged back to the issue branch.
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

**After the subagent returns**, merge its commits back into the issue branch:

```bash
cd "${WORKTREE_PATH}" && git merge --ff-only <subagent-branch>  # fast-forward merge of subagent commits
```

The subagent branch name and worktree path are returned in the Task tool result when `isolation: "worktree"` is
used. Use that branch name in the merge command above.

### Parallel Subagent Execution (two or more groups)

When PLAN.md contains two or more execution groups, spawn one subagent per group simultaneously.
Each subagent is spawned with `isolation: "worktree"` — it gets its own isolated git worktree branched from
the issue branch HEAD. Subagents execute concurrently without shared disk state. The last group's subagent
updates STATE.md; other groups skip it.

**IMPORTANT:** Each parallel subagent commits to its own isolated worktree branch. After all subagents
complete, the main agent merges each subagent branch back into the issue branch in order (A, B, C, ...).
Only the last wave subagent updates STATE.md.

**Step order:** For each group label (A, B, C, ... sorted alphabetically), extract the steps
belonging to that group from PLAN.md, then spawn the subagent. Spawn all subagents in the same
message (Task tool calls can be parallel).

For each group (example for group A with steps 1, 2, 3):

```
Task tool:
  description: "Execute: implement ${ISSUE_ID} group A (steps 1, 2, 3)"
  subagent_type: "cat:work-execute"
  isolation: "worktree"
  prompt: |
    Execute the implementation for issue ${ISSUE_ID}, group A only.

    ## Issue Configuration
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    BRANCH: ${BRANCH}
    TARGET_BRANCH: ${TARGET_BRANCH}
    ESTIMATED_TOKENS: ${ESTIMATED_TOKENS}
    TRUST_LEVEL: ${TRUST}
    PLAN_MD_PATH: ${PLAN_MD}
    ASSIGNED_WAVE: 1

    Read the Goal section from PLAN_MD_PATH. Then read ONLY the `### Wave 1` section from
    PLAN_MD_PATH for your execution items. Do NOT read or execute items from other wave sections.
    Do NOT ask the main agent to provide this content — it is authoritative in PLAN.md.

    ## Pre-Invoked Skill Results
    [If skills were pre-invoked above, include their output here]

    ## First Action (MANDATORY)
    Before doing ANYTHING else, verify the branch:
    ```bash
    git branch --show-current  # Must output: ${BRANCH}
    ```
    If the branch does not match ${BRANCH}, STOP and return BLOCKED immediately.

    ## Critical Requirements
    - You are working in an isolated worktree. Your changes will be merged back to the issue branch.
    - Verify you are on branch ${BRANCH} before making changes
    - Execute ONLY the items assigned to your wave (ASSIGNED_WAVE above, read from PLAN.md)
    - Do NOT execute items from other waves
    - **STATE.md ownership:** You are [DETERMINED AUTOMATICALLY: if wave is the last one, "the STATE.md owner"
      else "NOT the STATE.md owner"]. [If owner: "Update STATE.md in your final commit: status: closed,
      progress: 100%." Else: "Do NOT modify STATE.md in any commit."]
    - Run tests if applicable
    - Commit your changes using the commit type from PLAN.md (e.g., `feature:`, `bugfix:`, `docs:`). The commit
      message must follow the format: `<type>: <descriptive summary>`. Do NOT use generic messages.

    ## Return Format
    Return JSON when complete:
    ```json
    {
      "status": "SUCCESS|PARTIAL|FAILED|BLOCKED",
      "group": "A",
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
      "group": "A",
      "message": "Description of blocker",
      "blocker": "What needs to be resolved"
    }
    ```

    CRITICAL: You are the implementation agent - implement directly, do NOT spawn another subagent.
```

**Wait for all group subagents to complete, then merge each subagent branch back into the issue branch in
alphabetical order (A first, then B, C, ...):**

```bash
cd "${WORKTREE_PATH}" && git merge --ff-only <subagent-A-branch>
# For subsequent groups where ff-only fails (diverged history), use /cat:git-merge-linear-agent
# ... repeat for each group
```

The subagent branch name and worktree path for each group are returned in the Task tool result when
`isolation: "worktree"` is used.

- Collect commits from all groups into a single combined list
- If any group returns FAILED or BLOCKED, stop and report failure
- Aggregate `files_changed`, `tokens_used`, and `compaction_events` across all groups

### Handle Execution Result

Parse the subagent result(s):

- **SUCCESS/PARTIAL** (all groups): Merge commits, aggregate metrics, proceed to verification
- **FAILED** (any group): Return FAILED status with error details from that group
- **BLOCKED** (any group): Return FAILED with blocker info from that group

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
cd "${WORKTREE_PATH}" && git log --format="%H %s" ${TARGET_BRANCH}..HEAD
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
cd "${WORKTREE_PATH}" && git commit --amend -m "<correct message>"
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
       VERIFY_DIR="${PROJECT_CAT_DIR}/${CLAUDE_SESSION_ID}/cat/verify"
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
- Continue to Step 5

**If status is PARTIAL:**
- STOP — do not proceed to stakeholder review
- Re-enter the INCOMPLETE fix loop below to address unmet post-conditions before review
- See also: [PARTIAL Verification Result](#partial-verification-result-step-4) in Rejection Handling

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
- Continue to Step 5 with gaps noted in the approval gate summary

## Step 5: Review Phase (MANDATORY)

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
  skill: "cat:stakeholder-review-agent"
  args: "${ISSUE_ID} ${WORKTREE_PATH} ${VERIFY} ${COMMITS_COMPACT}"
```

The stakeholder-review skill will spawn its own reviewer subagents and return aggregated results.

### Handle Review Result

Parse review result and filter false positives (concerns from reviewers that read target branch instead of worktree).

**False Positive Classification:**

A false positive is a concern raised because the reviewer read the wrong branch (target branch vs worktree). Stakeholder
reviewers run inside the worktree with pre-fetched file content, so false positives should be rare and only occur when
a reviewer ignores the provided file contents and reads from its default working directory.

**Pre-existing concerns are NOT false positives.** If a reviewer raises a concern about code that existed before the
current issue began, that is a real concern — apply the patience cost/benefit framework (below) to decide whether to
fix it inline or defer it as a new issue. Never classify a pre-existing concern as a false positive simply because the
code was not changed in this issue.

**Parse Review Result:**

The `cat:stakeholder-review-agent` skill returns a JSON object. Extract the following fields:

- `REVIEW_STATUS` = `review_result.review_status` (e.g., `"REVIEW_PASSED"` or `"CONCERNS_FOUND"`)
- `ALL_CONCERNS` = `review_result.concerns[]` — the full list of concern objects returned by the review

Store `ALL_CONCERNS` for use in the auto-fix loop and the approval gate. Each concern object has fields:
`severity`, `stakeholder`, `location`, `explanation`, `recommendation`, and optionally `detail_file`.

**Read patience from config:**

```bash
# Read patience from .claude/cat/cat-config.json
# Default is "medium" if config is missing or field is absent
PATIENCE_LEVEL="medium"

CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/cat-config.json"
if [[ -f "$CONFIG_FILE" ]]; then
    # Extract patience simple string value using grep/sed (no jq available)
    PATIENCE_RAW=$(grep -o '"patience"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONFIG_FILE" | head -1 | \
        sed 's/.*"patience"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    if [[ -n "$PATIENCE_RAW" ]]; then
        PATIENCE_LEVEL="$PATIENCE_RAW"
    fi
fi
```

**Concern Decision Gate (trust=low only):**

When `TRUST == "low"`, present the patience matrix FIX/DEFER decisions to the user for confirmation before the auto-fix
loop runs. When `TRUST != "low"` (medium or high), skip this gate entirely and proceed directly to the auto-fix loop.

**Gate procedure (trust=low only):**

1. Build a formatted summary of all concerns that survived the `minSeverity` filter, showing for each concern:
   - Severity and brief description
   - Stakeholder and location
   - Decision: **FIX** or **DEFER**
   - Reasoning: benefit (severity weight), cost (scope estimate), threshold (`benefit >= cost × patience_multiplier`)

   Format each concern as:
   ```
   N. [FIX/DEFER] SEVERITY - brief description
      Stakeholder: [stakeholder] | Location: [location]
      Benefit: [weight] | Cost: [cost] | Threshold: benefit >= cost × [multiplier] = [result]
   ```

2. Present the summary to the user via `AskUserQuestion` with these options:
   - **"Proceed with these decisions (Recommended)"** — continue to the auto-fix loop with the current FIX/DEFER
     assignments unchanged
   - **"Let me change decisions"** — the user specifies which concerns to flip between FIX and DEFER. After receiving
     the user's modifications, update the FIX/DEFER assignments accordingly and proceed to the auto-fix loop with the
     revised assignments

3. After the user confirms (or modifies and confirms), proceed to the auto-fix loop below with the final FIX/DEFER
   assignments.

**Auto-fix loop for concerns marked as FIX:**

**Spawn fix subagents without asking the user during the auto-fix loop.** When stakeholder review returns
REJECTED, always enter the auto-fix loop — CRITICAL concerns must be fixed before merge. When review returns
CONCERNS_FOUND, concerns flow through the pipeline: `minSeverity` filter (silently drops concerns below
threshold) → patience cost/benefit matrix (marks each surviving concern as FIX or DEFER) → concern decision gate
(trust=low only: user confirms/modifies FIX/DEFER assignments). FIX-marked concerns enter the auto-fix loop. For
trust >= "medium", the concern decision gate is skipped and FIX/DEFER assignments from the patience matrix are used
directly. In all cases, do NOT present options to the user or ask what to do during the auto-fix loop itself — spawn
fix subagents and continue.

**CRITICAL: The auto-fix loop applies ONLY to spawning fix subagents. Steps 6, 7, 8, and 9 (Deferred Concern
Review, Squash Commits by Topic, Rebase onto Target Branch, and Approval Gate) MUST still be executed after the loop
completes. The user must explicitly approve the merge via Step 9 when trust != "high".**

Initialize loop counter: `AUTOFIX_ITERATION=0`

**While FIX-marked concerns exist and AUTOFIX_ITERATION < 3:**

1. Increment iteration counter: `AUTOFIX_ITERATION++`
2. Extract FIX-marked concerns from `ALL_CONCERNS` (after patience matrix evaluation) and construct the following variables:

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
   concern, if `detail_file` is present and non-empty, use it as an absolute path (reviewer agents write absolute
   paths using their `${WORKTREE_PATH}`). Include the path only if the file exists on disk. Omit concerns with no
   `detail_file` or a non-existent file — this is normal when a reviewer found no detailed concerns worth recording.

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
       TARGET_BRANCH: ${TARGET_BRANCH}

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
       TARGET_BRANCH: ${TARGET_BRANCH}

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
         implementation commit in Step 7. Do NOT use `test:` as an independent commit
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
     skill: "cat:stakeholder-review-agent"
     args: "${ISSUE_ID} ${WORKTREE_PATH} ${VERIFY} ${ALL_COMMITS_COMPACT}"
   ```
6. Parse new review result
7. If FIX-marked concerns remain, continue loop (if under iteration limit)

**If FIX-marked concerns persist after 3 iterations:**
- Note that auto-fix reached iteration limit
- Store all remaining concerns for display at approval gate
- Continue to out-of-scope classification below

**If no FIX-marked concerns remain (or no concerns at all):**
- Store concerns for display at approval gate
- Continue to out-of-scope classification below

**NOTE:** "REVIEW_PASSED" means stakeholder review passed, NOT user approval to merge.
User approval is a SEPARATE gate in Step 9.

### Evaluate Remaining Concerns (Cost/Benefit)

After the auto-fix loop, apply a cost/benefit analysis to determine whether to fix each remaining concern inline or
defer it.

**Step 1: Read the list of files changed by this issue:**

```bash
# Compare against target branch (what the issue changed)
cd "${WORKTREE_PATH}" && git diff --name-only "${TARGET_BRANCH}..HEAD"
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

Partition `ALL_CONCERNS` into two named lists based on the threshold:

- `FIXED_CONCERNS` — concerns where `benefit >= cost × patience_multiplier` (will be fixed in the auto-fix loop)
- `DEFERRED_CONCERNS` — concerns where `benefit < cost × patience_multiplier` (will be deferred or tracked as issues)

**Concerns that pass the threshold** (benefit >= cost × patience_multiplier):
- Add to `FIXED_CONCERNS` and back into the auto-fix loop for fixing
- These additional loop iterations count toward the existing `AUTOFIX_ITERATION < 3` limit

**Step 5: Handle deferred concerns** (benefit < cost × patience_multiplier):

Perform these actions immediately, before proceeding to Step 6 (Deferred Concern Review).

For **HIGH or CRITICAL severity** deferred concerns — create a tracking issue via `/cat:add-agent` so the concern is not
lost. The target version is determined by the combination of severity and patience:

| Severity | Patience | Target Version | `/cat:add-agent` args prefix |
|----------|----------|----------------|------------------------|
| CRITICAL | low      | current minor  | `add issue:`           |
| CRITICAL | medium   | current minor  | `add issue:`           |
| CRITICAL | high     | next minor     | `add issue to next minor version:` |
| HIGH     | low      | current minor  | `add issue:`           |
| HIGH     | medium   | next minor     | `add issue to next minor version:` |
| HIGH     | high     | next major     | `add issue to next major version:` |

- Build an issue title following the naming convention: `{version}-fix-{short-description}` where `{version}` is the
  current version (e.g. `v2.1`) and `{short-description}` is a 2–4 word slug of the concern.
- Include in the args: the stakeholder name, severity, file location, concern description, and recommended fix
  so the created issue is immediately actionable.

Example invocations:

- Current minor (CRITICAL/low, CRITICAL/medium, HIGH/low):
  ```
  Skill tool:
    skill: "cat:add-agent"
    args: "add issue: {version}-fix-{short-description} — [stakeholder]: [severity] concern in [file/location]:
    [full concern description]. Recommended fix: [recommended fix]"
  ```
- Next minor (CRITICAL/high, HIGH/medium):
  ```
  Skill tool:
    skill: "cat:add-agent"
    args: "add issue to next minor version: {version}-fix-{short-description} — [stakeholder]: [severity] concern
    in [file/location]: [full concern description]. Recommended fix: [recommended fix]"
  ```
- Next major (HIGH/high):
  ```
  Skill tool:
    skill: "cat:add-agent"
    args: "add issue to next major version: {version}-fix-{short-description} — [stakeholder]: [severity] concern
    in [file/location]: [full concern description]. Recommended fix: [recommended fix]"
  ```

Any deferred concern that (a) is at or above `minSeverity` AND (b) was not automatically tracked as an issue above
(e.g., MEDIUM or LOW severity concerns deferred by the patience matrix) is collected for the interactive wizard in
Step 6 where the user decides how to handle them. Concerns below `minSeverity` are silently ignored and never appear
in Step 6.

## Step 6: Deferred Concern Review (Interactive Wizard)

This step runs BEFORE the Squash Commits by Topic step (Step 7) and BEFORE the Approval Gate (Step 9), giving the user a
chance to review and adjust deferred concern handling before committing to the merge.

### Part A: Review HIGH/CRITICAL concern scheduling

If any HIGH or CRITICAL concerns had issues created in Step 5, present them to the user:

1. Display a summary of each concern:
   - Severity and stakeholder
   - Short description of the concern
   - The version it was scheduled for (current minor / next minor / next major)

2. Use AskUserQuestion to ask the user about the scheduling:

```
AskUserQuestion tool:
  question: "The following deferred concerns were automatically scheduled as new issues:

  [list each concern: severity, stakeholder, description → scheduled for: version]

  Are these deferred concern schedules acceptable?"
  options:
    - "Accept all schedules"
    - "Reschedule one or more concerns"
    - "Other"
```

3. If user selects **"Reschedule one or more concerns"**: use AskUserQuestion to ask which ones to reschedule and
   what version to target, then invoke the appropriate `/cat:add-agent` with the corrected version args. Replace the
   originally created issue(s) if possible, or note that the original issue(s) should be manually removed.

4. If user selects **"Other"**: pause and let the user provide freeform instructions, then act on them.

### Part B: Review untracked deferred concerns

If any deferred concerns were NOT automatically tracked as issues AND are at or above `minSeverity` (this includes
MEDIUM/LOW concerns deferred by the patience matrix), present them to the user. Concerns below `minSeverity` are
silently ignored and do not appear here.

1. Display a summary of each untracked concern:
   - Severity and stakeholder
   - Short description of the concern

2. Use AskUserQuestion to ask the user how to handle them:

```
AskUserQuestion tool:
  question: "The following concerns remain untracked:

  [list each concern: severity, stakeholder, description]

  How should these be handled?"
  options:
    - "Create issues for selected concerns"
    - "Ignore these concerns"
    - "Other"
```

3. If user selects **"Create issues for selected concerns"**: use AskUserQuestion with multiSelect to let the user
   pick which concerns to track, then invoke `/cat:add-agent` for each selected concern. Use the same severity × patience
   matrix from Step 5 to determine the target version for each selected concern.

4. If user selects **"Ignore these concerns"**: proceed without creating issues for these concerns.

5. If user selects **"Other"**: pause and let the user provide freeform instructions, then act on them.

### Skip conditions

Skip this step entirely and proceed to Step 7 if ANY of:
- There are no deferred concerns (all concerns passed the threshold or there were no concerns)
- All deferred concerns are below `minSeverity` (they are silently ignored, not presented to the user)
- `TRUST == "high"` (high-trust mode auto-creates issues per Step 5 and proceeds without user interaction)

## Step 7: Squash Commits by Topic Before Review (MANDATORY)

**MANDATORY: Delegate rebase, squash, and STATE.md closure verification to a squash subagent.** This step must not be
skipped — the approval gate (Step 9) checks that squash was executed and blocks proceeding if it was not.
This keeps the parent agent context lean by offloading git operations to a dedicated haiku-model subagent.

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

**Invoke `cat:git-rebase-agent`:**
```
Skill("cat:git-rebase-agent", args="{WORKTREE_PATH} {TARGET_BRANCH}")
```

**If rebase reports CONFLICT:**
- Examine the conflicting files reported by cat:git-rebase-agent
- Resolve each conflict
- Stage resolved files and continue the rebase
- Delete the backup branch created by cat:git-rebase-agent after resolution
- Continue to Step 9 (Approval Gate)

**If rebase reports OK:**
- Delete the backup branch created by cat:git-rebase-agent
- Continue to Step 9 (Approval Gate)

**If rebase reports ERROR:**
- Output the error message
- Restore from the backup branch if needed
- STOP — do not proceed to approval gate until the error is resolved

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

### If trust == "high"

Skip approval gate. Continue directly to Step 10 (merge).

### If trust == "low" or trust == "medium"

**STOP HERE for user approval.** Do NOT proceed to merge automatically.

### Check for Prior Direct Approval

Before presenting AskUserQuestion, check whether the user has already typed an explicit approval in the current
conversation. Scan recent conversation messages for user messages containing both "approve" and "merge" (e.g.,
"approve and merge", "approve merge", "approved merge").

**If direct approval is detected:** Skip AskUserQuestion and proceed directly to Step 10 (merge). The
PreToolUse hook reads the session JSONL file and will recognize the user's direct message as approval.

**If no direct approval is detected:** Continue to the approval gate below.

**MANDATORY — PATIENCE MATRIX MUST PRECEDE APPROVAL GATE:** Before presenting the approval gate, the patience matrix
workflow (Step 4/5) MUST have already executed and produced `ALL_CONCERNS`, `FIXED_CONCERNS`, and `DEFERRED_CONCERNS`
lists. The approval gate displays these lists — it does NOT drive concern handling decisions.

**Do NOT ask the user how to handle concerns.** Concern handling is determined automatically by the patience matrix
based on severity, benefit, cost, and patience level. Users are shown the results at the approval gate, not asked to
decide in advance.

**If you are about to present the approval gate without having run the patience matrix: STOP.** Return to Step 4,
execute the patience matrix, then resume from Step 5 onward before proceeding to the approval gate.

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

2. **Display commit summary** — list commits since target branch:
   ```bash
   cd "${WORKTREE_PATH}" && git log --oneline ${TARGET_BRANCH}..HEAD
   ```

3. **Display issue goal** — extract the first non-empty line after `## Goal` in PLAN.md:
   ```bash
   ISSUE_GOAL=$(grep -A1 "^## Goal" "${ISSUE_PATH}/PLAN.md" | tail -n1)
   ```
   Display this to the user.

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
  skill: "cat:stakeholder-concern-box-agent"
  args: "${SEVERITY} ${STAKEHOLDER} ${CONCERN_DESCRIPTION} ${FILE_LOCATION}"
```

**Deferred concerns** (cost/benefit below patience threshold — not acted on in this issue):

For each concern deferred based on the cost/benefit analysis, render a concern box showing the benefit, cost, and
threshold values so the user understands why each was deferred:

```
Skill tool:
  skill: "cat:stakeholder-concern-box-agent"
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

**Empty answers detection:** If `toolUseResult.answers` is an empty object `{}`, no selection was recorded. The
visible signal is "User has answered your questions: ." with nothing after the colon. Treat this identically to no
response — re-present the approval gate. Unknown consent = No consent = STOP.

**If the user rejects the AskUserQuestion tool call** (e.g., to invoke `/cat:learn` or another skill), the approval
gate was NOT answered. After any interrupting skill completes, return to Step 9 and re-present the approval gate. Do
NOT proceed to merge, release the lock, remove the worktree, or invoke work-complete without explicit user selection.

**If approved:** Continue to Step 10

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
       TARGET_BRANCH: ${TARGET_BRANCH}

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
         implementation commit in Step 7. Do NOT use `test:` as an independent commit
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
     skill: "cat:stakeholder-review-agent"
     args: "${ISSUE_ID} ${WORKTREE_PATH} ${VERIFY} ${ALL_COMMITS_COMPACT}"
   ```
   **The review MUST be re-run to:**
   - Verify the concerns were actually resolved
   - Detect new concerns introduced by the fixes
   - Provide updated results to the user at the approval gate
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

**Exit the worktree directory before spawning the merge subagent:**

```bash
cd "${CLAUDE_PROJECT_DIR}"
```

**Context-Aware Merge Delegation:**

Always delegate the merge phase to a work-merge subagent via the Task tool. This is mandatory when main agent context
exceeds 37k tokens to prevent session compaction during git operations (rebase, squash, conflict resolution). Even when
context is below the threshold, delegation keeps the main agent context lean.

**Pre-merge approval verification (when trust != "high"):**

Before spawning the merge subagent, verify that user approval was obtained in Step 9. This proactive check
eliminates wasted Task calls that would otherwise be blocked by the PreToolUse hook.

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

Spawn a merge subagent using the **Task tool** (NOT the Skill tool — the Skill tool only loads skill
content and does NOT execute the merge):

```
Task tool:
  description: "Merge: squash, merge, cleanup"
  subagent_type: "cat:work-merge-agent"
  prompt: |
    Execute the merge phase for issue ${ISSUE_ID}.

    ## Configuration
    SESSION_ID: $CLAUDE_SESSION_ID
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    BRANCH: ${BRANCH}
    TARGET_BRANCH: ${TARGET_BRANCH}
    COMMITS: ${commits_json}

    Load and follow: @${CLAUDE_PLUGIN_ROOT}/skills/work-merge-agent/SKILL.md

    Return JSON per the output contract in the skill.
```

### Handle Merge Result

Parse merge result:

- **MERGED**: Continue to post-merge verification below
- **CONFLICT**: Return FAILED with conflict details
- **ERROR**: Return FAILED with error

### Post-Merge Verification (BLOCKING — M447)

Before proceeding to Step 11, verify the merge actually occurred by checking that `TARGET_BRANCH`
now contains the squashed commit:

```bash
# Verify the merge commit is reachable from TARGET_BRANCH
# git -C is intentional here: the worktree is already removed at this point,
# so we must run from the main project directory.
SQUASH_HASH=$(git -C "${CLAUDE_PROJECT_DIR}" rev-parse HEAD 2>/dev/null)
MERGED=$(git -C "${CLAUDE_PROJECT_DIR}" branch --contains "${SQUASH_HASH}" 2>/dev/null \
  | grep -c "${TARGET_BRANCH}" || true)
if [[ "${MERGED}" -eq 0 ]]; then
  echo "ERROR: Merge not confirmed — ${SQUASH_HASH} is not reachable from ${TARGET_BRANCH}."
  echo "The merge phase may have failed silently. Do NOT invoke work-complete."
  echo "Re-run Step 10 using the Task tool."
  exit 1
fi
```

If verification fails: STOP — do NOT invoke `work-complete`. The merge did not happen.
Re-spawn the merge subagent using the Task tool.

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

The workflow has three distinct rejection states that can interrupt mid-workflow execution (Steps 4, 5, and 9).
Each requires a specific response. Steps 4 and 5 reference this section for full handling rules.

### PARTIAL Verification Result (Step 4)

When the verify subagent returns `"status": "PARTIAL"`, some post-conditions are unmet but none are
fully missing. STOP — do not proceed to Step 5 (stakeholder review). Re-enter the INCOMPLETE fix loop
(described in Step 4) to address unmet post-conditions first.

Do NOT carry partially-met post-conditions into the review phase — the stakeholder review assumes the
implementation is complete.

### Rejected Stakeholder Review (Step 5)

When the stakeholder review returns `REVIEW_STATUS == "CONCERNS_FOUND"` with FIX-marked concerns,
**automatically enter the re-work loop without asking the user**.

The user approved the workflow when they invoked `/cat:work`. The re-work loop is a mandatory quality
gate, not an optional step. Do NOT:
- Ask "should I fix these concerns?"
- Present the concerns and wait for guidance
- Skip to the approval gate without fixing

Always proceed directly into the auto-fix loop (Step 5), fix concerns, re-run the review, and continue
until either all FIX-marked concerns are resolved or the iteration limit is reached.

### User Rejects Approval Gate (Step 9)

When the user rejects the AskUserQuestion tool call — for example, by invoking `/cat:learn`, asking a
question, or requesting changes without selecting an option — the approval gate was **NOT answered**.

**Required response:** Re-present the full approval gate in the NEXT response after any interrupting
action completes. This includes:
1. Re-running `cat:get-diff` to display current changes
2. Re-displaying the commit summary, issue goal, execution summary, E2E summary, and review results
3. Re-invoking AskUserQuestion with the same options

Do NOT:
- Proceed to merge after an unanswered gate
- Release the lock or remove the worktree
- Invoke `work-complete` without explicit user selection
- Skip the gate because "the user probably meant to approve"

Fail-fast principle: Unknown consent = No consent = STOP and re-present.

## Error Handling

If any phase fails:

1. Capture error message and phase name
2. Restore working directory: `cd "${CLAUDE_PROJECT_DIR}"`
3. Attempt lock release: `"${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${ISSUE_ID}" "$CLAUDE_SESSION_ID"`
4. Return FAILED status with actual error details

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
- [ ] Main Agent Waves skills invoked directly at main agent level before delegation
- [ ] Approval gates respected based on trust level
- [ ] Progress banners displayed at phase transitions
- [ ] Lock released on completion or error
- [ ] Results collected and returned as JSON
