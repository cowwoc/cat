# Plan

## Goal

Implement the trust level approval gate model so each level maps to a qualitatively distinct number of
checkpoints. Currently all three levels only differ in whether the merge gate is shown; this issue
introduces a pre-implementation gate for `low` and removes all gates for `high`.

## Trust Level Definitions

### low — 2 checkpoints (maximum control)

**Gate 1: Pre-implementation review**

Before the implementation subagent is spawned, present the user with:
- Issue goal (from plan.md `## Goal`)
- Post-conditions (from plan.md `## Post-conditions`)
- Estimated token cost

Options: `Approve and start`, `Request changes`, `Abort`

Only on `Approve and start` does implementation proceed. On `Request changes`, pause for user to
revise the plan. On `Abort`, release lock and exit.

**Gate 2: Pre-merge review (standard approval gate)**

After implementation, confirm, and review phases: present the diff, stakeholder concerns, and commit
summary, then ask for merge approval as today.

### medium — 1 checkpoint (current default behavior)

No pre-implementation gate. Only the standard pre-merge approval gate after implementation completes.
Behavior is identical to the current `trust=medium` workflow.

### high — 0 checkpoints (auto-merge on clean review)

No user approval gates at all. After stakeholder review:

| Stakeholder verdict | Action |
|---------------------|--------|
| APPROVED (no concerns) | Auto-merge immediately |
| CONCERNS (medium/low only) | Auto-merge immediately |
| CONCERNS (high severity) | Pause — present concerns to user, ask: `Approve and merge` / `Fix concerns` / `Abort` |
| REJECTED | Pause — present rejection reasons, ask: `Fix issues` / `Abort` |

The auto-merge path still runs squash and rebase onto the target branch before merging.

## Pre-conditions

(none)

## Post-conditions

- [ ] trust=low: pre-implementation gate shown with issue goal, post-conditions, and estimated tokens
- [ ] trust=low: implementation does not start until user selects `Approve and start`
- [ ] trust=low: `Request changes` at pre-implementation gate returns control to user without starting work
- [ ] trust=low: standard pre-merge gate shown after implementation (unchanged)
- [ ] trust=medium: no pre-implementation gate; only standard pre-merge gate (current behavior preserved)
- [ ] trust=high: no approval gates when stakeholder verdict is APPROVED or CONCERNS (low/medium severity only)
- [ ] trust=high with HIGH severity CONCERNS: pauses and presents concerns before proceeding
- [ ] trust=high with REJECTED verdict: pauses and presents rejection reasons before proceeding
- [ ] trust=high auto-merge path: squash and rebase onto target branch execute before merge
- [ ] Unit tests covering gate routing logic for each trust level
- [ ] No regressions in existing trust=medium workflows
- [ ] E2E: run /cat:work at trust=low and verify two gates appear; trust=medium verify one gate; trust=high verify auto-merge on APPROVED

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Step renumbering in work-implement-agent may break external references; concern persistence
  mechanism must not interfere with parallel sessions; auto-merge for trust=high bypasses manual review.
- **Mitigation:** Renumber steps sequentially; use per-session/per-issue paths for review result files;
  auto-merge still runs squash, rebase, and stakeholder review (only the human approval gate is skipped).

## Files to Modify

- `plugin/skills/work-implement-agent/first-use.md` — Insert Step 4 (Pre-Implementation Approval Gate),
  renumber old Steps 4+ to 5+
- `plugin/skills/work-review-agent/first-use.md` — At end of skill (just before returning JSON), write
  review result to session file
- `plugin/skills/work-merge-agent/first-use.md` — Update Step 12 (Approval Gate) for trust=high routing
  based on persisted review result

## Research Findings

### How review results reach work-merge-agent

`work-review-agent` returns JSON (`status`, `all_concerns`, `deferred_concerns`, `allCommitsCompact`) to
`work-with-issue-agent`. Phase 4 then invokes `work-merge-agent` with only a `COMMITS_JSON_PATH` — concerns
are NOT currently passed to the merge phase. To implement trust=high auto-merge logic, the review result
must be persisted to a session file that `work-merge-agent` can read.

The existing `CRITERIA_FILE` pattern (used in `work-merge-agent` Step 7) establishes the precedent:
```
VERIFY_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}"
CRITERIA_FILE="${VERIFY_DIR}/criteria-analysis.json"
```

The review result file will follow the same pattern:
```
REVIEW_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/review/${CLAUDE_SESSION_ID}"
REVIEW_RESULT_FILE="${REVIEW_DIR}/${ISSUE_ID}-result.json"
```

### What constitutes "APPROVED vs CONCERNS vs REJECTED" at merge time

From `work-review-agent` output contract:
- `status = REVIEW_PASSED`: all CRITICAL/HIGH concerns resolved (fixed or explicitly deferred)
- `status = CONCERNS_FOUND`: CRITICAL/HIGH concerns remain unresolved after 3 auto-fix iterations

For trust=high routing in work-merge-agent:
- `REVIEW_PASSED` + no deferred HIGH/CRITICAL → **APPROVED** → auto-merge
- `REVIEW_PASSED` + some deferred concerns but none HIGH/CRITICAL → **CONCERNS (low/medium)** → auto-merge
- `CONCERNS_FOUND` (has unresolved HIGH/CRITICAL) → **CONCERNS (high severity) or REJECTED** → pause

Whether CONCERNS_FOUND is called "high severity CONCERNS" or "REJECTED" makes no practical difference at the
merge gate — both cases pause and ask the user. The distinction in the plan is informational. Implementation
treats `CONCERNS_FOUND` as the pause trigger.

### Missing concerns file (caution=low or review skipped)

When `caution=low`, `work-review-agent` is skipped entirely (work-with-issue-agent skips Phase 3).
`REVIEW_RESULT_FILE` will not exist. work-merge-agent must handle this case:
- If `REVIEW_RESULT_FILE` absent and trust=high: auto-merge (no review was run, treat as APPROVED).

## Sub-Agent Waves

### Wave 1: Pre-implementation gate (work-implement-agent)

- **Modify `plugin/skills/work-implement-agent/first-use.md`**
  - Files: `plugin/skills/work-implement-agent/first-use.md`
  - Insert new **Step 4: Pre-Implementation Approval Gate** between current Step 3 (implementing banner)
    and current Step 4 (generate implementation steps).
  - Renumber current Steps 4+ sequentially to Steps 5+ (preserving all content; only step numbers change).
  - Step 4 content (`PLAN_MD`, `ISSUE_ID`, `ESTIMATED_TOKENS`, and `TRUST` are already set from the
    existing Step 1 argument parsing at the top of the skill):

    ```
    ## Step 4: Pre-Implementation Approval Gate (trust=low only)

    If `TRUST != "low"`, skip this step entirely and proceed to Step 5.

    Read the Goal and Post-conditions from plan.md:

    ```bash
    ISSUE_GOAL=$(sed -n '/^## Goal/{n;:loop;/^## /b;p;n;b loop}' "${PLAN_MD}" | sed '/^[[:space:]]*$/d' | head -20)
    POST_CONDITIONS=$(sed -n '/^## Post-conditions/{n;:loop;/^## /b;p;n;b loop}' "${PLAN_MD}" | sed '/^[[:space:]]*$/d' | head -30)
    ```

    If either `ISSUE_GOAL` or `POST_CONDITIONS` is empty, STOP immediately:
    ```
    ERROR: plan.md is missing required sections (Goal or Post-conditions).
    Cannot present pre-implementation gate without these sections.
    Fix plan.md and retry /cat:work.
    ```

    Present the pre-implementation review gate:

    ```
    AskUserQuestion:
      header: "${ISSUE_ID} — Pre-Implementation Review"
      question: |
        **Goal:**
        ${ISSUE_GOAL}

        **Post-conditions:**
        ${POST_CONDITIONS}

        **Estimated token cost:** ${ESTIMATED_TOKENS}

        Approve to start implementation, or request changes to the plan first.
      options:
        - "Approve and start"
        - "Request changes"
        - "Abort"
    ```

    Gate result handling:

    - **"Approve and start"**: proceed to Step 5.
    - **"Request changes"**: release lock:
      ```bash
      "${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${ISSUE_ID}" "${CLAUDE_SESSION_ID}"
      ```
      Return:
      ```json
      {"status": "BLOCKED", "message": "User requested changes to the plan before implementation. Edit plan.md and re-invoke /cat:work."}
      ```
    - **"Abort"**: release lock (same command as above). Return:
      ```json
      {"status": "BLOCKED", "message": "User aborted before implementation started."}
      ```
    - **Gate rejected (empty or non-matching answer)**: Re-present the full gate. Max 3 attempts. If still
      not answered after 3 attempts, treat as "Abort".
    ```

### Wave 2: Review result persistence (work-review-agent) and auto-merge (work-merge-agent)

These two changes are independent: Wave 2 modifies different files than Wave 1.

- **Modify `plugin/skills/work-review-agent/first-use.md`**
  - Files: `plugin/skills/work-review-agent/first-use.md`
  - Just before the final `## Return Result` section (the section that outputs the JSON), insert a
    new section that persists the review result to a session file.
  - Location: find the line `Output ONLY this JSON (no surrounding text):` and insert before it:

    ```
    ## Persist Review Result for Merge Phase

    Write the review result to a session file so the merge phase can access it for trust=high routing:

    ```bash
    REVIEW_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/review/${CLAUDE_SESSION_ID}"
    mkdir -p "${REVIEW_DIR}"
    REVIEW_RESULT_FILE="${REVIEW_DIR}/${ISSUE_ID}-result.json"
    ```

    Construct minimal JSON with status and highest severity present in deferred_concerns:

    ```bash
    # Determine if any HIGH or CRITICAL concern exists in all_concerns
    # (use the in-context ALL_CONCERNS array populated during the review phase)
    HAS_HIGH_OR_CRITICAL="false"
    for concern in "${ALL_CONCERNS[@]}"; do
      severity=$(echo "$concern" | grep -o '"severity"[[:space:]]*:[[:space:]]*"[^"]*"' | \
        sed 's/.*"severity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
      if [[ "$severity" == "HIGH" || "$severity" == "CRITICAL" ]]; then
        HAS_HIGH_OR_CRITICAL="true"
        break
      fi
    done
    ```

    Write the file. Use printf with a here-doc approach that does not involve redirecting tee or '>' to
    a variable-expanded path — instead construct the JSON as a string variable and write with printf:

    ```bash
    REVIEW_RESULT_JSON="{\"status\":\"${REVIEW_STATUS}\",\"has_high_or_critical\":${HAS_HIGH_OR_CRITICAL}}"
    printf '%s' "${REVIEW_RESULT_JSON}" > "${REVIEW_RESULT_FILE}"
    ```

    If writing fails (non-zero exit), log a warning but do NOT stop — the merge phase has a fallback.
    ```

  **Variable names confirmed**: The existing skill defines `REVIEW_STATUS` (set from `review_result.review_status`
  at step "Parse positional arguments" / stakeholder review result extraction) and `ALL_CONCERNS` (set from
  `review_result.concerns[]` at the same step). Use these exact names — no lookup required.

- **Modify `plugin/skills/work-merge-agent/first-use.md`**
  - Files: `plugin/skills/work-merge-agent/first-use.md`
  - Update **Step 12: Approval Gate** section.
  - Replace the existing `**trust == "high":** Skip gate — UNLESS REBASE_HAD_CONFLICTS=true...` block with
    the following logic:

    ```
    **trust == "high":** Read the review result file to determine whether to auto-merge or pause.

    ```bash
    REVIEW_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/review/${CLAUDE_SESSION_ID}"
    REVIEW_RESULT_FILE="${REVIEW_DIR}/${ISSUE_ID}-result.json"

    HIGH_TRUST_PAUSE="false"
    if [[ -f "${REVIEW_RESULT_FILE}" ]]; then
      PERSISTED_STATUS=$(grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' "${REVIEW_RESULT_FILE}" | \
        head -1 | sed 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
      PERSISTED_HAS_HIGH=$(grep -o '"has_high_or_critical"[[:space:]]*:[[:space:]]*[^,}]*' "${REVIEW_RESULT_FILE}" | \
        sed 's/.*"has_high_or_critical"[[:space:]]*:[[:space:]]*\([^,}]*\)/\1/' | tr -d ' ')

      if [[ "$PERSISTED_STATUS" == "CONCERNS_FOUND" || "$PERSISTED_HAS_HIGH" == "true" ]]; then
        HIGH_TRUST_PAUSE="true"
      fi
    fi
    # If REVIEW_RESULT_FILE absent: caution=low was configured, no review ran → treat as APPROVED → auto-merge.
    # If REBASE_HAD_CONFLICTS=true: always pause regardless of review result.
    if [[ "${REBASE_HAD_CONFLICTS:-false}" == "true" ]]; then
      HIGH_TRUST_PAUSE="true"
    fi
    ```

    If `HIGH_TRUST_PAUSE == "false"`:
    - Auto-merge path: proceed directly to Step 13 without invoking AskUserQuestion.
    - Still run the pre-merge approval verification (marker check) to maintain audit trail.
    - Set `APPROVAL_MARKER=true` automatically (no user gate needed for trust=high clean review).

    If `HIGH_TRUST_PAUSE == "true"`:
    - Present concerns and conflict resolutions as in the standard gate.
    - Offer: `["Approve and merge", "Fix concerns", "Abort"]`
    - Process responses per the existing gate logic.
    ```

### Wave 3: Unit tests for gate routing logic (fix for missing criterion)

- **Create `plugin/skills/work-implement-agent/test-trust-gate-routing.bats`**
  - Files: `plugin/skills/work-implement-agent/test-trust-gate-routing.bats`
  - Add license header (Bash format: `# Copyright (c) 2026 ...`) after the `#!/usr/bin/env bats` shebang line.
  - Write five Bats tests that exercise the bash logic patterns used in the skill:

    **Test 1 — trust=low triggers Step 4 (plan.md Goal extraction):**
    Create a temp plan.md with a `## Goal` section. Run the `sed` command from Step 4:
    ```bash
    ISSUE_GOAL=$(sed -n '/^## Goal/{n;:loop;/^## /b;p;n;b loop}' "${PLAN_MD}" | sed '/^[[:space:]]*$/d' | head -20)
    ```
    Assert `ISSUE_GOAL` is non-empty and matches the expected goal text.

    **Test 2 — trust=medium skips Step 4 (TRUST != "low" conditional):**
    Set `TRUST="medium"`. Evaluate `[[ "$TRUST" != "low" ]]`. Assert the condition is true (exit 0), confirming
    the skip branch is taken.

    **Test 3 — trust=high with clean review sets HIGH_TRUST_PAUSE=false:**
    Create a temp review result file with `{"status":"REVIEW_PASSED","has_high_or_critical":false}`. Run the
    `grep`/`sed` parsing logic from work-merge-agent Step 12 to extract `PERSISTED_STATUS` and
    `PERSISTED_HAS_HIGH`. Assert `HIGH_TRUST_PAUSE` remains `"false"` after the conditional check.

    **Test 4 — trust=high with has_high_or_critical=true sets HIGH_TRUST_PAUSE=true:**
    Create a temp review result file with `{"status":"REVIEW_PASSED","has_high_or_critical":true}`. Run the same
    parsing logic. Assert `HIGH_TRUST_PAUSE` is set to `"true"`.

    **Test 5 — trust=high with CONCERNS_FOUND status sets HIGH_TRUST_PAUSE=true:**
    Create a temp review result file with `{"status":"CONCERNS_FOUND","has_high_or_critical":false}`. Run the same
    parsing logic. Assert `HIGH_TRUST_PAUSE` is set to `"true"` (triggered by `PERSISTED_STATUS == "CONCERNS_FOUND"`).

  - Each test must use `BATS_TMPDIR` or `mktemp -d` for temp files and clean up in a `teardown` function.
  - Run the test file (`bats plugin/skills/work-implement-agent/test-trust-gate-routing.bats`) and confirm all 5
    tests pass before committing.

### Wave 4: E2E verification procedure (fix for missing criterion)

- **Create `plugin/skills/work-implement-agent/benchmark/trust-gate-e2e-check.md`**
  - Files: `plugin/skills/work-implement-agent/benchmark/trust-gate-e2e-check.md`
  - Add license header (Markdown HTML comment format) at the top (no frontmatter, so header goes first).
  - Document the manual E2E verification checklist for each trust level:

    ```
    # Trust Gate E2E Verification Checklist

    Run /cat:work on a test issue with each trust level and verify the following:

    ## trust=low — expect 2 approval gates

    1. Set .cat/config.json `"trust": "low"`
    2. Run `/cat:work` on any open issue
    3. Verify Gate 1 (Pre-Implementation Review) appears BEFORE implementation starts:
       - Header shows `<ISSUE_ID> — Pre-Implementation Review`
       - Goal and Post-conditions from plan.md are displayed
       - Estimated token cost is shown
       - Options: `Approve and start`, `Request changes`, `Abort`
    4. Select `Approve and start`; confirm implementation subagent spawns
    5. After implementation/review phases, verify Gate 2 (standard pre-merge) appears
    6. Confirm merge completes on approval

    ## trust=medium — expect 1 approval gate

    1. Set .cat/config.json `"trust": "medium"`
    2. Run `/cat:work` on any open issue
    3. Verify NO pre-implementation gate appears
    4. Implementation subagent spawns immediately after banner
    5. After implementation/review phases, verify the standard pre-merge gate appears
    6. Confirm merge completes on approval

    ## trust=high — expect 0 gates on clean review (auto-merge)

    1. Set .cat/config.json `"trust": "high"`
    2. Run `/cat:work` on any open issue expected to pass stakeholder review cleanly
    3. Verify NO pre-implementation gate
    4. Implementation subagent spawns immediately
    5. After stakeholder review with REVIEW_PASSED and no HIGH/CRITICAL concerns:
       - Verify NO approval gate appears
       - Verify merge executes automatically
    6. Optionally repeat with a review that has HIGH severity concerns and confirm the pause gate appears
    ```
