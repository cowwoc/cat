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

Parse arguments and display the **Reviewing phase** banner in a chained call:

```bash
read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH ALL_COMMITS_COMPACT TRUST VERIFY <<< "$ARGUMENTS" && \
PLAN_MD="${ISSUE_PATH}/PLAN.md" && \
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase reviewing
```

**Validate TRUST argument:**

TRUST must be one of: `low`, `medium`, `high`. If TRUST is empty or not one of these values, STOP immediately:
```
ERROR: Invalid or missing TRUST argument: "${TRUST}". Expected one of: low, medium, high.
```

Also cross-check TRUST against cat-config.json. **This cross-check is MANDATORY regardless of whether the file
exists.** If the file is absent, treat the effective trust as `low` (most restrictive) and fail if the argument
differs. There is no scenario in which a missing file permits the caller-supplied TRUST value to be used unchecked.

```bash
CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/cat-config.json"
# File must exist for the cross-check. If absent, the effective trust defaults to "low".
if [[ ! -f "$CONFIG_FILE" ]]; then
    CONFIG_TRUST="low"
else
    CONFIG_TRUST=$(grep -o '"trust"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONFIG_FILE" | head -1 | \
        sed 's/.*"trust"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    # If the trust field is absent from an existing cat-config.json, treat it as "low" (most restrictive).
    # This prevents trust injection via argument tampering when the field has not been explicitly configured.
    if [[ -z "$CONFIG_TRUST" ]]; then
        CONFIG_TRUST="low"
    fi
fi
if [[ "$TRUST" != "$CONFIG_TRUST" ]]; then
    echo "ERROR: TRUST argument '${TRUST}' differs from effective trust '${CONFIG_TRUST}'." >&2
    echo "The TRUST argument must match the configured trust level. Update cat-config.json or correct the argument." >&2
    exit 1
fi
```

If TRUST differs from the effective config trust (file absent → `low`; file present but field absent → `low`; field
present → its value), STOP immediately. Do NOT proceed with the injected TRUST value under any circumstance.

**Read minSeverity from config:**

`minSeverity` controls the minimum concern severity that is tracked and presented to the user. Concerns strictly
below `minSeverity` are silently ignored — they never appear in Step 6, are never tracked as issues, and are
never included in `deferred_concerns`.

Valid values (ordered low-to-high): `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.

Read it now, before Step 5:

```bash
CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/cat-config.json"
if [[ ! -f "$CONFIG_FILE" ]]; then
    echo "ERROR: cat-config.json not found at ${CONFIG_FILE}. Cannot proceed." >&2
    exit 1
fi

MIN_SEVERITY_RAW=$(grep -o '"minSeverity"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONFIG_FILE" | head -1 | \
    sed 's/.*"minSeverity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')

# Default is "LOW" when the field is absent (track everything by default)
MIN_SEVERITY="LOW"
if [[ -n "$MIN_SEVERITY_RAW" ]]; then
    VALID_MIN_SEVERITY=("LOW" "MEDIUM" "HIGH" "CRITICAL")
    MIN_SEVERITY_VALID=false
    for v in "${VALID_MIN_SEVERITY[@]}"; do
        if [[ "$MIN_SEVERITY_RAW" == "$v" ]]; then MIN_SEVERITY_VALID=true; break; fi
    done
    if [[ "$MIN_SEVERITY_VALID" != "true" ]]; then
        echo "ERROR: Invalid minSeverity value '${MIN_SEVERITY_RAW}' in ${CONFIG_FILE}. Expected one of: LOW, MEDIUM, HIGH, CRITICAL." >&2
        exit 1
    fi
    MIN_SEVERITY="$MIN_SEVERITY_RAW"
fi
```

Use `MIN_SEVERITY` everywhere the instructions reference `minSeverity`.

## Step 5: Review Phase (MANDATORY)

**If the command fails or produces no output**, STOP immediately:
```
FAIL: progress-banner launcher failed for phase 'reviewing'.
The jlink image may not be built. Run: mvn -f hooks/pom.xml verify
```
Do NOT skip the banner or continue without it.

### Skip Review if Configured

Skip if: `VERIFY == "none"`

If skipping, output: "Review skipped (verify: ${VERIFY})"

Note: `TRUST == "high"` does NOT skip review. Review is mandatory regardless of trust level.

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

**False-positive classification requires user confirmation, one concern at a time.** You MUST NOT silently discard
any concern by classifying it as a false positive on your own judgment. When you believe a concern may be a false
positive (i.e., the reviewer appears to have read the wrong branch), you MUST present **each concern individually**
via a separate AskUserQuestion and ask for explicit confirmation before discarding it. Grouping multiple concerns into
a single false-positive confirmation question is prohibited — each concern requires its own question. The question
must include: the concern text, the specific evidence that suggests the reviewer read the wrong branch, and an option
to keep the concern as real. If the user does not confirm the false-positive classification for a given concern, treat
that concern as real and process it through the normal pipeline.

**Parse Review Result:**

The `cat:stakeholder-review-agent` skill returns a JSON object. Extract the following fields:

- `REVIEW_STATUS` = `review_result.review_status` (e.g., `"REVIEW_PASSED"` or `"CONCERNS_FOUND"`)
- `ALL_CONCERNS` = `review_result.concerns[]` — the full list of concern objects returned by the review

Store `ALL_CONCERNS` for use in the auto-fix loop and the approval gate. Each concern object has fields:
`severity`, `stakeholder`, `location`, `explanation`, `recommendation`, and optionally `detail_file`.

**Read patience from config:**

```bash
# Read patience from .claude/cat/cat-config.json
# Default is "medium" if the config file exists but field is absent
# FAIL if the config file cannot be read, or if the value is present but invalid
PATIENCE_LEVEL="medium"

CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/cat-config.json"
if [[ ! -f "$CONFIG_FILE" ]]; then
    echo "ERROR: cat-config.json not found at ${CONFIG_FILE}. Cannot proceed." >&2
    exit 1
fi

PATIENCE_RAW=$(grep -o '"patience"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONFIG_FILE" | head -1 | \
    sed 's/.*"patience"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')

if [[ -n "$PATIENCE_RAW" ]]; then
    # Value is present — validate it
    VALID_PATIENCE=("low" "medium" "high")
    PATIENCE_VALID=false
    for v in "${VALID_PATIENCE[@]}"; do
        if [[ "$PATIENCE_RAW" == "$v" ]]; then PATIENCE_VALID=true; break; fi
    done
    if [[ "$PATIENCE_VALID" != "true" ]]; then
        echo "ERROR: Invalid patience value '${PATIENCE_RAW}' in ${CONFIG_FILE}. Expected one of: low, medium, high." >&2
        exit 1
    fi
    PATIENCE_LEVEL="$PATIENCE_RAW"
fi
# If PATIENCE_RAW is empty (field absent), PATIENCE_LEVEL remains "medium"
```

**Concern Decision Gate (trust=low only):**

After the patience matrix evaluates FIX/DEFER for each concern, the gate behavior depends on TRUST level:

- **trust=low**: Invoke AskUserQuestion tool with detailed FIX/DEFER summary (all fields: severity, stakeholder,
  location, decision, benefit, cost, threshold) and options:
  - "Proceed with these decisions (Recommended)"
  - "Let me change decisions"
- **trust=medium**: Print a non-interactive FIX/DEFER summary to the conversation, then proceed directly to the
  auto-fix loop without waiting for any user response. Do NOT use AskUserQuestion — this is informational output
  only, not a confirmation prompt. The summary MUST be printed before the loop begins; skipping it is a violation.

  **Required summary format (trust=medium):** Print one line per concern, in this exact structure:
  ```
  [SEVERITY] [stakeholder] @ [location] → [FIX|DEFER] (benefit=[N], cost=[N], threshold=[N])
  ```
  Every concern at or above `MIN_SEVERITY` MUST appear as its own line. A concern omitted from the list is treated
  as a violation equivalent to skipping the summary entirely. Aggregate placeholders such as "All concerns deferred"
  or "N concerns to fix" are NOT valid substitutes — each concern requires an individual entry with all six fields
  (severity, stakeholder, location, decision, benefit, cost, threshold). Missing any field on any line is also a
  violation.

  **Field value requirements:** Each field must contain the actual value derived from the concern data — not a
  placeholder, not "N/A", not "unknown", not an empty string. Specifically:
  - `SEVERITY`: must be one of `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` (from the concern object; default `MEDIUM` if
    absent from the concern)
  - `stakeholder`: must be the actual stakeholder name from the concern object
  - `location`: must be the actual file/line location from the concern object; if the concern object genuinely
    carries no location, write `(no location)` — this is the only permitted substitute
  - `FIX|DEFER`: must be the decision produced by the patience matrix for this concern
  - `benefit`: must be the numeric weight computed from the severity (CRITICAL=10, HIGH=6, MEDIUM=3, LOW=1)
  - `cost`: must be the numeric scope estimate (0, 1, 4, or 10) computed for this concern
  - `threshold`: must be `cost × patience_multiplier` computed for this concern

  A line that uses a placeholder value where a real value is required is treated as a missing-field violation.
- **trust=high**: Proceed directly to the auto-fix loop without printing a summary and without any user confirmation
  prompt.

A **valid selection** requires the `answer` field to exactly match one of the option strings (case-sensitive).
Any `answer` that does not exactly match one of the listed options is a **failed attempt**.

**Attempt limit (trust=low only):** Present the gate up to 3 times. If after 3 failed attempts no valid selection
is received, STOP and return:
```json
{
  "status": "CONCERNS_FOUND",
  "all_concerns": [...],
  "fixed_concerns": [],
  "deferred_concerns": [...],
  "all_commits_compact": "${ALL_COMMITS_COMPACT}"
}
```

Where `deferred_concerns` is populated with the full contents of `ALL_CONCERNS` (all concerns are treated as
deferred since no user decision was obtained). Do NOT return an empty `deferred_concerns` array when `all_concerns`
is non-empty.

After the user confirms (or modifies and confirms) at trust=low, proceed to the auto-fix loop below with the
final FIX/DEFER assignments. For trust=medium and trust=high, proceed directly to the auto-fix loop after
logging the decisions.

**Auto-fix loop for concerns marked as FIX:**

**Spawn fix subagents without asking the user during the auto-fix loop.** When stakeholder review returns
REJECTED, always enter the re-work loop — CRITICAL concerns must be fixed before merge. When review returns
CONCERNS_FOUND, concerns flow through the pipeline: `MIN_SEVERITY` filter (silently drops concerns strictly below
threshold) → patience cost/benefit matrix (marks each surviving concern as FIX or DEFER) → Concern Decision Gate
(trust=low: user confirms/modifies FIX/DEFER assignments via AskUserQuestion; trust=medium/high: decisions applied
silently). FIX-marked concerns enter the auto-fix loop. In all cases, do NOT present options to the user or ask
what to do during the auto-fix loop itself — spawn fix subagents and continue.

**CRITICAL: The auto-fix loop applies ONLY to spawning fix subagents. Step 6 (Deferred Concern
Review) MUST still be executed after the loop completes.**

**`AUTOFIX_ITERATION` is a single shared counter initialized exactly once to 0 before the first loop iteration and
is NEVER reset or re-initialized at any point during the entire review phase — not after loop exit, not when
"Evaluate Remaining Concerns" adds concerns back into the FIX list, and not when re-entering any loop construct.
There is one counter, one initialization, and it persists at its current value through every phase of this skill.**
The cumulative limit is 3 iterations total across all loop passes. There is no second counter, no reset on re-entry,
and no per-phase counter.

Initialize loop counter (exactly once, before the first iteration, never again): `AUTOFIX_ITERATION=0`

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
4a. **Verify implementation subagent made real progress** before re-running review. Extract
    `concerns_addressed_this_iteration` from the subagent's return value. If
    `concerns_addressed_this_iteration == 0`, the fix attempt produced no meaningful progress. In this case,
    do NOT re-run stakeholder review or increment the counter again — immediately reclassify all remaining
    FIX-marked concerns as `DEFERRED_CONCERNS` (treated as unresolved), set `AUTOFIX_ITERATION = 3` to prevent
    further iterations, and break out of the loop.

    **`concerns_addressed` is a pre-check hint, not authoritative verification.** The subagent's self-reported
    value is used only as an early-exit signal to skip the step 5 re-run when progress is clearly zero. The
    authoritative verification of progress is the stakeholder review re-run in step 5 — concerns that were not
    actually fixed will re-appear in the review result regardless of what the subagent reported. If the subagent
    over-reports progress (e.g., returns `concerns_addressed: 1` when nothing was fixed), step 5 will surface the
    same concerns again and the loop will continue until the iteration limit is reached.
5. Re-run stakeholder review by invoking the `cat:stakeholder-review-agent` skill via the Skill tool (encode all
   commits in compact format `hash:type,hash:type`). **PROHIBITION: Do NOT reuse the review result already in
   context. A new Skill tool invocation is MANDATORY. After the invocation returns, you MUST use the data from
   the new Skill tool response — not data from any prior invocation or any cached in-context JSON — to populate
   `REVIEW_STATUS` and `ALL_CONCERNS` for the remainder of this iteration. If the new result is identical in
   content to the prior result, it is still the new result and must be used as such.**
   ```
   Skill tool:
     skill: "cat:stakeholder-review-agent"
     args: "${ISSUE_ID} ${WORKTREE_PATH} ${VERIFY} ${ALL_COMMITS_COMPACT}"
   ```
6. Parse new review result from the Skill tool response above (not from any prior invocation). Assign
   `REVIEW_STATUS` and `ALL_CONCERNS` from this response before any further processing.
7. If FIX-marked concerns remain, continue loop (if under iteration limit)

**If FIX-marked concerns persist after 3 iterations:**
- Any remaining CRITICAL or HIGH concerns move to `DEFERRED_CONCERNS` (NOT to `FIXED_CONCERNS`). Do NOT add them
  to `FIXED_CONCERNS` — they are unresolved.
- The return status MUST be `CONCERNS_FOUND` if any CRITICAL or HIGH concerns remain unresolved after loop
  exhaustion, even though they have been reclassified to `DEFERRED_CONCERNS`. Reclassification to deferred does
  NOT change the status to `REVIEW_PASSED`.
- Store all remaining concerns for display at approval gate
- Continue to out-of-scope classification below

**If no FIX-marked concerns remain (or no concerns at all):**
- Store concerns for display at approval gate
- Continue to out-of-scope classification below

**NOTE:** "REVIEW_PASSED" means stakeholder review passed, NOT user approval to merge.
User approval is a SEPARATE gate in the merge phase.

### Evaluate Remaining Concerns (Cost/Benefit)

**SEQUENCING CONSTRAINT: This section MUST NOT be invoked until the auto-fix loop has fully exited.** Running
"Evaluate Remaining Concerns" while the loop is still iterating — for example, to re-classify DEFERRED concerns
as FIX and argue that the `AUTOFIX_ITERATION` counter applies only to post-loop calls — is a sequencing violation.
The constraint that prevents additional loop iterations when `AUTOFIX_ITERATION >= 3` applies equally to concerns
re-entered via this evaluation. There is one loop, one counter, and one post-loop evaluation point.

**COUNTER CONSTRAINT: `AUTOFIX_ITERATION` is NOT reset when this evaluation adds concerns back into the FIX list
and the auto-fix loop is re-entered. The counter retains its value from when the loop last exited. If
`AUTOFIX_ITERATION >= 3`, no further loop iterations are permitted; concerns that would otherwise re-enter the
loop are immediately reclassified to `DEFERRED_CONCERNS`.**

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
- These additional loop iterations count toward the existing `AUTOFIX_ITERATION < 3` limit. `AUTOFIX_ITERATION`
  retains its current value (it is NOT reset to 0). If `AUTOFIX_ITERATION` is already 3 or more, no further
  loop iterations are permitted and these concerns are reclassified to `DEFERRED_CONCERNS` immediately.

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

Any deferred concern that (a) is at or above `MIN_SEVERITY` AND (b) was not automatically tracked as an issue above
(e.g., MEDIUM or LOW severity concerns deferred by the patience matrix) is collected for the interactive wizard in
Step 6 where the user decides how to handle them. Concerns strictly below `MIN_SEVERITY` are silently ignored and
never appear in Step 6.

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

If any deferred concerns were NOT automatically tracked as issues AND are at or above `MIN_SEVERITY` (this includes
MEDIUM/LOW concerns deferred by the patience matrix), present them to the user. Concerns strictly below `MIN_SEVERITY`
are silently ignored and do not appear here.

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
- All deferred concerns are strictly below `MIN_SEVERITY` (they are silently ignored, not presented to the user)
- `TRUST == "high"` (high-trust mode auto-creates issues per Step 5 and proceeds without user interaction)

**Note:** trust=medium skips the Concern Decision Gate in Step 5 (FIX/DEFER decisions applied silently) but still
participates in Step 6 to review deferred concern scheduling interactively. Only trust=high skips Step 6 entirely.

## Rejection Handling

### Rejected Stakeholder Review

When `REVIEW_STATUS == "CONCERNS_FOUND"` with FIX-marked concerns, **automatically enter the re-work loop**.
Do NOT ask "should I fix these?", present and wait, or skip to approval gate. Proceed directly into the
auto-fix loop, fix concerns, re-run review, until all FIX-marked concerns resolved or iteration limit reached.

## Return Result (MANDATORY — Workflow Continues to Merge)

**After completing Steps 5, 6, and Rejection Handling, output the JSON return value.** This signals to the
`work-with-issue-agent` orchestrator that the review phase is complete and it must proceed to Phase 4: Merge.

**Do NOT stop after showing the stakeholder review box.** The review box is informational output, not the phase
completion signal. The JSON below is the completion signal.

Output ONLY this JSON (no surrounding text):

```json
{
  "status": "REVIEW_PASSED",
  "all_concerns": [],
  "fixed_concerns": [],
  "deferred_concerns": [],
  "all_commits_compact": "${ALL_COMMITS_COMPACT}"
}
```

Where:
- `status` = `REVIEW_PASSED` if all FIX-marked concerns resolved AND no CRITICAL or HIGH concerns appear in
  ANY output array (`all_concerns`, `fixed_concerns`, or `deferred_concerns`); `CONCERNS_FOUND` if any CRITICAL
  or HIGH concerns appear in any output array — including concerns deferred by the cost/benefit gate before ever
  entering the fix loop, and concerns reclassified to deferred after loop exhaustion. Cost/benefit deferral does
  NOT exempt a CRITICAL or HIGH concern from triggering `CONCERNS_FOUND`.
- `all_concerns` = the full list of concern objects from the final review pass
- `fixed_concerns` = concerns that were fixed in the auto-fix loop
- `deferred_concerns` = concerns deferred by the patience matrix (may have tracking issues created)
- `all_commits_compact` = accumulated compact format string including any fix commits added during this phase

**After outputting this JSON, do NOT add any further text.** The orchestrator parses the JSON return value and
immediately invokes `cat:work-merge-agent` — no user confirmation or additional summary is needed here.
