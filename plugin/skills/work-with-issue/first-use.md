---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Work With Issue: Direct Phase Orchestration

Execute all work phases (implement, confirm, review, merge) with the main agent directly orchestrating each phase.
Shows progress banners at phase transitions while maintaining clean user output.

**Architecture:** This skill is invoked by `/cat:work` after task discovery (Phase 1). The main agent
directly orchestrates all phases:
- Implement: Spawn implementation subagent
- Confirm: Invoke verify-implementation skill
- Review: Invoke stakeholder-review skill
- Merge: Spawn merge subagent

This eliminates nested subagent spawning (which is architecturally impossible) and enables proper
skill invocation at the main agent level.

## Arguments Format

The main `/cat:work` skill invokes this with JSON-encoded arguments:

```json
{
  "issue_id": "2.1-issue-name",
  "issue_path": "/workspace/.claude/cat/issues/v2/v2.1/issue-name",
  "worktree_path": "/workspace/.claude/cat/worktrees/2.1-issue-name",
  "branch": "2.1-issue-name",
  "base_branch": "v2.1",
  "estimated_tokens": 45000,
  "trust": "medium",
  "verify": "changed",
  "auto_remove": true
}
```

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

Extract configuration from arguments:

```bash
# Parse JSON arguments
ISSUE_ID=$(echo "$ARGUMENTS" | jq -r '.issue_id')
ISSUE_PATH=$(echo "$ARGUMENTS" | jq -r '.issue_path')
WORKTREE_PATH=$(echo "$ARGUMENTS" | jq -r '.worktree_path')
BRANCH=$(echo "$ARGUMENTS" | jq -r '.branch')
BASE_BRANCH=$(echo "$ARGUMENTS" | jq -r '.base_branch')
ESTIMATED_TOKENS=$(echo "$ARGUMENTS" | jq -r '.estimated_tokens')
TRUST=$(echo "$ARGUMENTS" | jq -r '.trust')
VERIFY=$(echo "$ARGUMENTS" | jq -r '.verify')
AUTO_REMOVE=$(echo "$ARGUMENTS" | jq -r '.auto_remove')
HAS_EXISTING_WORK=$(echo "$ARGUMENTS" | jq -r '.has_existing_work // false')
EXISTING_COMMITS=$(echo "$ARGUMENTS" | jq -r '.existing_commits // 0')
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
python3 -c "
import json, os, sys
lock_file = '${CLAUDE_PROJECT_DIR}/.claude/cat/locks/${ISSUE_ID}.lock'
expected = os.environ.get('CLAUDE_SESSION_ID', '')
if not expected:
    print('ERROR: CLAUDE_SESSION_ID environment variable is not set')
    sys.exit(1)
try:
    with open(lock_file) as f:
        session = json.load(f).get('session_id', '')
    if session == expected:
        print('OK: Lock verified for current session')
    else:
        print(f'ERROR: Lock for ${ISSUE_ID} belongs to session {session}, not {expected}')
        sys.exit(1)
except FileNotFoundError:
    print(f'ERROR: No lock file found for ${ISSUE_ID}. Task was not properly prepared.')
    sys.exit(1)
"
```

If lock ownership verification fails, STOP immediately and return FAILED status. Do NOT proceed
to execution — another session owns this task.

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

### Skip if Resuming

If `HAS_EXISTING_WORK == true`:
- Output: "Resuming task with existing work - skipping to verification"
- Skip to Step 4

### Read PLAN.md and Identify Skills

Read the execution steps from PLAN.md to understand what needs to be done:

```bash
# Read PLAN.md execution steps
PLAN_MD="${ISSUE_PATH}/PLAN.md"
EXECUTION_STEPS=$(sed -n '/## Execution Steps/,/^## /p' "$PLAN_MD" | head -n -1)
TASK_GOAL=$(sed -n '/## Goal/,/^## /p' "$PLAN_MD" | head -n -1 | tail -n +2)
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

Spawn a subagent to implement the task:

```
Task tool:
  description: "Execute: implement ${ISSUE_ID}"
  subagent_type: "cat:work-execute"
  model: "sonnet"
  prompt: |
    Execute the implementation for task ${ISSUE_ID}.

    ## Task Configuration
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    BRANCH: ${BRANCH}
    BASE_BRANCH: ${BASE_BRANCH}
    ESTIMATED_TOKENS: ${ESTIMATED_TOKENS}
    TRUST_LEVEL: ${TRUST}

    ## Task Goal (from PLAN.md)
    ${TASK_GOAL}

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
      "task_metrics": {},
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

### Invoke Verify Implementation

Invoke the verify-implementation skill at main agent level:

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

### Handle Verification Result

Parse verification result to determine if all post-conditions were satisfied.

**If all criteria Done:**
- Output: "All post-conditions verified - proceeding to review"
- Continue to Step 5

**If any criteria Missing:**
- Extract missing criteria details
- Spawn implementation subagent to fix gaps (max 2 iterations):
  ```
  Task tool:
    description: "Fix missing post-conditions (iteration ${ITERATION})"
    subagent_type: "cat:work-execute"
    model: "sonnet"
    prompt: |
      Fix the following missing post-conditions for task ${ISSUE_ID}.

      ## Task Configuration
      ISSUE_ID: ${ISSUE_ID}
      WORKTREE_PATH: ${WORKTREE_PATH}
      BRANCH: ${BRANCH}

      ## Missing Post-conditions
      ${missing_criteria_formatted}

      ## Instructions
      - Work in the worktree at ${WORKTREE_PATH}
      - Implement each missing post-condition according to PLAN.md
      - Commit your fixes with appropriate commit messages
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
- Re-run verify-implementation after fixes
- If still Missing after 2 iterations, continue to Step 5 with gaps noted

**If any criteria Partial:**
- Note partial status in metrics
- Continue to E2E verification below

### End-to-End Verification

**After acceptance criteria verification, run E2E testing to verify the change works in its real environment.**

This applies to all implementation issue types (feature, bugfix, refactor, performance). Skip for docs and config
issues that don't change runtime behavior.

**Step 1: Check for E2E criteria in PLAN.md:**

```bash
grep -i "e2e\|end.to.end\|end-to-end" "${ISSUE_PATH}/PLAN.md"
```

**If no E2E criteria found in PLAN.md:**
- Output: "Warning: No E2E post-conditions found in PLAN.md for this issue."
- Output: "Running E2E verification based on the feature's goal."
- Proceed to Step 2 using the goal from PLAN.md to determine what to test.

**If E2E criteria found:**
- Extract the E2E criteria text.
- Proceed to Step 2 using those criteria.

**Step 2: Run E2E verification:**

**Isolation requirement:** E2E tests must not impact other Claude instances or the main worktree. Always test using the
worktree's own built artifacts (jlink image, scripts) rather than the cached plugin installation. If the test requires
modifying shared state (e.g., updating the cached plugin, writing to shared config), explain the impact to the user via
AskUserQuestion and let them decide whether to proceed or skip E2E testing.

The main agent (not a subagent) must verify the change works in its real environment. This means:
- If the change adds/modifies a hook: build the jlink image from the worktree and test the binary with realistic input
- If the change adds/modifies a skill: invoke load-skill.sh from the worktree and confirm it produces expected output
- If the change modifies agent behavior: spawn a test subagent and verify the behavior
- If the change adds/modifies a CLI tool: run the tool from the worktree with test input and verify output
- If the change fixes a bug: reproduce the bug scenario and verify it no longer occurs

**Runtime invocation is required. Static file checks are not E2E testing.** Inspecting file contents
(grep for patterns, cat of file, reading the file) does NOT verify runtime behavior. Skill variable
expansion, argument passing, and output rendering only occur when the skill is actually invoked. Always
run the artifact — do not substitute reading it for running it.

```bash
# Example: testing a hook binary (uses worktree's jlink, not cached plugin)
echo '{"test": "input"}' | "${WORKTREE_PATH}/client/target/jlink/bin/<hook-name>" 2>/dev/null

# Example: testing skill loading (uses worktree's scripts, not cached plugin)
"${WORKTREE_PATH}/plugin/scripts/load-skill.sh" "${WORKTREE_PATH}/plugin" "cat:<skill>" \
  "${CAT_AGENT_ID}" "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"
```

**If the jlink image is not built**, build it first:
```bash
"${WORKTREE_PATH}/client/build-jlink.sh"
```

**If E2E test requires shared state changes:**
```
AskUserQuestion:
  header: "E2E Test"
  question: "E2E testing for this feature requires [describe impact]. Proceed or skip?"
  options:
    - "Proceed with E2E test" (accept the impact)
    - "Skip E2E test" (proceed to review without E2E verification)
```

**If E2E test passes:** Continue to Step 5.

**If E2E test fails:**
- Spawn implementation subagent to fix (max 1 iteration)
- Re-run E2E test
- If still failing after fix attempt, note the failure and continue to Step 5 (stakeholder review
  may catch the issue, and the user can decide at the approval gate)

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

```
Skill tool:
  skill: "cat:stakeholder-review"
  args: |
    {
      "issue_id": "${ISSUE_ID}",
      "worktree_path": "${WORKTREE_PATH}",
      "verify_level": "${VERIFY}",
      "commits": ${execution_commits_json}
    }
```

The stakeholder-review skill will spawn its own reviewer subagents and return aggregated results.

### Handle Review Result

Parse review result and filter false positives (concerns from reviewers that read base branch instead of worktree).

**Read auto-fix level and proceed limits from config:**

```bash
# Read reviewThresholds from .claude/cat/cat-config.json
# Defaults match current hardcoded behavior if config is missing or field is absent
AUTOFIX_LEVEL="high_and_above"
PROCEED_CRITICAL=0
PROCEED_HIGH=0
PROCEED_MEDIUM=0
PROCEED_LOW=0

CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/cat-config.json"
if [[ -f "$CONFIG_FILE" ]]; then
    # Extract reviewThresholds.autofix using grep/sed (no jq available)
    AUTOFIX_RAW=$(grep -o '"autofix"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONFIG_FILE" | head -1 | \
        sed 's/.*"autofix"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    if [[ -n "$AUTOFIX_RAW" ]]; then
        AUTOFIX_LEVEL="$AUTOFIX_RAW"
    fi

    # Extract reviewThresholds.proceed values
    # The proceed block looks like: "proceed": { "critical": 0, "high": 3, ... }
    PROCEED_SECTION=$(awk '/"proceed"/{found=1} found{print; if(/\}/) {count++; if(count>=2) exit}}' "$CONFIG_FILE" 2>/dev/null || echo "")

    extract_proceed_value() {
        local key="$1"
        local default_val="$2"
        local val
        val=$(echo "$PROCEED_SECTION" | grep -o "\"${key}\"[[:space:]]*:[[:space:]]*-\?[0-9]*" | head -1 | grep -o '-\?[0-9]*$')
        if [[ -n "$val" ]]; then
            echo "$val"
        else
            echo "$default_val"
        fi
    }

    PROCEED_CRITICAL=$(extract_proceed_value "critical" 0)
    PROCEED_HIGH=$(extract_proceed_value "high" 0)
    PROCEED_MEDIUM=$(extract_proceed_value "medium" 0)
    PROCEED_LOW=$(extract_proceed_value "low" 0)
fi

# Determine minimum severity to auto-fix based on AUTOFIX_LEVEL:
# "all"            -> auto-fix CRITICAL, HIGH, and MEDIUM
# "high_and_above" -> auto-fix CRITICAL and HIGH (default)
# "critical"       -> auto-fix CRITICAL only
# "none"           -> never auto-fix
#
# Use PROCEED_* to decide when to block (stop auto-fix loop) vs proceed to user approval.
# A value of 0 means reject if any concerns remain at that severity.
# A value of 0 means none are allowed (must block if any remain).
```

**Auto-fix loop for concerns (based on configured autofix level):**

Initialize loop counter: `AUTOFIX_ITERATION=0`

**While concerns exist at or above the configured auto-fix threshold and AUTOFIX_ITERATION < 3:**

The auto-fix threshold is determined by `AUTOFIX_LEVEL`:
- `"all"`: loop while CRITICAL, HIGH, or MEDIUM concerns exist
- `"high_and_above"`: loop while CRITICAL or HIGH concerns exist (default)
- `"critical"`: loop while CRITICAL concerns exist
- `"none"`: skip auto-fix loop entirely (proceed directly to approval gate)

1. Increment iteration counter: `AUTOFIX_ITERATION++`
2. Extract concerns at or above the auto-fix threshold (severity, description, location, recommendation)
3. Spawn implementation subagent to fix the concerns:
   ```
   Task tool:
     description: "Fix review concerns (iteration ${AUTOFIX_ITERATION})"
     subagent_type: "cat:work-execute"
     model: "sonnet"
     prompt: |
       Fix the following stakeholder review concerns for task ${ISSUE_ID}.

       ## Task Configuration
       ISSUE_ID: ${ISSUE_ID}
       WORKTREE_PATH: ${WORKTREE_PATH}
       BRANCH: ${BRANCH}
       BASE_BRANCH: ${BASE_BRANCH}

       ## HIGH+ Concerns to Fix
       ${concerns_formatted}

       ## Instructions
       - Work in the worktree at ${WORKTREE_PATH}
       - Fix each concern according to the recommendation
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
4. Re-run stakeholder review:
   ```
   Skill tool:
     skill: "cat:stakeholder-review"
     args: |
       {
         "issue_id": "${ISSUE_ID}",
         "worktree_path": "${WORKTREE_PATH}",
         "verify_level": "${VERIFY}",
         "commits": ${all_commits_json}
       }
   ```
5. Parse new review result
6. If HIGH+ concerns remain, continue loop (if under iteration limit)

**If HIGH+ concerns persist after 3 iterations:**
- Note that auto-fix reached iteration limit
- Store all remaining concerns for display at approval gate
- Continue to Step 5

**If all concerns are MEDIUM or lower (or no concerns):**
- Store concerns for display at approval gate
- Continue to Step 5

**NOTE:** "REVIEW_PASSED" means stakeholder review passed, NOT user approval to merge.
User approval is a SEPARATE gate in Step 6.

## Step 6: Rebase and Squash Commits Before Review

**MANDATORY: Rebase the issue branch onto the base branch before presenting work for user review.** The base branch may
have advanced since the worktree was created (e.g., learning commits, other merges). Rebasing ensures the user reviews
changes against the current base, not a stale snapshot, and that squashing only captures task changes:

```bash
git -C ${WORKTREE_PATH} rebase ${BASE_BRANCH}
```

Then use `/cat:git-squash` to consolidate commits:

- All implementation work + STATE.md closure into 1 feature/bugfix commit
- Target: 1 commit (STATE.md belongs with implementation, not in a separate commit)

**Commit message for squash:** Use the primary implementation commit's message from the execution result. If multiple
topics exist, use the most significant commit's message. The squash script requires the message as its second argument:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/git-squash" "${BASE_BRANCH}" "<commit message from execution result>" "${WORKTREE_PATH}"
```

Do NOT use generic messages like "squash commit", "squash commits", or "combined work". The main agent already has the
commits from the execution result — reuse the primary implementation commit's message as the squash message

**CRITICAL: STATE.md file grouping:**
- STATE.md status changes belong IN THE SAME COMMIT as the implementation work
- Do NOT create separate `planning:` or `config:` commits for STATE.md updates
- Commit type should match the implementation work (`feature:`, `bugfix:`, `config:`, etc.)
- Example: `feature: add user authentication` includes STATE.md closure in that commit

This ensures the user reviews clean commit history, not intermediate implementation state.

### Verify Squash Quality

**After squashing, verify that no further squashing is needed.**

Run this check against the commits on the branch:

```bash
git -C ${WORKTREE_PATH} log --format="%H %s" ${BASE_BRANCH}..HEAD
```

**Indicators that further squashing is needed** (any one triggers):

1. **Same type prefix + overlapping files:** Two or more commits share the same type prefix (e.g., both `feature:`)
   AND modify at least one file in common. Check with:
   ```bash
   # For each pair of same-prefix commits, check file overlap
   comm -12 <(git show --name-only --format="" COMMIT_A | sort) \
            <(git show --name-only --format="" COMMIT_B | sort)
   ```

2. **Iterative commit messages:** Commit messages containing words like "fix", "update", "address", "correct",
   "adjust" that reference work done in an earlier commit on the same branch.

3. **Refactor touching same files as feature:** A `refactor:` commit modifies the same files as a preceding
   `feature:` or `bugfix:` commit, suggesting the refactor is part of the same work.

**If any indicator triggers:** Return to squash step and consolidate the affected commits.

**If no indicators trigger:** Proceed to STATE.md closure verification.

### Verify STATE.md Closure (BLOCKING)

**Before proceeding to the approval gate, verify that STATE.md is closed in the final commit.**

```bash
# Check STATE.md status in the HEAD commit
STATE_RELATIVE=$(realpath --relative-to="${WORKTREE_PATH}" "${ISSUE_PATH}/STATE.md")
STATUS_IN_COMMIT=$(git -C "${WORKTREE_PATH}" show "HEAD:${STATE_RELATIVE}" 2>/dev/null | \
  grep -i "^\*\*Status:\*\*\|^- \*\*Status:\*\*" | head -1)
echo "STATE.md status in HEAD commit: ${STATUS_IN_COMMIT}"
```

**Blocking condition:** If STATE.md status is NOT `closed` in HEAD, STOP and fix before presenting the approval gate:

1. Open `${ISSUE_PATH}/STATE.md` and set `Status: closed`, `Progress: 100%`
2. Amend the most recent implementation commit to include the STATE.md change:
   ```bash
   git -C "${WORKTREE_PATH}" add "${ISSUE_PATH}/STATE.md"
   git -C "${WORKTREE_PATH}" commit --amend --no-edit
   ```
3. Re-run squash quality verification above

**Why this check exists:** The approval gate presents final work for user review. STATE.md must already
reflect the closed state at review time — not in a separate follow-up commit.

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

### Present Changes Before Approval Gate (BLOCKING)

**MANDATORY: Render the diff and output the full change summary BEFORE invoking AskUserQuestion.**

Context compaction can occur at any point in a long session. When the conversation is compacted, the user's
visible context resets — they will only see output from the current turn onward. If AskUserQuestion is invoked
without first presenting the changes in the same turn, the user sees an approval gate with no visible context
about what they are approving.

**Required pre-gate output sequence (all mandatory, in this order):**

1. **Render diff** — invoke `cat:render-diff` to display the changes:
   ```
   Skill tool:
     skill: "cat:render-diff"
   ```

2. **Display commit summary** — list commits since base branch:
   ```bash
   git -C ${WORKTREE_PATH} log --oneline ${BASE_BRANCH}..HEAD
   ```

3. **Display task goal** (from PLAN.md)

4. **Display execution summary** (commits count, files changed)

5. **Display E2E testing summary** (see below)

6. **Display review results with ALL concern details** (see below)

Only after all six items above have been output in the current conversation turn may you invoke AskUserQuestion.

**MANDATORY: Display E2E testing summary before the approval gate.** Explain what E2E tests were run, what they
verified, and what the results were. This helps the user determine whether the feature works in its real environment.
If E2E testing was skipped (no E2E criteria in PLAN.md, or non-feature issue), state that explicitly.

**MANDATORY: Display ALL stakeholder concerns before the approval gate**, regardless of severity.
Users need full visibility into review findings to make informed merge decisions. For each concern
(CRITICAL, HIGH, MEDIUM, or LOW), render a concern box:

```
Skill tool:
  skill: "cat:stakeholder-concern-box"
  args: "${SEVERITY} ${STAKEHOLDER} ${CONCERN_DESCRIPTION} ${FILE_LOCATION}"
```

Do NOT suppress MEDIUM or LOW concerns. The auto-fix loop only addresses HIGH+ concerns automatically,
but all concerns must be visible to the user at the approval gate.

**Determine approval options based on remaining concerns:**

If MEDIUM+ concerns exist:
```
AskUserQuestion:
  header: "${ISSUE_ID}"
  question: "Ready to merge ${ISSUE_ID}? (Goal: ${TASK_GOAL})"
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
  question: "Ready to merge ${ISSUE_ID}? (Goal: ${TASK_GOAL})"
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
1. Extract MEDIUM+ concerns
2. Spawn implementation subagent to fix:
   ```
   Task tool:
     description: "Fix remaining concerns (user-requested)"
     subagent_type: "cat:work-execute"
     model: "sonnet"
     prompt: |
       Fix the following stakeholder review concerns for task ${ISSUE_ID}.

       ## Task Configuration
       ISSUE_ID: ${ISSUE_ID}
       WORKTREE_PATH: ${WORKTREE_PATH}
       BRANCH: ${BRANCH}
       BASE_BRANCH: ${BASE_BRANCH}

       ## MEDIUM+ Concerns to Fix
       ${concerns_formatted}

       ## Instructions
       - Work in the worktree at ${WORKTREE_PATH}
       - Fix each concern according to the recommendation
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
3. **MANDATORY: Re-run stakeholder review after fixes:**
   ```
   Skill tool:
     skill: "cat:stakeholder-review"
     args: |
       {
         "issue_id": "${ISSUE_ID}",
         "worktree_path": "${WORKTREE_PATH}",
         "verify_level": "${VERIFY}",
         "commits": ${all_commits_json}
       }
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

Spawn a merge subagent (haiku model - mechanical operations only):

```
Task tool:
  description: "Merge: squash, merge, cleanup"
  subagent_type: "cat:work-merge"
  model: "haiku"
  prompt: |
    Execute the merge phase for task ${ISSUE_ID}.

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
2. Attempt lock release: `${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh release "${CLAUDE_PROJECT_DIR}" "${ISSUE_ID}"
   "$CLAUDE_SESSION_ID"`
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
