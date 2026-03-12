# Plan: update-skill-builder-tdd-to-use-adversarial-agents

## Type
feature

## Goal
Add a generalized adversarial TDD hardening loop to `tdd-implementation-agent` and update
`skill-builder-agent` to use the generalized dedicated-subagent interface from
`2.1-generalize-adversarial-agents-for-multi-target`.

## Context

### skill-builder-agent (update)

After `2.1-generalize-adversarial-agents-for-multi-target`, `plugin/skills/skill-builder-agent/first-use.md`
Step 4 already delegates to `cat:red-team-agent`, `cat:blue-team-agent`, and `cat:diff-validation-agent`.
However, `2.1-generalize-adversarial-agents-for-multi-target` may introduce minor interface mismatches
(parameter names, argument format) that need reconciling with the skill-builder invocation site. This issue
audits and tightens that integration.

Specifically:
- Verify that `target_type: skill_instructions` is passed correctly to both agents.
- Verify that round 2+ resume prompts include the `target_type` field so resumed agents do not revert to
  skill-specific language.
- Confirm diff-validation-agent is invoked with all required fields and that the loop aborts on validation
  failure.

### tdd-implementation-agent (new capability)

`plugin/skills/tdd-implementation-agent/first-use.md` currently implements the RED-GREEN-REFACTOR cycle with
no adversarial hardening phase. After the GREEN phase produces a passing test suite, there is no automated
mechanism to probe whether the tests are sufficiently comprehensive — a red-team agent could identify missing
assertions, untested edge cases, or overly broad assertions that allow incorrect implementations to pass.

This issue adds an adversarial hardening loop as a new step in the tdd-implementation workflow:

- After STEP 3 (REFACTOR) and before STEP 4 (ITERATE OR VERIFY), insert an optional adversarial hardening
  phase gated on the current behavior being fully implemented (all tests green).
- The red-team agent is invoked with `target_type: test_code` and `target_content: {CURRENT_TEST_CODE}` to
  find missing assertions and coverage gaps.
- The blue-team agent is invoked with `target_type: test_code` to close the identified gaps.
- The diff-validation-agent verifies that the blue-team's test edits address the red-team's findings.
- The loop runs up to 5 rounds (test hardening converges faster than skill instruction hardening).
- After the loop, the implementation is re-run against the hardened test suite to confirm it still passes.
  If the hardened tests expose a regression in the implementation, the tdd cycle re-enters RED.

### Scope boundary

This issue does NOT modify the agent SKILL.md files themselves (`red-team-agent`, `blue-team-agent`,
`diff-validation-agent`). Those are owned by `2.1-generalize-adversarial-agents-for-multi-target`. This
issue only modifies the consuming skills (`tdd-implementation-agent/first-use.md` and
`skill-builder-agent/first-use.md`).

## Pre-conditions

- [ ] `2.1-generalize-adversarial-agents-for-multi-target` is closed (red-team-agent, blue-team-agent, and
  diff-validation-agent dedicated subagents exist with `target_type` support)

## Sub-Agent Waves

### Wave 1: Audit and tighten skill-builder-agent integration

Read `plugin/skills/skill-builder-agent/first-use.md` Step 4 as produced by
`2.1-generalize-adversarial-agents-for-multi-target` and verify:

1. Round 1 red-team Task call passes `target_type: skill_instructions`.
2. Round 1 blue-team Task call passes `target_type: skill_instructions` and `target_file_path`.
3. Round 2+ resume prompts for both agents include `target_type`.
4. Diff-validation-agent is invoked after each blue-team round with all required fields
   (`red_team_commit_hash`, `blue_team_commit_hash`, `target_file_path`, `target_type`).
5. Loop abort on diff-validation failure is implemented (not just logged).

If any of these are missing or inconsistent, apply the minimal fix to `first-use.md`.

- Files: `plugin/skills/skill-builder-agent/first-use.md`

### Wave 2: Add adversarial test hardening phase to tdd-implementation-agent

Insert a new "STEP 3.5: ADVERSARIAL TEST HARDENING" section into
`plugin/skills/tdd-implementation-agent/first-use.md` between STEP 3 (REFACTOR) and STEP 4 (ITERATE OR
VERIFY).

The new step must:

1. **Gate on GREEN state:** Only run if all tests currently pass. If the test suite is not fully green at
   this point, skip this step and proceed directly to STEP 4.

2. **Read test file content:** Collect the current content of all test files modified in this TDD cycle as
   `CURRENT_TEST_CODE`.

3. **Run adversarial hardening loop (up to 5 rounds):**

   - Spawn `cat:red-team-agent` with:
     - `target_type: test_code`
     - `target_content: {CURRENT_TEST_CODE}`
   - Read `major_loopholes_found` from the red-team's findings commit. If false, exit loop early.
   - Spawn `cat:blue-team-agent` with:
     - `target_type: test_code`
     - `target_file_path: {TEST_FILE_PATH}` (one invocation per test file if multiple files)
     - `red_team_commit_hash: {RED_TEAM_COMMIT_HASH}`
   - Invoke `cat:diff-validation-agent` to verify the patch. Abort loop on validation failure.
   - Increment round counter. Continue if round < 5.

4. **Re-run implementation tests against hardened suite:** After the loop exits, run the full test suite.
   - If all tests still pass: proceed to STEP 4 normally.
   - If any tests fail (hardened tests exposed a gap in the implementation): log "Adversarial hardening
     revealed uncovered behavior. Returning to RED." and re-enter STEP 1 with the failing test as the
     starting point.

5. **Display hardening summary:** After the loop, print a summary:

   ```
   Adversarial test hardening: {N} rounds, {M} assertions added, {K} edge cases covered
   ```

Update the TDD State Machine diagram to reflect the new STEP 3.5 node and its transitions.

- Files: `plugin/skills/tdd-implementation-agent/first-use.md`

### Wave 2b: Fix TDD State Machine diagram node label

In `plugin/skills/tdd-implementation-agent/first-use.md`, update the TDD State Machine diagram so
the hardening node is labeled `[STEP 3.5]` instead of `[HARDEN]`, and make the PASS and FAIL
transitions explicit with labels indicating the target state (STEP 4 for pass, STEP 1 for fail).

The diagram must clearly show:
- A node labeled `[STEP 3.5]` (not `[HARDEN]`)
- An explicit transition from `[STEP 3.5]` to `[STEP 4]` (or `[ITERATE OR VERIFY]`) labeled PASS
- An explicit transition from `[STEP 3.5]` to `[STEP 1]` (or `[RED]`) labeled FAIL

- Files: `plugin/skills/tdd-implementation-agent/first-use.md`

### Wave 2c: Fix inverted PASS/FAIL labels on STEP 3.5 transitions in TDD State Machine diagram

In `plugin/skills/tdd-implementation-agent/first-use.md`, the TDD State Machine diagram currently has the
PASS and FAIL labels semantically inverted on the STEP 3.5 transitions:

- The label `PASS: impl gap revealed → re-enter STEP 1` is attached to the STEP 1 transition, but "impl gap
  revealed" is a **FAIL** condition (hardened tests expose an implementation gap), not a PASS.
- The rightward arrow from `[STEP 3.5]` to `[ITERATE OR VERIFY]` (STEP 4) has no explicit `PASS` label.

Fix the diagram so that:
1. The arrow from `[STEP 3.5]` to `[STEP 4]` (or `[ITERATE OR VERIFY]`) is explicitly labeled **PASS** (all
   tests still pass after hardening).
2. The arrow from `[STEP 3.5]` to `[STEP 1]` (or `[RED]`) is explicitly labeled **FAIL** (hardened tests
   exposed an implementation gap).
3. Remove or correct the `PASS: impl gap revealed → re-enter STEP 1` label so it does not misleadingly map
   PASS to the failure path.

- Files: `plugin/skills/tdd-implementation-agent/first-use.md`

### Wave 3: Verification

Manual trace through both updated skills:

**skill-builder-agent:**
1. Confirm Step 4 round 1 and round 2+ calls include `target_type` for both agents.
2. Confirm diff-validation-agent is invoked every round with the four required fields.
3. Confirm loop aborts (not just logs) when diff-validation fails.

**tdd-implementation-agent:**
1. Confirm STEP 3.5 is gated on GREEN state (skipped if tests not passing).
2. Confirm `target_type: test_code` is passed to red-team and blue-team agents.
3. Confirm hardened test failure re-enters STEP 1 rather than masking the gap.
4. Confirm the TDD State Machine diagram includes STEP 3.5 as a node.
5. Confirm the hardening summary line is printed after the loop exits.

- Files: `plugin/skills/skill-builder-agent/first-use.md`,
  `plugin/skills/tdd-implementation-agent/first-use.md`

## Post-conditions

- [ ] `plugin/skills/skill-builder-agent/first-use.md` Step 4 passes `target_type: skill_instructions` in
  both round 1 and round 2+ Task calls for red-team and blue-team agents
- [ ] `plugin/skills/skill-builder-agent/first-use.md` Step 4 invokes `cat:diff-validation-agent` after
  every blue-team round with `red_team_commit_hash`, `blue_team_commit_hash`, `target_file_path`, and
  `target_type`
- [ ] `plugin/skills/skill-builder-agent/first-use.md` Step 4 aborts the loop (not just logs) when
  diff-validation-agent exits non-zero
- [ ] `plugin/skills/tdd-implementation-agent/first-use.md` contains a "STEP 3.5: ADVERSARIAL TEST
  HARDENING" section between STEP 3 and STEP 4
- [ ] STEP 3.5 is skipped when the test suite is not fully green
- [ ] STEP 3.5 passes `target_type: test_code` to red-team-agent and blue-team-agent
- [ ] STEP 3.5 re-enters STEP 1 if hardened tests expose an implementation gap
- [ ] STEP 3.5 runs a maximum of 5 adversarial rounds
- [ ] STEP 3.5 prints a hardening summary after the loop exits
- [ ] The TDD State Machine diagram in `tdd-implementation-agent/first-use.md` includes STEP 3.5 as a node
  with transitions to STEP 4 (pass) and STEP 1 (fail)
- [ ] No modifications to `red-team-agent/SKILL.md`, `blue-team-agent/SKILL.md`, or
  `diff-validation-agent/SKILL.md` (those are owned by the prerequisite issue)
