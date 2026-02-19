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
import json, sys
lock_file = '${CLAUDE_PROJECT_DIR}/.claude/cat/locks/${ISSUE_ID}.lock'
expected = '${CLAUDE_SESSION_ID}'
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

**This step confirms PLAN.md acceptance criteria were implemented before stakeholder quality review.**

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

Parse verification result to determine if all acceptance criteria were satisfied.

**If all criteria Done:**
- Output: "All acceptance criteria verified - proceeding to review"
- Continue to Step 5

**If any criteria Missing:**
- Extract missing criteria details
- Spawn implementation subagent to fix gaps (max 2 iterations):
  ```
  Task tool:
    description: "Fix missing acceptance criteria (iteration ${ITERATION})"
    subagent_type: "cat:work-execute"
    model: "sonnet"
    prompt: |
      Fix the following missing acceptance criteria for task ${ISSUE_ID}.

      ## Task Configuration
      ISSUE_ID: ${ISSUE_ID}
      WORKTREE_PATH: ${WORKTREE_PATH}
      BRANCH: ${BRANCH}

      ## Missing Acceptance Criteria
      ${missing_criteria_formatted}

      ## Instructions
      - Work in the worktree at ${WORKTREE_PATH}
      - Implement each missing criterion according to PLAN.md
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
- Continue to Step 5

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

**Auto-fix loop for HIGH+ concerns:**

Initialize loop counter: `AUTOFIX_ITERATION=0`

**While any concerns have severity >= HIGH and AUTOFIX_ITERATION < 3:**

1. Increment iteration counter: `AUTOFIX_ITERATION++`
2. Extract HIGH+ concerns (severity, description, location, recommendation)
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
       - Commit your fixes with appropriate commit messages
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

## Step 6: Squash Commits Before Review

**Squash worktree commits by topic into clean, reviewable commits before presenting the approval gate.**

**Rebase onto current base first.** The base branch may have advanced since the worktree was
created (e.g., learning commits, other merges). Rebase ensures squashing only captures task changes:

```bash
git -C ${WORKTREE_PATH} rebase ${BASE_BRANCH}
```

Then use `/cat:git-squash` to consolidate commits:

- All implementation work + STATE.md closure into 1 feature/bugfix commit
- Target: 1 commit (STATE.md belongs with implementation, not in a separate commit)

**Commit message for squash:** Use the primary implementation commit's message from the execution result. If multiple
topics exist, use the most significant commit's message. The squash script requires the message as its second argument:

```bash
"$(git rev-parse --show-toplevel)/plugin/scripts/git-squash-quick.sh" "${BASE_BRANCH}" "<commit message from execution result>" "${WORKTREE_PATH}"
```

Do NOT use generic messages like "squash commit", "squash commits", or "combined work". The main agent already has the
commits from the execution result — reuse the primary implementation commit's message as the squash message

**CRITICAL: STATE.md file grouping:**
- STATE.md status changes belong IN THE SAME COMMIT as the implementation work
- Do NOT create separate `planning:` or `config:` commits for STATE.md updates
- Commit type should match the implementation work (`feature:`, `bugfix:`, `config:`, etc.)
- Example: `feature: add user authentication` includes STATE.md closure in that commit

This ensures the user reviews clean commit history, not intermediate implementation state.

## Step 7: Approval Gate

**CRITICAL: This step is MANDATORY when trust != "high".**

**Enforced by hook M480:** PreToolUse hook on Task tool blocks work-merge spawn when trust=medium/low
and no explicit user approval is detected in session history.

### If trust == "high"

Skip approval gate. Continue directly to Step 8 (merge).

### If trust == "low" or trust == "medium"

**STOP HERE for user approval.** Do NOT proceed to merge automatically.

Present a summary and ask for approval:

```
Display task goal from PLAN.md
Display execution summary (commits, files changed)
Display review results with ALL concern details (see below)
```

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
       - Commit your fixes with appropriate commit messages
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
    SESSION_ID: ${CLAUDE_SESSION_ID}
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
   "${CLAUDE_SESSION_ID}"`
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
