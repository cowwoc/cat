<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Trust Gate E2E Verification Checklist

Manual end-to-end checklist for verifying that trust-level gate routing behaves correctly when running
`/cat:work` with a real issue. Run each scenario in sequence using an isolated test issue.

## Prerequisites

- A test issue with a valid `plan.md` containing `## Goal` and `## Post-conditions` sections.
- The CAT config file at `.cat/config.json` must be writable to change the `trust` value between runs.
- Run each scenario from a fresh worktree (re-run `/cat:work` after changing trust level).

---

## Scenario A: trust=low (2 approval gates expected)

**Setup:** Set `"trust": "low"` in `.cat/config.json`.

### Gate 1 — Pre-Implementation Review (work-implement-agent Step 4)

- [ ] The pre-implementation gate is displayed before any code is written.
- [ ] The gate header reads: `<issue-id> — Pre-Implementation Review`
- [ ] The gate shows the issue **Goal** (extracted from `## Goal` in plan.md).
- [ ] The gate shows the issue **Post-conditions** (extracted from `## Post-conditions` in plan.md).
- [ ] The gate shows the **Estimated token cost**.
- [ ] Selecting **"Approve and start"** proceeds to implementation (Step 5).
- [ ] Selecting **"Request changes"** releases the lock and returns `BLOCKED` — no implementation starts.
- [ ] Selecting **"Abort"** releases the lock and returns `BLOCKED` — no implementation starts.

### Gate 2 — Pre-Merge Approval Gate (work-merge-agent Step 12)

- [ ] After implementation and review complete, the standard pre-merge gate is shown.
- [ ] The gate presents the diff, commit summary, issue goal, and stakeholder concerns.
- [ ] Selecting **"Approve and merge"** proceeds to merge (Step 13).
- [ ] Selecting **"Request changes"** or **"Abort"** returns control to the user without merging.

**Expected total gates for trust=low:** 2 (one before implementation, one before merge).

---

## Scenario B: trust=medium (1 approval gate expected)

**Setup:** Set `"trust": "medium"` in `.cat/config.json`.

### Gate 1 — Pre-Merge Approval Gate only (work-merge-agent Step 12)

- [ ] No pre-implementation gate is shown — implementation starts immediately after lock acquisition.
- [ ] After implementation and review complete, the standard pre-merge gate is shown.
- [ ] The gate presents the diff, commit summary, issue goal, and stakeholder concerns.
- [ ] Selecting **"Approve and merge"** proceeds to merge.

**Expected total gates for trust=medium:** 1 (merge gate only).

---

## Scenario C: trust=high, clean review (0 gates expected — auto-merge)

**Setup:** Set `"trust": "high"` in `.cat/config.json`. Ensure the stakeholder review returns
`REVIEW_PASSED` with `has_high_or_critical: false` (no HIGH or CRITICAL concerns).

### Auto-merge path (work-merge-agent Step 12)

- [ ] No pre-implementation gate is shown.
- [ ] After implementation, the review runs and persists a result file with
      `"status": "REVIEW_PASSED"` and `"has_high_or_critical": false`.
- [ ] `HIGH_TRUST_PAUSE` is set to `false`.
- [ ] Merge proceeds automatically — no `AskUserQuestion` is invoked.
- [ ] The approval marker is written automatically (`APPROVAL_MARKER=true`).
- [ ] The issue branch is merged into the target branch without user intervention.

**Expected total gates for trust=high (clean review):** 0.

---

## Scenario D: trust=high, HIGH severity concerns (gate pauses)

**Setup:** Set `"trust": "high"` in `.cat/config.json`. The stakeholder review must return
`has_high_or_critical: true` (at least one HIGH or CRITICAL concern present).

### Pause path (work-merge-agent Step 12)

- [ ] After implementation, the review runs and persists a result file with
      `"has_high_or_critical": true`.
- [ ] `HIGH_TRUST_PAUSE` is set to `true`.
- [ ] The standard pre-merge gate is displayed with all concerns listed.
- [ ] Options offered: `["Approve and merge", "Fix concerns", "Abort"]`
- [ ] Selecting **"Approve and merge"** proceeds to merge.
- [ ] Selecting **"Fix concerns"** returns control to the user without merging.

**Expected total gates for trust=high (HIGH concerns):** 1 (merge gate with concerns displayed).

---

## Scenario E: trust=high, CONCERNS_FOUND verdict (gate pauses)

**Setup:** Set `"trust": "high"` in `.cat/config.json`. The stakeholder review must return
`"status": "CONCERNS_FOUND"` (review rejected verdict).

### Pause path (work-merge-agent Step 12)

- [ ] After implementation, the review persists a result file with `"status": "CONCERNS_FOUND"`.
- [ ] `HIGH_TRUST_PAUSE` is set to `true`.
- [ ] The standard pre-merge gate is displayed with rejection reasons.
- [ ] Options offered: `["Approve and merge", "Fix concerns", "Abort"]`
- [ ] Selecting **"Approve and merge"** proceeds to merge.
- [ ] Selecting **"Fix concerns"** or **"Abort"** returns control to the user without merging.

**Expected total gates for trust=high (CONCERNS_FOUND):** 1 (merge gate with concerns displayed).
