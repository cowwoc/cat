<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# E2E Dispute Trace — Blue-Team False-Premise Scenario

This document is a design-time simulation of the adversarial TDD loop under a controlled false-premise scenario,
documenting the expected end-to-end behavior of the blue-team dispute mechanism.

## Scenario Setup

- **Target skill:** `plugin/skills/git-commit-agent/SKILL.md` (minimal real skill used as subject)
- **Red-team instruction:** Raise exactly one false-premise finding about the skill
- **False premise injected:** Red-team claims the skill does not have access to `${WORKTREE_PATH}`, asserting
  this variable is unavailable in the execution environment, and therefore the skill cannot locate the
  worktree root for commit operations.
- **Blue-team expected action:** Dispute the finding with evidence that `${WORKTREE_PATH}` is a documented
  built-in variable injected by the plugin at skill invocation time.

---

## Round 1 — Red-Team Phase

**Red-team subagent spawned.** It analyzes `git-commit-agent/SKILL.md` and writes `findings.json`:

```json
{
  "loopholes": [
    {
      "id": "RT-1",
      "severity": "HIGH",
      "description": "The skill references ${WORKTREE_PATH} but this variable is not available in the \
plugin execution environment. Commit operations will fail at runtime because the worktree root cannot be \
resolved.",
      "location": "SKILL.md line 12"
    }
  ],
  "disputed": []
}
```

**Red-team commit hash:** `abc1234`

---

## Round 1 — Blue-Team Phase

**Blue-team subagent spawned** with the findings at `abc1234` and the Dispute Protocol:

> Before patching any finding, verify its premise. If a finding claims something that is factually incorrect
> ... do NOT patch it. Instead, move it from the `loopholes` array to the `disputed` array in findings.json
> with two fields: `"false_premise"` and `"evidence"`.

**Blue-team evaluation of RT-1:**

The blue-team reads the CAT plugin documentation and confirms that `${WORKTREE_PATH}` is listed in the
built-in variables injected at skill invocation time (documented in `plugin/concepts/skill-loading.md`).
The red-team's claim that the variable is unavailable is factually incorrect.

**Blue-team action:** Moves RT-1 from `loopholes` to `disputed`. Does NOT patch the skill file.

**Updated findings.json written by blue-team:**

```json
{
  "loopholes": [],
  "disputed": [
    {
      "id": "RT-1",
      "severity": "HIGH",
      "false_premise": "Red-team claimed ${WORKTREE_PATH} is not available in the plugin execution \
environment and that commit operations will fail at runtime.",
      "evidence": "WORKTREE_PATH is a documented built-in variable injected by the plugin at skill \
invocation time. See plugin/concepts/skill-loading.md § Built-in Variables: \
[CLAUDE_PLUGIN_ROOT, CLAUDE_SESSION_ID, CLAUDE_PROJECT_DIR, WORKTREE_PATH]. The variable is \
reliably present for all skills invoked inside a worktree context."
    }
  ]
}
```

**Blue-team commit hash:** `def5678`

No skill file changes are committed — only `findings.json` is updated.

---

## Round 1 — Arbitration Phase

Because blue-team moved RT-1 to `disputed`, the main agent spawns a fresh arbitration subagent (not the
red-team or blue-team agent) to independently verify the dispute.

**Arbitration subagent spawned** with the disputed entries from `def5678`:

> You are an independent arbitration agent. For each disputed finding, read the `"false_premise"` and
> `"evidence"` fields. Independently verify the evidence. Return a JSON verdict array.

**Arbitration agent evaluation of RT-1:**

The arbitration agent reads `plugin/concepts/skill-loading.md` and locates the "Built-in Variables" section.
It confirms that `WORKTREE_PATH` is listed as a built-in variable injected at skill invocation time. The
blue-team's evidence matches the documentation — the red-team's premise is false.

**Arbitration verdict:**

```json
[
  {
    "finding_id": "RT-1",
    "verdict": "upheld",
    "reasoning": "Confirmed: plugin/concepts/skill-loading.md § Built-in Variables lists WORKTREE_PATH as \
injected at skill invocation time. The red-team's claim that the variable is unavailable is factually \
incorrect. Blue-team evidence is accurate."
  }
]
```

**Main agent action:** Verdict is `"upheld"`. The main agent adds `"arbitration_verdict": "upheld"` to the
RT-1 entry in `findings.json` and writes the updated file.

**Updated findings.json after arbitration:**

```json
{
  "loopholes": [],
  "disputed": [
    {
      "id": "RT-1",
      "severity": "HIGH",
      "false_premise": "Red-team claimed ${WORKTREE_PATH} is not available in the plugin execution \
environment and that commit operations will fail at runtime.",
      "evidence": "WORKTREE_PATH is a documented built-in variable injected by the plugin at skill \
invocation time. See plugin/concepts/skill-loading.md § Built-in Variables: \
[CLAUDE_PLUGIN_ROOT, CLAUDE_SESSION_ID, CLAUDE_PROJECT_DIR, WORKTREE_PATH]. The variable is \
reliably present for all skills invoked inside a worktree context.",
      "arbitration_verdict": "upheld"
    }
  ]
}
```

No rejected disputes — blue-team is not resumed. Proceed to diff validation.

---

## Diff Validation

The diff-validation subagent checks `git diff abc1234..def5678 -- git-commit-agent/SKILL.md`.

Result: `{"status": "VALID"}` — no skill file was modified, which is correct because the only disputed
finding was moved to the `disputed` array and required no patch.

---

## Round Completion

```
Read the skill file at BLUE_TEAM_COMMIT_HASH to update CURRENT_INSTRUCTIONS.
Increment round counter. If round < 10: continue to next iteration.

> Note: Disputed findings count as closed for round-advancement purposes. The round counter increments
> whether findings were patched, disputed, or both.
```

**Round counter:** 1 → 2

---

## Round 2 — Red-Team Phase

**Red-team subagent resumed.** It re-analyzes `git-commit-agent/SKILL.md` from `def5678` for any new loopholes not
previously identified.

**Red-team evaluation:** No new loopholes are found. The skill file has not changed since round 1, and the original
finding RT-1 has already been evaluated. Red-team returns an updated `findings.json` with no new entries:

```json
{
  "loopholes": [],
  "disputed": [
    {
      "id": "RT-1",
      "severity": "HIGH",
      "false_premise": "Red-team claimed ${WORKTREE_PATH} is not available in the plugin execution \
environment and that commit operations will fail at runtime.",
      "evidence": "WORKTREE_PATH is a documented built-in variable injected by the plugin at skill \
invocation time. See plugin/concepts/skill-loading.md § Built-in Variables: \
[CLAUDE_PLUGIN_ROOT, CLAUDE_SESSION_ID, CLAUDE_PROJECT_DIR, WORKTREE_PATH]. The variable is \
reliably present for all skills invoked inside a worktree context."
    }
  ]
}
```

**Red-team commit hash:** `ghi9012`

---

## Termination Check

Read `findings.json` from `ghi9012`. Scan only the `loopholes` array for CRITICAL or HIGH entries:

```
loopholes array: []   ← empty
```

No CRITICAL or HIGH entries in `loopholes`. **Termination condition met. Loop stops after round 2.**

The disputed entry in the `disputed` array is NOT counted toward the termination check, consistent with:

> Findings moved to `disputed` by blue-team do not count toward this threshold.

---

## Summary Table Output

After loop termination, the batch summary table is rendered:

| Skill | Rounds | Loopholes Closed | Disputes Upheld | Patches Applied |
|-------|--------|-----------------|-----------------|-----------------|
| git-commit-agent/SKILL.md | 2 | 0 | 1 | 0 |

**Verification:**

- (a) Finding RT-1 appears in `"disputed"` array, NOT in `"loopholes"` array. ✓
- (b) RT-1 entry in `"disputed"` has `"arbitration_verdict": "upheld"` added by the main agent. ✓
- (c) No patch was applied to the skill file — blue-team commit touched only `findings.json`. ✓
- (d) Summary shows **Disputes Upheld: 1** and **Patches Applied: 0**. ✓
- (e) Termination check reads `findings.json` from the round-2 red-team commit hash (`ghi9012`); `loopholes` is
  empty, so loop terminates after round 2. ✓

---

## Conclusion

The blue-team dispute mechanism with arbitration operates correctly end-to-end:

1. Red-team raises a false-premise finding (HIGH severity).
2. Blue-team evaluates the finding, confirms the premise is false with cited evidence, and moves it to
   `disputed` without patching the skill.
3. The `disputed` array in `findings.json` is populated with `false_premise` and `evidence` fields.
4. The arbitration agent independently verifies the blue-team's evidence against the actual documentation
   and returns verdict `"upheld"`. The `"arbitration_verdict": "upheld"` field is added to the entry.
5. Diff validation passes because no out-of-scope changes were made.
6. The round counter increments normally — an arbitration-upheld dispute round advances the counter just
   as a patch-only round would.
7. Round 2 red-team finds no new loopholes and returns commit `ghi9012`.
8. The termination check reads `findings.json` from the round-2 red-team commit (`ghi9012`); the `loopholes`
   array is empty, so the loop terminates after round 2.
9. The summary table correctly reports **Disputes Upheld: 1** and **Patches Applied: 0**.
