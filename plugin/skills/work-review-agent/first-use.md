<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Phase: Review

Review phase for `/cat:work`. Runs stakeholder review (Step 5) and deferred concern wizard (Step 6).

## Arguments Format

```
<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <all_commits_compact> <trust> <verify>
```

| Position | Name | Example |
|----------|------|---------|
| 1 | catAgentId | agent ID passed through from parent |
| 2 | issue_id | `2.1-issue-name` |
| 3 | issue_path | `/workspace/.claude/cat/issues/v2/v2.1/issue-name` |
| 4 | worktree_path | `${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/cat/worktrees/2.1-issue-name` |
| 5 | issue_branch | `2.1-issue-name` |
| 6 | target_branch | `v2.1` |
| 7 | all_commits_compact | compact format `hash:type,hash:type` |
| 8 | trust | `medium` |
| 9 | verify | `changed` |

## Output Contract

Return JSON when complete:

```json
{
  "status": "REVIEW_PASSED|CONCERNS_FOUND",
  "all_concerns": [...],
  "fixed_concerns": [...],
  "deferred_concerns": [...],
  "all_commits_compact": "hash:type,hash:type"
}
```

## Configuration

```bash
read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH ALL_COMMITS_COMPACT TRUST VERIFY <<< "$ARGUMENTS"
PLAN_MD="${ISSUE_PATH}/PLAN.md"
```

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

**CRITICAL: The auto-fix loop applies ONLY to spawning fix subagents. Step 6 (Deferred Concern
Review) MUST still be executed after the loop completes.**

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
User approval is a SEPARATE gate in the merge phase.

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

This step runs BEFORE the Squash Commits by Topic step and BEFORE the Approval Gate, giving the user a
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

Skip this step entirely and proceed to return if ANY of:
- There are no deferred concerns (all concerns passed the threshold or there were no concerns)
- All deferred concerns are below `minSeverity` (they are silently ignored, not presented to the user)
- `TRUST == "high"` (high-trust mode auto-creates issues per Step 5 and proceeds without user interaction)

## Rejection Handling

### Rejected Stakeholder Review

When `REVIEW_STATUS == "CONCERNS_FOUND"` with FIX-marked concerns, **automatically enter the re-work loop**.
Do NOT ask "should I fix these?", present and wait, or skip to approval gate. Proceed directly into the
auto-fix loop, fix concerns, re-run review, until all FIX-marked concerns resolved or iteration limit reached.
