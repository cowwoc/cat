# Plan: Fix Work-Merge Gate Timing — Block Approval Gate Until All Background Tasks Complete

## Problem

`plugin/skills/work-merge-agent/first-use.md` allows the approval gate (AskUserQuestion in Step 9) to be
presented while background tasks are still running. The Pre-Gate Skill-Builder Review section instructs
the agent to invoke `/cat:skill-builder` but does not specify whether execution must be synchronous, or
that any background task started earlier (via `run_in_background: true`) must complete before the gate.
In practice, skill-builder was run in the background and the approval gate was presented before
skill-builder returned, so the gate showed no skill-builder results. (Recorded as mistake M539.)

The fix must be generalized: ANY background task started during Steps 7-9 must complete before the
approval gate is presented — not just skill-builder.

## Parent Requirements

None

## Reproduction Code

```
# In work-merge-agent/first-use.md Step 9, Pre-Gate Skill-Builder Review:
# Agent invokes:
Agent tool:
  run_in_background: true      # ← background task started
  subagent_type: "cat:skill-builder-agent"
  prompt: "review plugin/skills/..."

# Then immediately proceeds to:
Skill("cat:get-diff-agent", ...)   # pre-gate diff
AskUserQuestion(...)               # approval gate ← background task still running!
```

## Expected vs Actual

- **Expected:** Approval gate (AskUserQuestion) presented only AFTER all background tasks have
  returned their results (via `<task-notification>` system messages)
- **Actual:** Approval gate presented while skill-builder (or other background tasks) are still
  executing; their results are unavailable to inform the gate or are received mid-approval

## Root Cause

The MANDATORY STEPS section and the Pre-Gate Skill-Builder Review section in
`plugin/skills/work-merge-agent/first-use.md` do not state that background tasks must complete
before Step 9's AskUserQuestion. Without this rule, agents may run background tasks concurrently
with gate presentation, violating the intent that gate output must reflect all completed work.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Changing a documentation rule could cause agents to wait too long if they
  misinterpret "all background tasks" as blocking even tasks started before Step 7. Mitigated by
  scoping the rule to Steps 7-9.
- **Mitigation:** Static validation script verifies rule presence; E2E test confirms the rule is
  followed in a work-merge-agent execution.

## Files to Modify

- `plugin/skills/work-merge-agent/first-use.md` — Add MANDATORY rule to MANDATORY STEPS section
  and add a new "Pre-Gate Background Task Completion" blocking step in Step 9, before the
  AskUserQuestion invocation

## Test Cases

- [ ] Rule documented: `first-use.md` contains a general MANDATORY rule about background task
  completion before the approval gate (not skill-builder-specific)
- [ ] Rule positioned correctly: The blocking check appears in Step 9 before `AskUserQuestion`
- [ ] No regressions: Existing Step 9 flow (squash verification, skill-builder review,
  artifact cleanup, pre-gate output, AskUserQuestion) remains intact

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Modify `plugin/skills/work-merge-agent/first-use.md`:

  **Change 1 — MANDATORY STEPS section (lines 11-18):**
  Add a new bullet after the existing "Step 9 (sub-step): Skill-Builder Review" bullet:
  ```
  - **Step 9 (sub-step): Background Task Completion** — before presenting the approval gate
    (AskUserQuestion), ALL tasks launched with `run_in_background: true` during Steps 7-9 must
    have returned their results via `<task-notification>`. Do NOT invoke AskUserQuestion while
    any background task is still executing.
  ```

  **Change 2 — Step 9 body, add new subsection before "Present Changes Before Approval Gate":**
  Insert a new subsection titled "### Pre-Gate Background Task Completion (MANDATORY — BLOCKING)"
  immediately BEFORE the "### Present Changes Before Approval Gate" subsection (around line 399).
  Content:
  ```markdown
  ### Pre-Gate Background Task Completion (MANDATORY — BLOCKING)

  **Before presenting any pre-gate output or the approval gate, ensure ALL background tasks have
  completed.** This applies to any task started with `run_in_background: true` via the Agent tool
  during Steps 7-9 (including skill-builder review if invoked in the background).

  **How to verify completion:** Background Agent tasks deliver results exclusively via
  `<task-notification>` system messages. A background task is complete when its
  `<task-notification>` has appeared in the session. Do NOT assume a background task is complete
  based on elapsed time or conversation turns.

  **If any background task has not yet completed:**
  1. DO NOT invoke `cat:get-diff-agent` or any other pre-gate output step
  2. DO NOT invoke AskUserQuestion
  3. Wait until the `<task-notification>` arrives for each outstanding background task
  4. Process the task result BEFORE proceeding to pre-gate output

  **Why this matters:** The approval gate must reflect the results of ALL completed work,
  including skill-builder findings. A gate presented before background tasks complete is missing
  information the user needs to make an informed approval decision.
  ```

  Files: `plugin/skills/work-merge-agent/first-use.md`

- Add static validation script `plugin/scripts/validate-work-merge-gate-timing.sh`:
  ```bash
  #!/usr/bin/env bash
  # Copyright (c) 2026 Gili Tzabari. All rights reserved.
  #
  # Licensed under the CAT Commercial License.
  # See LICENSE.md in the project root for license terms.
  #
  # Validates that work-merge-agent/first-use.md contains the required
  # background task completion rule before the approval gate.
  set -euo pipefail

  SKILL_FILE="plugin/skills/work-merge-agent/first-use.md"

  if [[ ! -f "$SKILL_FILE" ]]; then
    echo "ERROR: $SKILL_FILE not found" >&2
    exit 1
  fi

  # Check MANDATORY STEPS contains background task completion rule
  if ! grep -q "Background Task Completion" "$SKILL_FILE"; then
    echo "FAIL: MANDATORY STEPS missing 'Background Task Completion' rule in $SKILL_FILE" >&2
    exit 1
  fi

  # Check pre-gate section exists
  if ! grep -q "Pre-Gate Background Task Completion" "$SKILL_FILE"; then
    echo "FAIL: Missing 'Pre-Gate Background Task Completion' section in $SKILL_FILE" >&2
    exit 1
  fi

  # Check the rule is general (not skill-builder-specific)
  if ! grep -A5 "Pre-Gate Background Task Completion" "$SKILL_FILE" | grep -q "run_in_background"; then
    echo "FAIL: 'Pre-Gate Background Task Completion' section does not reference run_in_background" >&2
    exit 1
  fi

  echo "PASS: work-merge-agent gate timing rule verified"
  exit 0
  ```
  Files: `plugin/scripts/validate-work-merge-gate-timing.sh`

- Commit both changes with message:
  `bugfix: enforce all background tasks complete before work-merge approval gate`

  Update STATE.md in the same commit: status: closed, progress: 100%

## Post-conditions

- [ ] Bug resolved: `plugin/skills/work-merge-agent/first-use.md` contains a MANDATORY rule that
  ALL background tasks must complete before the approval gate is presented
- [ ] Rule is general-purpose: the rule references `run_in_background: true` as the trigger
  condition, not a skill-builder-specific condition
- [ ] New "Pre-Gate Background Task Completion" subsection exists in Step 9 BEFORE "Present
  Changes Before Approval Gate" and BEFORE AskUserQuestion
- [ ] `plugin/scripts/validate-work-merge-gate-timing.sh` exists and passes (`exit 0`)
- [ ] No regressions: existing Step 9 sections (squash verification, skill-builder review,
  post-skill-builder cleanup, pre-gate output, AskUserQuestion flow) remain structurally intact
- [ ] E2E: the full work-merge-agent flow works correctly (no broken references or orphaned steps)
