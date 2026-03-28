## Type
bugfix

## Goal
Block the approval gate in `work-merge-agent` when any plan.md post-condition is Missing or Partial,
preventing premature merge attempts when the confirm phase has unresolved gaps.

## Background
`work-merge-agent` presents `AskUserQuestion` (the approval gate) after completing squash, rebase,
and skill-builder review. It does not currently read the verify-implementation results produced by the
`work-confirm-agent` phase before presenting the gate. This allowed an approval gate to appear for issue
`2.1-add-output-token-optimization-guidance` when the committed changes contained only tests with no
actual skill file modifications — the confirm phase reported INCOMPLETE, but the merge phase proceeded
regardless. Recorded as M587.

## Root Cause
`work-merge-agent` receives a `COMMITS_JSON_PATH` file from the `work-with-issue-agent` orchestrator
but has no step to check the verify-implementation summary produced during the confirm phase. The verify
subagent writes a compact JSON result (status COMPLETE/PARTIAL/INCOMPLETE, criteria array with
Done/Partial/Missing per criterion) that is consumed by `work-confirm-agent` internally. No file or
parameter carries that result forward to `work-merge-agent`, so the merge phase has no signal about
unmet post-conditions.

## Fix
Add a new Step 6.5 immediately before Step 7 (Squash Commits) in `work-merge-agent/first-use.md`.
The step reads the verify-implementation result file written by the `work-verify` subagent, checks for
Missing or Partial criteria, and STOPs with a clear actionable message when any are found.

The verify subagent writes its output to a session-scoped directory:
`${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}/criteria-analysis.json`

The criteria-analysis.json file contains criterion objects with a `status` field of `Done`, `Partial`,
or `Missing`. If any criterion has status `Partial` or `Missing`, the gate must be blocked.

## Files to Modify
- `plugin/skills/work-merge-agent/first-use.md` — add Step 6.5 post-condition gate check

## Files to Update (non-code)
- `.cat/retrospectives/mistakes-2026-03.json` — update M587 entry with
  `prevention_implemented: true`, `prevention_path`, and `correct_behavior`

## Post-conditions
- [ ] `work-merge-agent` reads `${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}/criteria-analysis.json`
      before proceeding to Step 7 (squash), and blocks with a clear message if any criterion is Missing or Partial
- [ ] Blocked message names each unmet criterion (text + status) and instructs the agent to return to
      implementation before re-presenting the gate
- [ ] When criteria-analysis.json is absent (e.g., verify=none), the check is skipped with a logged notice
      and the standard flow continues
- [ ] Standard flow (all criteria Done, or verify=none) is fully unchanged — no regressions introduced
- [ ] M587 entry in `.cat/retrospectives/mistakes-2026-03.json` is updated:
      `prevention_implemented=true`, `prevention_path` set to
      `plugin/skills/work-merge-agent/first-use.md Step 6.5`, and `correct_behavior` filled in
- [ ] E2E simulation: manually trace the skill steps with a Missing criterion in criteria-analysis.json
      and confirm the gate is blocked rather than presented

## Execution Plan

### Step 1: Read the current work-merge-agent first-use.md (worktree copy)

Read `plugin/skills/work-merge-agent/first-use.md` from the worktree. Identify the exact line where
Step 7 (Squash Commits by Topic Before Review) begins so Step 6.5 is inserted immediately before it.

### Step 2: Insert Step 6.5 — Post-Condition Gate Check

Edit `plugin/skills/work-merge-agent/first-use.md` to insert the following section immediately before
the `## Step 7: Squash Commits by Topic Before Review (MANDATORY)` heading.

The new section text is:

```
## Step 6.5: Post-Condition Gate Check (MANDATORY — BLOCKING)

Before squashing or rebasing, verify that the confirm phase reported all post-conditions as Done.
If any criterion is Missing or Partial, the approval gate MUST NOT be presented.

**Locate the criteria results file:**

```bash
VERIFY_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}"
CRITERIA_FILE="${VERIFY_DIR}/criteria-analysis.json"
```

**If the file does not exist** (e.g., `VERIFY == "none"` or confirm phase was skipped):

```bash
if [[ ! -f "${CRITERIA_FILE}" ]]; then
  echo "NOTE: criteria-analysis.json not found — post-condition gate check skipped (verify=${VERIFY})"
fi
```

Output the note and continue to Step 7 without blocking.

**If the file exists**, read it and check for unmet criteria:

```bash
# Extract all criteria with status Partial or Missing
UNMET=$(grep -E '"status"[[:space:]]*:[[:space:]]*"(Partial|Missing)"' "${CRITERIA_FILE}" || true)
```

If `UNMET` is empty, all criteria are Done — continue to Step 7 without blocking.

If `UNMET` is non-empty, extract the criterion names paired with their status and STOP:

```bash
# Extract criterion name + status pairs for the unmet items
# Each criterion object has "name" and "status" fields
UNMET_DETAILS=$(grep -B5 '"status"[[:space:]]*:[[:space:]]*"(Partial|Missing)"' "${CRITERIA_FILE}" \
  | grep -E '"(name|status)"' | paste - - \
  | sed 's/.*"name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/  - \1 [\2]/')
```

Display a blocking error and return FAILED — do NOT proceed to squash, rebase, or the approval gate:

```
BLOCKED: Approval gate cannot be presented — unmet post-conditions detected.

The confirm phase reported the following criteria as not fully satisfied:
${UNMET_DETAILS}

Required action:
1. Return to the implementation phase and address each unmet criterion.
2. Re-run /cat:work to complete confirm → review → merge in sequence.
3. Do NOT manually squash commits or force-present the approval gate.

The gate protects users from approving incomplete work. Fix the implementation first.
```

Return FAILED status:

```json
{
  "status": "FAILED",
  "phase": "pre-gate-check",
  "message": "Approval gate blocked: unmet post-conditions (see above)",
  "issueId": "${ISSUE_ID}",
  "lock_retained": true
}
```
```

### Step 3: Append M587 entry to mistakes-2026-03.json

M587 does not yet exist in this file. Read `.cat/retrospectives/mistakes-2026-03.json` and append
a new entry to the `mistakes` array. Insert it before the closing `] }` of the array, adding a
comma after the previous last entry, maintaining valid JSON array syntax.

Use the following structure, which matches the field schema of existing entries in the file:

```json
{
  "id": "M587",
  "timestamp": "2026-03-20T00:00:00Z",
  "category": "protocol_violation",
  "title": "Approval gate presented despite INCOMPLETE verify-implementation result",
  "description": "work-merge-agent presented AskUserQuestion for issue 2.1-add-output-token-optimization-guidance when the confirm phase had reported INCOMPLETE status. The commit contained only tests with no actual skill file modifications, so post-conditions were Missing. The merge phase had no mechanism to check the verify-implementation result before proceeding to the gate.",
  "root_cause": "work-merge-agent does not read the criteria-analysis.json file produced by the work-verify subagent during the confirm phase. No parameter or file carries the verify result forward to the merge phase, leaving the gate unguarded against incomplete implementations.",
  "cause_signature": "compliance_failure:skill_incomplete:gate_unguarded",
  "prevention_type": "skill",
  "prevention_implemented": true,
  "prevention_verified": false,
  "prevention_path": "plugin/skills/work-merge-agent/first-use.md Step 6.5",
  "prevention_commit_hash": null,
  "recurrence_of": null,
  "pattern_keywords": ["work-merge-agent", "approval-gate", "post-conditions", "verify-implementation", "criteria-analysis"],
  "prevention_quality": {
    "verification_type": "positive",
    "fragility": "low",
    "catches_variations": true
  },
  "correct_behavior": "Before squashing or rebasing (Step 7), work-merge-agent reads ${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}/criteria-analysis.json. If any criterion has status Missing or Partial, the skill outputs a BLOCKED message listing each unmet criterion and returns FAILED without presenting AskUserQuestion. The gate is only presented when all criteria are Done (or the file is absent because verify=none)."
}
```

### Step 4: Run mvn tests

```bash
mvn -f client/pom.xml test
```

All tests must pass before committing.

### Step 5: Commit implementation

Commit the skill change and M587 update together in one commit (STATE.md update belongs with
implementation per CLAUDE.md conventions):

```bash
cd "${WORKTREE_PATH}"
git add plugin/skills/work-merge-agent/first-use.md .cat/retrospectives/mistakes-2026-03.json
git commit -m "bugfix: block approval gate when post-conditions are Missing or Partial (M587)"
```

### Step 6: E2E Simulation (manual trace)

Trace the updated Step 6.5 logic manually:

1. Confirm `CRITERIA_FILE` path resolves correctly for the current session.
2. Simulate file absent: confirm the NOTE is output and flow continues to Step 7.
3. Simulate file present with all `Done` criteria: confirm flow continues to Step 7.
4. Simulate file present with one `Missing` criterion: confirm BLOCKED message is output and
   FAILED status is returned without reaching the squash or rebase steps.
5. Simulate file present with one `Partial` criterion: same — BLOCKED, not presented.

Document the simulation results as a comment appended to the Execution Plan section of this
PLAN.md file (the commit from Step 5 is already done at this point; do not amend it).

<!-- E2E Simulation Results (Step 6 trace — 2026-03-28)

Scenario 1: criteria-analysis.json absent
  CRITERIA_FILE="${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}/criteria-analysis.json"
  [[ ! -f "${CRITERIA_FILE}" ]] → true
  Output: "NOTE: criteria-analysis.json not found — post-condition gate check skipped"
  Result: Flow continues to Step 8 (squash). PASS.

Scenario 2: All criteria Done
  CRITERIA_FILE present. Contents include only "status": "Done" entries.
  UNMET=$(grep -E '"status"[[:space:]]*:[[:space:]]*"(Partial|Missing)"' ...) → empty string
  [[ -z "${UNMET}" ]] → true
  Result: Flow continues to Step 8 (squash). PASS.

Scenario 3: One Missing criterion
  CRITERIA_FILE present. One criterion has "status": "Missing", name: "Skill file updated".
  UNMET → non-empty
  UNMET_DETAILS computed via grep -EB5 + awk → "  - Skill file updated [Missing]"
  Output: "BLOCKED: Approval gate cannot be presented — unmet post-conditions detected."
           "  - Skill file updated [Missing]"
  Returned: {"status": "FAILED", "phase": "pre-gate-check", ...}
  Result: Gate not presented, squash/rebase skipped. PASS.

Scenario 4: One Partial criterion
  CRITERIA_FILE present. One criterion has "status": "Partial", name: "Tests passing".
  UNMET → non-empty
  UNMET_DETAILS → "  - Tests passing [Partial]"
  Output: BLOCKED message with criterion listed.
  Returned: {"status": "FAILED", ...}
  Result: Gate not presented. PASS.

All 4 scenarios verified. grep -EB5 flag ensures alternation in the pattern is active. awk-based
extraction handles field order variation (name before or after status) correctly.
-->

### Step 7: Correct prevention_path in mistakes-2026-03.json

The M587 entry in `.cat/retrospectives/mistakes-2026-03.json` has `prevention_path` set to
`"plugin/skills/work-merge-agent/first-use.md Step 7"`. The plan specified `Step 6.5`, but the
implementation correctly renumbered the step to `Step 7` per CLAUDE.md convention (no half-steps).
The retrospective entry must reflect the actual step label used in the implementation.

Read `.cat/retrospectives/mistakes-2026-03.json`, locate the M587 entry, and verify the current
value of `prevention_path`. If it already reads `"plugin/skills/work-merge-agent/first-use.md Step 7"`,
the entry is correct and no change is needed — this step is a no-op. If it reads `Step 6.5` or any
other value, update it to `"plugin/skills/work-merge-agent/first-use.md Step 7"`.

Commit the updated file:

```bash
cd "${WORKTREE_PATH}"
git add .cat/retrospectives/mistakes-2026-03.json
git commit -m "bugfix: correct M587 prevention_path to Step 7 (renamed from Step 6.5 per CLAUDE.md)"
```
