<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Phase: Review

Review phase for `/cat:work`. Runs stakeholder review (Step 5) and deferred concern wizard (Step 6).

## Arguments Format

```
<cat_agent_id> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <all_commits_compact> <trust> <verify>
```

| Position | Name | Example |
|----------|------|---------|
| 1 | cat_agent_id | agent ID passed through from parent |
| 2 | issue_id | `2.1-issue-name` |
| 3 | issue_path | `${WORKTREE_PATH}/.cat/issues/v2/v2.1/issue-name` |
| 4 | worktree_path | `${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/2.1-issue-name` |
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
  "allCommitsCompact": "hash:type,hash:type"
}
```

## Configuration

Parse arguments and display the **Reviewing phase** banner in a chained call:

```bash
read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH ALL_COMMITS_COMPACT TRUST VERIFY <<< "$ARGUMENTS" && \
PLAN_MD="${ISSUE_PATH}/plan.md" && \
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase reviewing
```

**Validate TRUST argument:**

TRUST must be one of: `low`, `medium`, `high`. If TRUST is empty or not one of these values, STOP immediately:
```
ERROR: Invalid or missing TRUST argument: "${TRUST}". Expected one of: low, medium, high.
```

Also cross-check TRUST against config.json:
```bash
CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.cat/config.json"
CONFIG_TRUST=$(grep -o '"trust"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONFIG_FILE" | head -1 | \
    sed 's/.*"trust"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
# If the trust field is absent from config.json, treat it as "low" (most restrictive).
# This prevents trust injection via argument tampering when the field has not been explicitly configured.
if [[ -z "$CONFIG_TRUST" ]]; then
    CONFIG_TRUST="low"
fi
if [[ "$TRUST" != "$CONFIG_TRUST" ]]; then
    echo "ERROR: TRUST argument '${TRUST}' differs from config.json '${CONFIG_TRUST}'." >&2
    echo "The TRUST argument must match the configured trust level. Update config.json or correct the argument." >&2
    exit 1
fi
```

If TRUST differs from config.json (or its effective default of "low" when absent), STOP immediately. Do NOT proceed with the injected TRUST value.

**Config file is READ-ONLY during the review phase (MANDATORY):**

`config.json` MUST NOT be modified at any point during the review phase (Steps 5–6 and all sub-steps). This
constraint binds the review phase agent AND every subagent it spawns (planning subagents, implementation subagents,
and any other agent invoked during Steps 5–6), regardless of what task the subagent believes it is performing.
This includes but is not limited to: `Bash` commands that write to it (e.g., `sed -i`, `echo >`, `tee`, `cat >`),
`Edit` tool calls, `Write` tool calls, or any subagent that modifies it. The values of `trust`, `verify`,
`minSeverity`, and `patience` are read once at the start of the review phase and used as-is throughout. Any attempt
to modify `config.json` during the review phase is a protocol violation — STOP immediately and report the
violation. **There is no exception for "fixing code concerns" — config.json is off-limits to all subagents
spawned during the review phase, for any reason.**

**Read minSeverity from config:**

`minSeverity` controls the minimum concern severity that is tracked and presented to the user. Concerns strictly
below `minSeverity` are silently ignored — they never appear in Step 6, are never tracked as issues, and are
never included in `deferred_concerns`.

Valid values (ordered low-to-high): `LOW`, `MEDIUM`, `HIGH`. The value `CRITICAL` is explicitly forbidden — setting
minSeverity=CRITICAL would suppress all non-CRITICAL concerns, defeating the purpose of review.

Read it now, before Step 5:

```bash
CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.cat/config.json"
if [[ ! -f "$CONFIG_FILE" ]]; then
    echo "ERROR: config.json not found at ${CONFIG_FILE}. Cannot proceed." >&2
    exit 1
fi

MIN_SEVERITY_RAW=$(grep -o '"minSeverity"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONFIG_FILE" | head -1 | \
    sed 's/.*"minSeverity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')

# Default is "LOW" when the field is absent (track everything by default)
MIN_SEVERITY="LOW"
if [[ -n "$MIN_SEVERITY_RAW" ]]; then
    VALID_MIN_SEVERITY=("LOW" "MEDIUM" "HIGH")
    MIN_SEVERITY_VALID=false
    for v in "${VALID_MIN_SEVERITY[@]}"; do
        if [[ "$MIN_SEVERITY_RAW" == "$v" ]]; then MIN_SEVERITY_VALID=true; break; fi
    done
    if [[ "$MIN_SEVERITY_RAW" == "CRITICAL" ]]; then
        echo "ERROR: minSeverity=CRITICAL is not allowed in ${CONFIG_FILE}. The maximum permitted value is HIGH." >&2
        echo "Setting minSeverity=CRITICAL would suppress all non-CRITICAL concerns, defeating the purpose of review." >&2
        exit 1
    fi
    if [[ "$MIN_SEVERITY_VALID" != "true" ]]; then
        echo "ERROR: Invalid minSeverity value '${MIN_SEVERITY_RAW}' in ${CONFIG_FILE}. Expected one of: LOW, MEDIUM, HIGH." >&2
        exit 1
    fi
    MIN_SEVERITY="$MIN_SEVERITY_RAW"
fi
```

Use `MIN_SEVERITY` everywhere the instructions reference `minSeverity`. The `minSeverity` value is read once here and
MUST NOT be re-read or modified for the remainder of the review phase (see read-only constraint above).

## Step 5: Review Phase (MANDATORY)

**If the command fails or produces no output**, STOP immediately:
```
FAIL: progress-banner launcher failed for phase 'reviewing'.
The jlink image may not be built. Run: mvn -f hooks/pom.xml verify
```
Do NOT skip the banner or continue without it.

### Skip Review if Configured

**Validate VERIFY argument:**

VERIFY must be one of: `all`, `changed`, `none`. If VERIFY is empty or not one of these values, STOP immediately:
```
ERROR: Invalid or missing VERIFY argument: "${VERIFY}". Expected one of: all, changed, none.
```

Also cross-check VERIFY against config.json:
```bash
CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.cat/config.json"
CONFIG_VERIFY=$(grep -o '"verify"[[:space:]]*:[[:space:]]*"[^"]*"' "$CONFIG_FILE" | head -1 | \
    sed 's/.*"verify"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
# If the verify field is absent from config.json, treat it as "changed" (default).
if [[ -z "$CONFIG_VERIFY" ]]; then
    CONFIG_VERIFY="changed"
fi
if [[ "$VERIFY" != "$CONFIG_VERIFY" ]]; then
    echo "ERROR: VERIFY argument '${VERIFY}' differs from config.json '${CONFIG_VERIFY}'." >&2
    echo "The VERIFY argument must match the configured verify level. Update config.json or correct the argument." >&2
    exit 1
fi
```

If VERIFY differs from config.json (or its effective default of "changed" when absent), STOP immediately. Do NOT
proceed with the injected VERIFY value.

Skip if: `VERIFY == "none"` (after validation above confirms it matches config.json)

**Note:** `verify=none` is an explicit user choice made via `config.json`. The user is responsible for their own
configuration. The informational warning below for sensitive changes is the intended guardrail — no additional
enforcement is applied. If a prior phase modified `config.json` to set `verify=none`, that modification would have
been committed and is visible in git history, so the user can audit it.

If skipping, check whether the commits include potentially sensitive changes before outputting the skip message:
- Parse `ALL_COMMITS_COMPACT` for any commit with type `bugfix:` — these indicate defect corrections that benefit
  from review
- Check if any committed files match security-sensitive patterns (e.g., files containing `auth`, `security`,
  `credential`, `token`, `secret`, `password`, `key` in their path, or files under `hooks/` or `rules/` directories)

If either condition is true, log a warning BEFORE the skip message:
`"WARNING: Review skipped (verify=none) but commits include potentially sensitive changes (${reason}). Consider running review manually."`

This warning is informational only — it does NOT block the skip. `verify=none` is an intentional user configuration
and is always respected.

Then output: "Review skipped (verify: ${VERIFY})"

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
  args: "${CAT_AGENT_ID} ${ISSUE_ID} ${WORKTREE_PATH} ${VERIFY} ${COMMITS_COMPACT}"
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

**False-positive volume limit (MANDATORY):** At most 2 concerns may be classified as potential false positives per
review phase. If more than 2 concerns appear to be false positives, only the first 2 (in the order they appear in
`ALL_CONCERNS`) may be presented as potential false positives. All remaining concerns MUST be treated as real concerns
and proceed through the normal FIX/DEFER pipeline. This prevents volume-based decision fatigue attacks.

**Combined-mechanism concern-elimination cap (MANDATORY):** Across the entire review phase, the total number of
concerns eliminated by combining any subset of the following mechanisms — (a) false-positive discard, (b) UNFIXABLE
reclassification to DEFERRED_CONCERNS, (c) silent MIN_SEVERITY filtering, and (d) user-approved deferrals via the
"Defer to tracking issue" option in the repeated UNFIXABLE escalation AskUserQuestion — MUST NOT exceed the
false-positive cap of 2 for concerns at HIGH or CRITICAL severity. Specifically: if 2 HIGH/CRITICAL concerns have
already been eliminated across any combination of mechanisms (a)–(d), no additional HIGH/CRITICAL concern may be
moved to DEFERRED_CONCERNS via any of those mechanisms in the same review phase. Each mechanism's individual limits
still apply independently; this cap adds an aggregate ceiling to prevent composing the mechanisms to eliminate more
HIGH/CRITICAL concerns than any single mechanism would allow.

**False-positive classification requires user confirmation.** You MUST NOT silently discard any concern by classifying
it as a false positive on your own judgment. When you believe a concern may be a false positive (i.e., the reviewer
appears to have read the wrong branch), you MUST present the concern to the user via AskUserQuestion and ask for
explicit confirmation before discarding it. The AskUserQuestion MUST use the following exact format:

```
AskUserQuestion tool:
  question: "A reviewer concern may be a false positive (reviewer read wrong branch).

  Concern: [full concern text including severity, stakeholder, location, explanation]

  Evidence: Reviewer referenced file path [exact path] but worktree path is [exact worktree path].

  Should this concern be discarded as a false positive?"
  options:
    - "Keep this concern (treat as real)"
    - "Discard as false positive"
```

The `answer` field must exactly match one of these two option strings (case-sensitive). "Keep this concern (treat as
real)" is the FIRST option (default). If the answer does not exactly match either option, treat the concern as real.
Only if the user selects "Discard as false positive" may the concern be removed. Do NOT use any other option text,
do NOT add additional options, and do NOT rephrase the options.

**Evidence field constraint (MANDATORY):** The "Evidence" line in the AskUserQuestion MUST contain ONLY verifiable
facts: the specific file path the reviewer referenced and the expected worktree path. Use the exact format shown
above: `Reviewer referenced file path [X] but worktree path is [Y]`. Do NOT include interpretive conclusions,
persuasive language, subjective assessments, or any text beyond the two paths. The user must judge the evidence
themselves. **Path normalization (MANDATORY):** Before populating [X] and [Y], convert both paths to absolute
form (resolve any leading `./`, `../`, or relative segments against the worktree root). A concern whose [X] and
[Y] refer to the same file after normalization is NOT a false positive — it is a real concern and must be treated
as such.

**Parse Review Result:**

The `cat:stakeholder-review-agent` skill returns a JSON object. Extract the following fields:

- `REVIEW_STATUS` = `review_result.review_status` (e.g., `"REVIEW_PASSED"` or `"CONCERNS_FOUND"`)
- `ALL_CONCERNS` = `review_result.concerns[]` — the full list of concern objects returned by the review

Store `ALL_CONCERNS` for use in the auto-fix loop and the approval gate. Each concern object has fields:
`severity`, `stakeholder`, `location`, `explanation`, `recommendation`, and optionally `detail_file`.

**Severity immutability (MANDATORY):** The `severity` field of each concern object is set by the stakeholder
reviewer and MUST NOT be modified, re-interpreted, downgraded, or overridden at any point during the review phase.
Use the exact severity value as returned by the stakeholder review for all downstream decisions: the patience
matrix, the Concern Decision Gate, issue creation in Step 5, and the deferred concern wizard in Step 6. If a
concern is missing the `severity` field entirely, treat it as `CRITICAL` (not MEDIUM) and log a warning:
`"WARNING: Concern from [stakeholder] at [location] is missing severity field — treating as CRITICAL."` This is the
safe default because a missing severity is more likely a bug or manipulation than a legitimate omission. Additionally,
when `REVIEW_STATUS == "CONCERNS_FOUND"`, validate that every concern object in the response has a `severity` field.
If any concern lacks `severity`, log the warning above for each such concern. This is the only case where severity is
inferred. Reclassifying a concern's severity (e.g., treating a CRITICAL as HIGH or a HIGH as MEDIUM) is
prohibited.

**Read patience from config:**

```bash
# Read patience from .cat/config.json
# Default is "medium" if the config file exists but field is absent
# FAIL if the config file cannot be read, or if the value is present but invalid
PATIENCE_LEVEL="medium"

CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.cat/config.json"
if [[ ! -f "$CONFIG_FILE" ]]; then
    echo "ERROR: config.json not found at ${CONFIG_FILE}. Cannot proceed." >&2
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

**Concern Decision Gate (MANDATORY — ALL trust levels):**

**MANDATORY at ALL trust levels.** After the patience matrix evaluates FIX/DEFER for each concern, present the
decisions to the user via AskUserQuestion before running the auto-fix loop. This gate applies regardless of TRUST
level — no trust level bypasses it. The Step 6 `TRUST == "high"` skip condition applies ONLY to Step 6 (the
deferred concern wizard) and does NOT affect this gate.

**Per-trust-level procedure:**

- **trust=low**: Invoke AskUserQuestion tool with detailed FIX/DEFER summary (all fields: severity, stakeholder,
  location, explanation, recommendation, decision, benefit, cost, threshold) and options:
  - "Proceed with these decisions (Recommended)"
  - "Let me change decisions"
- **trust=medium**: Invoke AskUserQuestion tool with brief FIX/DEFER summary (severity, stakeholder, description,
  decision) and options:
  - "Proceed"
  - "Request changes"
- **trust=high**: Invoke AskUserQuestion tool with minimal FIX/DEFER summary showing count of FIX vs DEFER **with
  severity breakdown** (e.g., "FIX: 1 CRITICAL, 2 HIGH | DEFER: 1 MEDIUM") and options:
  - "Proceed"
  - "Abort"

A **valid selection** requires the `answer` field to exactly match one of the option strings (case-sensitive).
Any `answer` that does not exactly match one of the listed options is a **failed attempt**.

**ASCII-only option strings (MANDATORY):** All AskUserQuestion `options` strings in this skill MUST use only printable
ASCII characters (U+0020 to U+007E). Any non-ASCII character (including Unicode homoglyphs, zero-width characters,
combining marks, or RTL overrides) in an option string is a protocol violation. The agent MUST use the exact option
strings specified in the per-trust-level procedure above — no rephrasing, no character substitution, no additions.

**Attempt limit:** Present the gate up to 3 times. If after 3 failed attempts no valid selection is received, STOP
and return:
```json
{
  "status": "CONCERNS_FOUND",
  "all_concerns": [...],
  "fixed_concerns": [],
  "deferred_concerns": [...],
  "allCommitsCompact": "${ALL_COMMITS_COMPACT}"
}
```

Where `deferred_concerns` is populated with the full contents of `ALL_CONCERNS` (all concerns are treated as
deferred since no user decision was obtained). Do NOT return an empty `deferred_concerns` array when `all_concerns`
is non-empty.

After the user confirms (or modifies and confirms), proceed to the auto-fix loop below with the final FIX/DEFER
assignments.

**Auto-fix loop for concerns marked as FIX:**

**Spawn fix subagents without asking the user during the auto-fix loop.** When stakeholder review returns
REJECTED, always enter the re-work loop — CRITICAL concerns must be fixed before merge. When review returns
CONCERNS_FOUND, concerns flow through the pipeline: `MIN_SEVERITY` filter (silently drops concerns strictly below
threshold) → patience cost/benefit matrix (marks each surviving concern as FIX or DEFER) → Concern Decision Gate
(ALL trust levels: user confirms/modifies FIX/DEFER assignments via AskUserQuestion). FIX-marked concerns enter
the auto-fix loop. In all cases, do NOT present options to the user or ask what to do during the auto-fix loop
itself — spawn fix subagents and continue.

**CRITICAL: The auto-fix loop applies ONLY to spawning fix subagents. Step 6 (Deferred Concern
Review) MUST still be executed after the loop completes.**

**`AUTOFIX_ITERATION` is a single shared counter initialized exactly once to 0 before this loop begins and is
NEVER reset during the entire review phase, including when the cost/benefit evaluation (Evaluate Remaining
Concerns) adds concerns back into the FIX list.** The cumulative limit is 3 iterations total across all loop
passes. There is no second counter, no reset on re-entry, and no per-phase counter.

Initialize loop counter (once, before any looping): `AUTOFIX_ITERATION=0`

**While FIX-marked concerns exist and AUTOFIX_ITERATION < 3:**

**Priority ordering (MANDATORY):** The auto-fix loop MUST address CRITICAL concerns first, then HIGH, then
MEDIUM, then LOW. When constructing `concerns_formatted` below, order concerns by severity (CRITICAL first).
When spawning planning and fix subagents, the prompt MUST instruct them to address CRITICAL concerns BEFORE
other severities. If any CRITICAL concern is in the FIX list, the subagent prompts MUST explicitly state:
"CRITICAL concerns MUST be addressed first, before any other severity."

1. Increment iteration counter: `AUTOFIX_ITERATION++`
2. Extract FIX-marked concerns from `ALL_CONCERNS` (after patience matrix evaluation), sort by severity
   (CRITICAL > HIGH > MEDIUM > LOW), and construct the following variables:

   **`concerns_formatted`** — a numbered Markdown list, one entry per filtered concern. For each concern, use the
   format:
   ```
   ### Concern N: SEVERITY - brief description
   - Stakeholder: [stakeholder]
   - Location: [location or "(no location)"]
   - Explanation: [explanation or "(field missing — see detail_file for full context)"]
   - Recommendation: [recommendation or "(field missing — see detail_file for full context)"]
   ```
   If a concern is missing `severity`, treat it as `CRITICAL` (see severity immutability rule above). If `explanation` or `recommendation` are absent,
   substitute `"(field missing — see detail_file for full context)"`.

   **`detail_file_paths`** — a newline-separated list of absolute paths to concern detail files. For each filtered
   concern, if `detail_file` is present and non-empty, use it as an absolute path (reviewer agents write absolute
   paths using their `${WORKTREE_PATH}`). Include the path only if the file exists on disk. Omit concerns with no
   `detail_file` or a non-existent file — this is normal when a reviewer found no detailed concerns worth recording.

3. Spawn a shared planning subagent to produce a per-concern fix plan:
   ```
   Task tool:
     description: "Plan per-concern fixes (iteration ${AUTOFIX_ITERATION})"
     subagent_type: "cat:work-execute"
     model: "sonnet"
     prompt: |
       Analyze the following stakeholder review concerns for issue ${ISSUE_ID} and produce a
       per-concern fix plan. Each concern MUST have a self-contained section with all information
       a separate subagent would need to implement the fix without additional context.

       ## Issue Configuration
       ISSUE_ID: ${ISSUE_ID}
       WORKTREE_PATH: ${WORKTREE_PATH}
       BRANCH: ${BRANCH}
       TARGET_BRANCH: ${TARGET_BRANCH}

       ## Concerns to Analyze (CRITICAL first)
       ${concerns_formatted}

       ## Concern Detail Files
       ${detail_file_paths}

       ## Instructions
       - CRITICAL concerns MUST be addressed first (list them first in the plan).
       - For each concern produce a self-contained section with:
         1. Exact file path(s) to modify
         2. What the current code does (quote the relevant lines if possible)
         3. What the fixed code must look like (write out the replacement)
         4. Why this fixes the concern
       - If a concern cannot be fixed with a code change, mark it UNFIXABLE and explain why.
       - Do NOT implement the fixes — only plan them.

       ## Return Format
       Return a fix plan with one section per concern:

       ### Concern N: [severity] [brief description]
       - Files: [exact file paths]
       - Current code: [quote or describe]
       - Fixed code: [replacement or description of change]
       - Rationale: [why this fixes the concern]
       [or: UNFIXABLE: [reason]]
   ```

   Capture the planning subagent's full output as `fix_plan_from_planning_subagent` for use in step 4 (single-concern path) and step 6 scope isolation validation.

   **Fix plan validation (MANDATORY):** After receiving the planning subagent's output, verify that each
   FIX-marked concern has at least one actionable file change (a file path and a concrete modification).
   If the plan contains only observations, commentary, or states that a concern is "already addressed"
   without any code change, treat that concern as UNFIXABLE for this iteration: move it to
   `DEFERRED_CONCERNS` and do NOT pass it to the implementation subagent(s). A fix plan that contains
   zero actionable changes across all concerns is treated as a complete planning failure — skip the
   implementation subagent(s) for this iteration and decrement no concerns from the FIX list.

   **Noop and partial-noop detection (MANDATORY):** Track two counters, both initialized to 0 before the loop:
   - `NOOP_ITERATIONS`: iterations that produce zero actionable changes (complete planning failures).
   - `PARTIAL_NOOP_ITERATIONS`: iterations where fewer than half of the FIX-marked concerns had at least one
     concern fix subagent return SUCCESS. Specifically, if `successful_concern_count < ceil(total_fix_concerns / 2)`,
     the iteration is a partial noop. (A full noop is also counted as a partial noop.) Concerns retried
     sequentially that succeed count as successful. Concerns that fail both the parallel and sequential attempts
     count as non-successful.

   **UNFIXABLE status (MANDATORY):** Reclassifying a concern as UNFIXABLE does NOT count as resolving the concern
   for status purposes. UNFIXABLE concerns moved to `DEFERRED_CONCERNS` retain their original severity. If any
   UNFIXABLE concern has CRITICAL or HIGH severity, the return `status` MUST be `CONCERNS_FOUND`, not
   `REVIEW_PASSED`.

   **CRITICAL and HIGH concerns CANNOT be marked as UNFIXABLE.** If the planning subagent declares a CRITICAL or HIGH
   concern as UNFIXABLE, treat this as a planning failure for that concern: log a warning
   (`"WARNING: Planning subagent declared [SEVERITY] concern '[description]' as UNFIXABLE — treating as planning failure."`)
   and keep the concern in the FIX list for the next iteration. The concern is NOT moved to `DEFERRED_CONCERNS`
   unless the auto-fix loop exhausts all 3 iterations.

   **MEDIUM UNFIXABLE cap (MANDATORY):** The planning subagent MAY declare MEDIUM concerns as UNFIXABLE, which moves
   them to `DEFERRED_CONCERNS`. However, at most 2 MEDIUM concerns may be moved to `DEFERRED_CONCERNS` via UNFIXABLE
   declaration across the entire review phase. If a third or subsequent MEDIUM concern is declared UNFIXABLE, treat it
   as a planning failure (same as CRITICAL/HIGH): log a warning
   (`"WARNING: MEDIUM UNFIXABLE cap reached — treating MEDIUM concern '[description]' as planning failure."`)
   and keep it in the FIX list for the next iteration. This prevents the planning subagent from silently deferring an
   unlimited number of MEDIUM concerns without user confirmation.

   **Repeated UNFIXABLE escalation (MANDATORY):** Track UNFIXABLE declarations per concern using the match key
   `stakeholder` + normalized `location` (same normalization as step 14: strip line numbers, remove leading `./`,
   remove trailing `/`). If the planning subagent declares the SAME CRITICAL or HIGH concern as UNFIXABLE for the
   SECOND time, halt auto-fix attempts for that concern immediately and escalate to the user:
   ```
   AskUserQuestion tool:
     question: "A [SEVERITY] concern has been declared UNFIXABLE twice by the planning subagent.

     Concern: [stakeholder] at [location]: [explanation]

     How should this be handled?"
     options:
       - "Force fix (keep in FIX list for remaining iterations)"
       - "Defer to tracking issue"
       - "Abort review phase"
   ```
   - "Force fix": keep the concern in the FIX list; the next iteration must attempt it again.
   - "Defer to tracking issue": move to `DEFERRED_CONCERNS` and create a tracking issue via `/cat:add-agent`
     using the severity x patience matrix. The return `status` MUST be `CONCERNS_FOUND`. **This status BLOCKS
     merge — `CONCERNS_FOUND` is not a terminal state that permits merge. The merge phase MUST NOT proceed
     when status is `CONCERNS_FOUND` regardless of whether the concern was moved to DEFERRED_CONCERNS.**
   - "Abort review phase": STOP immediately and return with `status: "CONCERNS_FOUND"`, all FIX-marked concerns
     moved to `deferred_concerns`.
   This escalation prevents wasting iteration budget on concerns the planner cannot resolve.

4. **Single-concern optimization:** When there is exactly ONE FIX-marked concern in the current iteration, skip
   the parallel worktree protocol entirely. Instead, spawn one implementation subagent running in the original
   issue worktree `${WORKTREE_PATH}` on the main `${BRANCH}`:

   ```
   Task tool:
     description: "Fix concern 1: ${concern_brief_description} (iteration ${AUTOFIX_ITERATION})"
     subagent_type: "cat:work-execute"
     model: "sonnet"
     prompt: |
       Fix the following stakeholder review concern for issue ${ISSUE_ID}.

       ## Issue Configuration
       ISSUE_ID: ${ISSUE_ID}
       WORKTREE_PATH: ${WORKTREE_PATH}
       BRANCH: ${BRANCH}
       TARGET_BRANCH: ${TARGET_BRANCH}

       ## Concern to Fix
       ${concerns_formatted}

       ## Concern Detail Files
       For comprehensive analysis, read these detail files (if present):
       ${detail_file_paths}

       ## Fix Plan (from planning step)
       ${fix_plan_from_planning_subagent}

       ## Instructions
       - Work in the worktree at ${WORKTREE_PATH}
       - Fix the concern according to the fix plan and recommendation
       - Read the concern detail file for full context (if path provided and file exists)
       - Commit your fix using the same commit type as the primary implementation
         (e.g., `bugfix:`, `feature:`). Do NOT use `test:` as an independent commit type.
       - Use git commit without `--no-verify` to ensure hooks run.
       - **ABSOLUTE PROHIBITION:** You MUST NOT read, write, or modify `.cat/config.json`
         for any reason. This file is locked for the duration of the review phase.
         Prohibited mechanisms include but are not limited to: `sed -i`, `echo >`, `tee`, `cat >`,
         `Edit` tool, `Write` tool, `mv` (rename/replace), `cp` (overwrite via copy), and `ln`
         (replace via symlink). Fix only the concern listed above — nothing else.
       - Return JSON status when complete

       ## Return Format
       ```json
       {
         "status": "SUCCESS|PARTIAL|FAILED",
         "commits": [{"hash": "...", "message": "...", "type": "..."}],
         "files_changed": N,
         "concern_addressed": true|false
       }
       ```
   ```

   Proceed directly to step 11 (validate fix subagent output) using commits from the fix subagent's return
   value. Skip steps 5–10.

   **When there are TWO OR MORE FIX-marked concerns**, use the full parallel worktree protocol (steps 5–10):

5. **Spawn N parallel concern fix subagents (one per FIX-marked concern):**

    **Worktree naming convention:** For each concern N (1-indexed, ordered by severity CRITICAL first), create
    a temporary branch name: `fix-concern-${AUTOFIX_ITERATION}-${N}-${BRANCH}`.

    Before spawning subagents, create one isolated worktree per concern using the main agent's Bash tool:

    ```bash
    # For each concern N (1-indexed):
    CONCERN_BRANCH="fix-concern-${AUTOFIX_ITERATION}-${N}-${BRANCH}"
    CONCERN_WORKTREE="${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/${CONCERN_BRANCH}"
    cd "${WORKTREE_PATH}" && \
      git worktree add -b "${CONCERN_BRANCH}" "${CONCERN_WORKTREE}" HEAD
    ```

    Run worktree creation for all N concerns in a single chained Bash call (create all before spawning any
    subagent). If worktree creation fails for concern N, do NOT spawn that concern's subagent — mark it as
    FAILED immediately and include it in the failed-concerns list.

    **Spawn all N concern fix subagents in a single message** (not sequentially — use N simultaneous Task tool
    calls in one response):

    For each concern N, the delegation prompt is:

    ```
    Task tool:
      description: "Fix concern ${N}: ${concern_brief_description} (iteration ${AUTOFIX_ITERATION})"
      subagent_type: "cat:work-execute"
      model: "sonnet"
      prompt: |
        Fix the following stakeholder review concern for issue ${ISSUE_ID}.

        ## Issue Configuration
        ISSUE_ID: ${ISSUE_ID}
        WORKTREE_PATH: ${CONCERN_WORKTREE_N}
        BRANCH: ${CONCERN_BRANCH_N}
        TARGET_BRANCH: ${TARGET_BRANCH}

        ## Your Assigned Concern (ONLY fix this concern — do NOT modify files unrelated to it)
        ${concern_N_formatted}

        ## Fix Plan for This Concern
        ${fix_plan_section_N}

        ## Concern Detail File (if applicable)
        ${concern_N_detail_file_path}

        ## Instructions
        - Work exclusively in the worktree at ${CONCERN_WORKTREE_N}.
        - Fix ONLY the files listed in the fix plan for this concern.
        - Do NOT modify any file not referenced in the fix plan section above.
        - Read concern detail file for full context (if path provided and file exists).
        - Commit your fix using the same commit type as the primary implementation (e.g., bugfix:,
          feature:). These commits will be merged into the main implementation branch.
        - Use git commit without `--no-verify` to ensure hooks run.
        - **ABSOLUTE PROHIBITION:** Do NOT read, write, or modify `.cat/config.json` for any reason.
          This file is locked for the duration of the review phase. Prohibited mechanisms include but are not
          limited to: `sed -i`, `echo >`, `tee`, `cat >`, `Edit` tool, `Write` tool, `mv` (rename/replace),
          `cp` (overwrite via copy), and `ln` (replace via symlink). Fix only the concern listed above — nothing else.

        ## Return Format
        ```json
        {
          "status": "SUCCESS|PARTIAL|FAILED",
          "commits": [{"hash": "...", "message": "...", "type": "..."}],
          "files_changed": N,
          "concern_addressed": true|false
        }
        ```
    ```

    Each subagent receives only the fix plan section for its assigned concern (not the full plan). The
    `${CONCERN_WORKTREE_N}` and `${CONCERN_BRANCH_N}` values are specific to concern N.

6. **Wait for all N concern fix subagents to complete.**

    Collect results from all N subagents. For each subagent result:
    - `SUCCESS`: subagent produced at least one commit touching concern-relevant files.
    - `PARTIAL`: subagent produced some commits but not all concern files were touched.
    - `FAILED` or no commits: subagent failed to produce any useful change.

    **Scope isolation validation (MANDATORY):** After all subagents complete, validate that each subagent
    only modified files allowed by the fix plan. The allowed files for concern N are extracted from the
    planning subagent's output by parsing lines matching `- Files: <paths>` within the `### Concern N:`
    section:

    ```bash
    # For each concern N that returned SUCCESS or PARTIAL:
    CONCERN_BRANCH="fix-concern-${AUTOFIX_ITERATION}-${N}-${BRANCH}"
    # Extract "- Files: ..." line(s) from the planning subagent's Concern N section
    # (grep between "### Concern N:" and the next "### Concern" or end of plan output)
    ALLOWED_FILES=$(echo "${fix_plan_from_planning_subagent}" | \
      awk "/^### Concern ${N}:/,/^### Concern [0-9]/" | \
      grep "^- Files:" | sed 's/- Files:[[:space:]]*//' | tr ',' '\n' | tr -d ' ')
    ACTUAL_FILES=$(cd "${WORKTREE_PATH}" && git diff --name-only HEAD "${CONCERN_BRANCH}")
    # For each file in ACTUAL_FILES, check if it appears in ALLOWED_FILES:
    while IFS= read -r actual_file; do
      if ! echo "${ALLOWED_FILES}" | grep -qF "${actual_file}"; then
        echo "WARNING: Concern ${N} fix subagent modified out-of-scope file: ${actual_file}. Allowed: ${ALLOWED_FILES}"
      fi
    done <<< "${ACTUAL_FILES}"
    ```

    Out-of-scope file warnings do NOT block the merge — they are logged for auditability.

7. **Merge concern worktree branches into the issue branch:**

    Perform all merges sequentially from the ISSUE branch (not from individual concern worktrees). Use the
    main agent's Bash tool:

    ```bash
    cd "${WORKTREE_PATH}"
    # For each concern N whose subagent returned SUCCESS or PARTIAL (in severity order: CRITICAL first):
    CONCERN_BRANCH="fix-concern-${AUTOFIX_ITERATION}-${N}-${BRANCH}"
    if ! git merge --no-ff "${CONCERN_BRANCH}" \
      -m "merge: concern ${N} fixes (${CONCERN_BRANCH})"; then
      # Conflict detected — apply last-write-wins strategy per file
      git checkout --theirs . && git add -A
      git commit -m "merge: concern ${N} fixes (conflict resolved via --theirs)"
      echo "WARNING: Merge conflict for concern ${N} resolved via last-write-wins (--theirs)."
    fi
    ```

    Log a warning for every conflict resolved via `--theirs`. After all merges complete, the issue worktree
    (`WORKTREE_PATH`) has all concern fixes applied.

    If a concern's subagent returned FAILED or produced no commits, do NOT attempt to merge its branch.
    Instead, apply the fallback strategy for that concern (see step 8).

8. **Fallback for failed concern fix subagents:**

    For each concern whose fix subagent returned FAILED or produced no commits:
    - Retry the concern ONCE using a sequential fix subagent (same Task tool format as step 4 single-concern
      path above, running in the ORIGINAL issue worktree `${WORKTREE_PATH}` on the main `${BRANCH}`).
    - If the sequential retry also fails, escalate to the user via AskUserQuestion:
      ```
      AskUserQuestion tool:
        question: "Concern fix subagent failed twice for concern:

        [concern_N_formatted]

        How should this be handled?"
        options:
          - "Skip this concern (add to deferred)"
          - "Abort review phase"
      ```
    - "Skip this concern": move concern N to `DEFERRED_CONCERNS`. If severity is CRITICAL or HIGH, create a
      tracking issue via `/cat:add-agent` per the severity × patience matrix.
    - "Abort review phase": STOP and return `status: "CONCERNS_FOUND"`, all unresolved FIX concerns moved to
      `deferred_concerns`.

9. **Clean up temporary worktrees and branches:**

    After all merges (and fallback handling) complete — whether successful or not — clean up ALL temporary
    worktrees created for this iteration:

    ```bash
    cd "${WORKTREE_PATH}"
    # For each concern N in this iteration:
    CONCERN_BRANCH="fix-concern-${AUTOFIX_ITERATION}-${N}-${BRANCH}"
    CONCERN_WORKTREE="${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/${CONCERN_BRANCH}"
    git worktree remove --force "${CONCERN_WORKTREE}" 2>/dev/null || true
    git branch -D "${CONCERN_BRANCH}" 2>/dev/null || true
    ```

    Run cleanup for all N concerns in a single chained Bash call. Cleanup failures are non-fatal (use `|| true`)
    but should be logged as warnings:
    `"WARNING: Failed to clean up worktree/branch for concern ${N}: ${CONCERN_BRANCH}"`

    Push the issue branch after all merges and cleanup:
    ```bash
    cd "${WORKTREE_PATH}" && git push origin "${BRANCH}"
    ```
    Apply the same push retry protocol as other worktree push operations (3 attempts with `git pull --rebase`
    on non-fast-forward rejection).

10. **Collect all new commits for `ALL_COMMITS_COMPACT` update:**

    After pushing, run:
    ```bash
    cd "${WORKTREE_PATH}" && git log --oneline "${TARGET_BRANCH}..HEAD"
    ```
    Parse the new commit hashes and append them to `ALL_COMMITS_COMPACT` (format: `hash:type`). The commit type
    for concern fix merges is `bugfix:` (or the primary implementation type if known).

    Compute `successful_concern_count` = number of concerns for which either the parallel fix subagent returned
    SUCCESS, or the sequential retry in step 8 succeeded. Compute `total_fix_concerns` = count of concerns in
    `concerns_formatted` this iteration. Then apply the NOOP_ITERATIONS and PARTIAL_NOOP_ITERATIONS formula:
    increment `NOOP_ITERATIONS` if `successful_concern_count == 0` (complete planning failure, zero actionable
    changes), otherwise increment `PARTIAL_NOOP_ITERATIONS` if
    `successful_concern_count < ceil(total_fix_concerns / 2)`. If `NOOP_ITERATIONS + PARTIAL_NOOP_ITERATIONS >= 2`,
    STOP the auto-fix loop immediately and report:
    `"WARNING: ${NOOP_ITERATIONS} full noop(s) and ${PARTIAL_NOOP_ITERATIONS} partial noop(s) in ${AUTOFIX_ITERATION} iterations. Possible planning failure."`
    Move all remaining FIX-marked concerns to `DEFERRED_CONCERNS` and continue to the Evaluate Remaining Concerns
    step. Note: alternating noop/trivial iterations do NOT reset either counter — totals are tracked across the
    entire loop. A single trivial change across many concerns does NOT constitute meaningful progress when CRITICAL
    concerns remain unaddressed.

11. **Validate fix subagent output (MANDATORY):** After fix subagent(s) return (either the single-concern
   implementation subagent from step 4, or after step 9 merges all concern branches into the issue branch),
   verify using commits in the issue worktree (`WORKTREE_PATH`):
   - For each concern in `concerns_formatted`, check that the commits touch at least one file mentioned
     in that concern's `location` field. Run `git diff --name-only` on each commit hash and confirm overlap with
     the concern's location. If a concern's location file was not touched by any commit, log a warning:
     `"WARNING: Concern '[description]' references [location] but no commit touched that file."` and treat the
     concern as unresolved for this iteration (do NOT count it in `concerns_addressed`).
   - The self-reported `concerns_addressed` count MUST match the number of concerns in `concerns_formatted` that
     had at least one file touched. If the self-reported count exceeds the validated count, use the validated
     (lower) count and log: `"WARNING: Fix subagent(s) reported ${reported} concerns addressed but only ${validated} were validated by file changes."`
   - **File-touch is necessary but NOT sufficient for CRITICAL/HIGH concerns (MANDATORY):** For CRITICAL and HIGH
     severity concerns, the file-level overlap check above is a necessary pre-condition only. A CRITICAL or HIGH
     concern is only considered resolved when the re-review in step 13 no longer flags it (same stakeholder +
     location + severity combination absent from the new review result). Until re-review confirms resolution,
     CRITICAL/HIGH concerns remain in the FIX list regardless of file-touch validation results.
12. **Persistent concern tracking (MANDATORY):** Before re-running stakeholder review, snapshot ALL CRITICAL and HIGH
   concerns from `ALL_CONCERNS` into `PRIOR_UNRESOLVED_CONCERNS` — regardless of file-touch validation status.
   File-touch validation is "necessary but NOT sufficient" for CRITICAL/HIGH (see step 11 above), so passing
   file-touch does NOT mean the concern is resolved. Only the re-review result (step 13–14) is authoritative for
   resolving CRITICAL/HIGH concerns. A CRITICAL/HIGH concern is removed from `PRIOR_UNRESOLVED_CONCERNS` ONLY
   when the re-review result contains no matching concern (same stakeholder + normalized location — see matching
   rules in step 14).

13. Re-run stakeholder review (encode all commits in compact format `hash:type,hash:type`):
   ```
   Skill tool:
     skill: "cat:stakeholder-review-agent"
     args: "${CAT_AGENT_ID} ${ISSUE_ID} ${WORKTREE_PATH} ${VERIFY} ${ALL_COMMITS_COMPACT}"
   ```
14. **Merge prior unresolved concerns (MANDATORY):** After parsing the new review result into `ALL_CONCERNS`, merge
   `PRIOR_UNRESOLVED_CONCERNS` back: for each concern in `PRIOR_UNRESOLVED_CONCERNS`, check whether a concern with
   the same `stakeholder` + normalized `location` combination exists in the new `ALL_CONCERNS`. If NOT present,
   re-add the prior concern to `ALL_CONCERNS`. This prevents non-deterministic LLM reviewer behavior from silently
   dropping real concerns between review rounds.

   **Location shell-game protection (MANDATORY):** When a CRITICAL or HIGH concern from `PRIOR_UNRESOLVED_CONCERNS`
   is absent from the re-review AND the fix subagent commits (as merged into the issue branch) include any of the
   following — file renames (`R` entries), new file additions (`A` entries), or deletions of existing files (`D`
   entries) — the concern is NOT removed from `PRIOR_UNRESOLVED_CONCERNS`. Check via `git diff --name-status` for
   entries starting with `R`, `A`, or `D`. This covers copy-then-delete patterns (where vulnerable code is copied to
   a new file and deleted from the original, without appearing as a rename) in addition to direct renames and
   additions. If any such entry is detected, log a warning:
   `"WARNING: CRITICAL/HIGH concern '[stakeholder] at [location]' absent from re-review but commits include file renames/additions/deletions — concern may have been relocated or the vulnerable code copied to a new file. Keeping in PRIOR_UNRESOLVED."`
   The concern remains in `PRIOR_UNRESOLVED_CONCERNS` until it either matches a concern in a subsequent re-review
   (same stakeholder + normalized location) OR the auto-fix loop exhausts all iterations.

   **Trivial-change guard for CRITICAL removal (MANDATORY):** Re-review non-determinism is an acknowledged limitation
   of LLM-based review. As mitigation: when a CRITICAL concern was in `PRIOR_UNRESOLVED_CONCERNS` and is absent from
   re-review, check the fix subagent commits (as merged into the issue branch) for the concern's file (the file
   referenced in the concern's `location` field) using `git diff --stat`. If the commits changed fewer than 5 lines
   in that file, log a warning:
   `"WARNING: CRITICAL concern '[stakeholder] at [location]' absent from re-review but only [N] lines changed in [file] — possible non-deterministic drop. Keeping in PRIOR_UNRESOLVED."`
   and keep the concern in `PRIOR_UNRESOLVED_CONCERNS`. Only remove a CRITICAL concern from persistent tracking when
   the fix commits include substantial changes (5+ lines) to the concern's file. This guard does NOT apply to HIGH
   concerns (only CRITICAL), because CRITICAL concerns warrant the highest protection against false resolution.

   **Location matching normalization (MANDATORY):** For matching purposes, the `location` field is normalized to
   file-path-only by: (1) stripping any line number suffixes (e.g., `:42`, `:10-20`, `#L5`), (2) removing leading
   `./`, and (3) removing trailing `/`. Two concerns match when `stakeholder` matches exactly AND the normalized
   file-path-only location matches. `severity` is NOT part of the match key — re-reviews may legitimately change
   severity for the same concern. **Severity floor rule (MANDATORY):** When a prior CRITICAL or HIGH concern matches
   a new concern with a LOWER severity, keep the HIGHER of the two severities. Severity can only be upgraded by
   re-review, never downgraded. This prevents non-deterministic LLM variation from stripping CRITICAL/HIGH
   protections (e.g., a concern oscillating from CRITICAL to MEDIUM between re-reviews). When a prior concern has
   severity MEDIUM or LOW, the new concern's severity is accepted as-is (whether higher or lower). When applying the
   severity floor, use the prior concern's severity but the new concern's other fields (explanation, recommendation,
   etc.).

15. Parse new review result (with merged concerns)
16. If FIX-marked concerns remain, continue loop (if under iteration limit)

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

**Cost validation (MANDATORY):** Cost=0 may ONLY be assigned when the concern's `location` field references a file
that appears in the `git diff --name-only "${TARGET_BRANCH}..HEAD"` output from Step 1. If the concern's location
references a file NOT in that list, cost MUST be >= 1. If the concern has no `location` field or the location does
not reference a specific file, cost MUST be >= 1 (the fix scope is unknown and cannot be confirmed as in-scope).
When presenting the Concern Decision Gate, include the cost assignment and the file(s) used to justify it so
the user can verify.

**Step 3: Apply the decision rule based on patience:**

Determine the `patience_multiplier` from `PATIENCE_LEVEL`:
- `low` (fix aggressively): multiplier = 0.5 — fix if `benefit >= cost × 0.5`
- `medium` (balanced): multiplier = 2 — fix if `benefit >= cost × 2`
- `high` (stay focused): multiplier = 5 — fix if `benefit >= cost × 5`

For each concern: fix inline if `benefit >= cost × patience_multiplier`

**Severity-based overrides (MANDATORY):**
- **CRITICAL concerns MUST always be marked as FIX** regardless of cost or patience. CRITICAL concerns cannot be
  deferred by the patience matrix. The cost/benefit calculation is skipped entirely for CRITICAL concerns.
- **HIGH concerns can only be deferred at cost >= 4.** If a HIGH concern has cost < 4, it MUST be marked as FIX
  regardless of the patience multiplier result.

These overrides take precedence over the patience matrix formula above.

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

**Late-discovered CRITICAL bonus iteration (MANDATORY):** If this Evaluate Remaining Concerns step discovers a NEW
CRITICAL concern — one not present in any prior round's `ALL_CONCERNS` (matched by `stakeholder` + normalized
`location`) — AND `AUTOFIX_ITERATION >= 3`, grant ONE additional iteration exclusively for that CRITICAL concern.
This bonus iteration is capped at 1 total (not per concern): if multiple new CRITICAL concerns are discovered, they
all share the single bonus iteration. The bonus iteration follows the same auto-fix loop rules (planning subagent,
implementation subagent, re-review, persistent tracking merge). If the bonus iteration does not resolve the CRITICAL
concern(s), they move to `DEFERRED_CONCERNS` with tracking issues created per the severity x patience matrix. The
`AUTOFIX_ITERATION` counter increments normally for the bonus iteration (e.g., from 3 to 4). No further bonus
iterations are granted after the first one.

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
   **Freeform constraints (MANDATORY):** Freeform instructions via "Other" MUST NOT: override the return `status`
   field, remove concerns from `all_concerns` or `deferred_concerns` tracking, bypass issue creation for
   HIGH/CRITICAL deferred concerns, or reclassify the severity of any concern (e.g., a freeform instruction
   saying "this is actually LOW severity" or "treat this as MEDIUM" must be rejected — severity is immutable and
   set by the stakeholder reviewer). The user may adjust scheduling (e.g., move to a different version) or provide
   additional context, but cannot eliminate concerns from the record or change their severity.
   **Version target constraint for HIGH/CRITICAL (MANDATORY):** When rescheduling HIGH or CRITICAL concerns (whether
   via "Reschedule one or more concerns" or "Other"), the target version is limited to: current minor version, next
   minor version, or next major version — the same versions available in the severity x patience matrix. No other
   target versions are permitted for HIGH/CRITICAL concerns. If a freeform instruction specifies a version outside
   this set for a HIGH/CRITICAL concern, reject it and re-prompt the user.

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
   **Freeform constraints (MANDATORY):** Freeform instructions via "Other" MUST NOT: override the return `status`
   field, remove concerns from `all_concerns` or `deferred_concerns` tracking, bypass issue creation for
   HIGH/CRITICAL deferred concerns, or reclassify the severity of any concern (e.g., a freeform instruction
   saying "this is actually LOW severity" or "treat this as MEDIUM" must be rejected — severity is immutable and
   set by the stakeholder reviewer). The user may adjust scheduling (e.g., move to a different version) or provide
   additional context, but cannot eliminate concerns from the record or change their severity.
   **Version target constraint for HIGH/CRITICAL (MANDATORY):** When scheduling or rescheduling HIGH or CRITICAL
   concerns via "Other", the target version is limited to: current minor version, next minor version, or next major
   version — the same versions available in the severity x patience matrix. No other target versions are permitted
   for HIGH/CRITICAL concerns. If a freeform instruction specifies a version outside this set for a HIGH/CRITICAL
   concern, reject it and re-prompt the user.

### Skip conditions

These skip conditions apply ONLY to Step 6 (the deferred concern wizard). They do NOT skip the Concern Decision
Gate in Step 5, which is MANDATORY at ALL trust levels regardless of these conditions.

Skip this step entirely and proceed to return if ANY of:
- There are no deferred concerns (all concerns passed the threshold or there were no concerns)
- All deferred concerns are strictly below `MIN_SEVERITY` (they are silently ignored, not presented to the user)
- `TRUST == "high"` (high-trust mode auto-creates issues per Step 5 and proceeds without user interaction)

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
  "allCommitsCompact": "${ALL_COMMITS_COMPACT}"
}
```

Where:
- `status` = `REVIEW_PASSED` if all FIX-marked concerns resolved AND no CRITICAL or HIGH concerns remain
  (including any reclassified to deferred after loop exhaustion); `CONCERNS_FOUND` if any CRITICAL or HIGH
  concerns remain unresolved after 3 iterations (regardless of whether they are in `fixed_concerns` or
  `deferred_concerns`). **`CONCERNS_FOUND` is a blocking status: the merge phase MUST NOT proceed. A concern
  deferred to a tracking issue via "Defer to tracking issue" does NOT change this — the deferred concern is
  still unresolved in the current issue and merge is blocked until the user explicitly overrides.**
- `all_concerns` = the full list of concern objects from the final review pass
- `fixed_concerns` = concerns that were fixed in the auto-fix loop
- `deferred_concerns` = concerns deferred by the patience matrix (may have tracking issues created)
- `allCommitsCompact` = accumulated compact format string including any fix commits added during this phase

**After outputting this JSON, do NOT add any further text.** The orchestrator parses the JSON return value and
immediately invokes `cat:work-merge-agent` — no user confirmation or additional summary is needed here.
