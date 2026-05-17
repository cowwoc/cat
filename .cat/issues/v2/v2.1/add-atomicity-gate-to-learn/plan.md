# Plan: add-atomicity-gate-to-learn

## Goal

Add an atomicity scoring quality gate to `cat:learn` before Phase 4 recording. The gate must evaluate each
proposed prevention rule against a rubric (`specific`, `verifiable`, `actionable`) and hard-block vague rules,
forcing revision guidance before `record-learning` can run.

## Problem

`cat:learn` currently validates structural fields (required keys, commit/hash integrity) before recording, but it
does not validate the quality of the prevention rule text itself. This allows vague guidance such as "be more
careful" or "ensure correctness" to pass through the pipeline and be persisted as a completed prevention.

## Parent Requirements

None

## Research Findings

- The hard recording boundary is in `client/plugin/skills/common/learn/first-use.md` Step 4 (`Validate Subagent
  Output, Verify Commit, Run record-learning CLI`). This is the right insertion point for a hard gate because it
  runs immediately before `record-learning`.
- `phase-prevent.md` currently outputs free-form `prevention_description` but does not provide a structured list of
  individual prevention rules for deterministic per-rule evaluation.
- Existing learn skill tests already use assertion-style markdown fixtures under
  `client/plugin/tests/skills/learn/phase-prevent/`; adding first-use and phase-prevent fixtures is consistent with
  the current test corpus.

## Approaches

### A: Add structured prevention rules to Phase 3 output + enforce rubric gate in Phase 4 (Selected)

- **Risk:** MEDIUM
- **Scope:** 4-6 files (moderate)
- **Description:** Add a structured `prevent.prevention_rules` array in `phase-prevent.md` output contract and make
  `first-use.md` enforce an atomicity rubric per rule before invoking `record-learning`.

### B: Add rubric language only in `phase-prevent.md` (Rejected)

- **Risk:** LOW
- **Scope:** 1 file (small)
- **Description:** Ask the subagent to write better rules but do not add a hard checkpoint in `first-use.md`.
- **Rejection reason:** This is advisory only and does not satisfy the requirement for hard-blocking vague rules before
  recording.

### C: Enforce rule quality inside Java `record-learning` tool (Rejected)

- **Risk:** HIGH
- **Scope:** multi-module Java + runtime tests
- **Description:** Move natural-language quality scoring into `client/bin/record-learning` implementation.
- **Rejection reason:** The quality rubric is semantic and LLM-judgment based; `record-learning` is intentionally a
  mechanical I/O tool. This would over-couple semantics into a non-LLM boundary and increase regression risk.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Regression Risk:** Overly strict rubric checks could block valid concise rules; output-schema change could break
  downstream parsing if not documented clearly.
- **Mitigation:**
  - Define explicit pass/fail heuristics and accepted examples in `first-use.md`.
  - Add negative and positive skill tests covering vague and specific rules.
  - Keep compatibility by adding new fields rather than replacing existing ones.

## Files to Modify

- `client/plugin/skills/common/learn/phase-prevent.md`
  - Extend Phase 3 output schema with `prevention_rules` (array of one or more rule strings).
  - Add instructions that each rule must be a concrete imperative statement that can be rubric-evaluated.
- `client/plugin/skills/common/learn/first-use.md`
  - Add a mandatory atomicity quality gate between field validation and `record-learning` invocation.
  - Enforce per-rule rubric checks (`specific`, `verifiable`, `actionable`) and fail fast on any failed rule.
  - Add deterministic error format with actionable rewrite guidance for rejected rules.
- `client/plugin/tests/skills/learn/phase-prevent/atomicity-rules-output-required.md` (new)
  - Requirement fixture ensuring phase-prevent output includes non-empty `prevention_rules` entries.
- `client/plugin/tests/skills/learn/first-use/atomicity-gate-blocks-vague-rules.md` (new)
  - Requirement fixture asserting vague rules are hard-blocked before recording.
- `client/plugin/tests/skills/learn/first-use/atomicity-gate-passes-specific-rules.md` (new)
  - Requirement fixture asserting concrete rules pass the gate and recording may proceed.
- `client/plugin/tests/skills/learn/first-use/atomicity-gate-revision-guidance.md` (new)
  - Requirement fixture asserting rejection output includes criterion-specific rewrite guidance.

## Test Cases

- [ ] Vague rule (`"be more careful"`) is rejected with failed criteria and no record-learning call.
- [ ] Vague rule (`"ensure correctness"`) is rejected with failed criteria and no record-learning call.
- [ ] Specific rule (`"Always validate commit hash before calling record-learning"`) passes atomicity gate.
- [ ] Rejection response includes explicit failed criteria (`specific|verifiable|actionable`) and rewrite guidance.
- [ ] Existing Step 4a/4b validation flow remains intact and still blocks malformed commit/hash/path claims.
- [ ] Missing/invalid `prevent.prevention_rules` (`absent|null|non-array|empty`) is hard-blocked before recording.

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- Update `client/plugin/skills/common/learn/phase-prevent.md` output contract:
  - Add `prevention_rules` to the JSON output format as a required array.
  - Require each element to be a single prevention rule sentence in imperative form.
  - Add explicit examples of invalid vague rules (`"be more careful"`, `"ensure correctness"`) and valid specific
    rules (`"Always validate commit hash before calling record-learning"`).

- Update `client/plugin/skills/common/learn/first-use.md` with a new atomicity gate before recording:
  - Keep existing Step 4a (required fields) and Step 4b (commit verification) behavior.
  - Insert a new checkpoint section before the `record-learning` call that:
    - Reads `prevent.prevention_rules`.
    - Hard-fails immediately when `prevent.prevention_rules` is missing, null, not an array, or empty.
    - Evaluates each rule against three criteria:
      - `specific`: names concrete target/context (file, command, condition, or artifact).
      - `verifiable`: provides observable pass/fail behavior that can be checked.
      - `actionable`: states a clear action, not a generic aspiration.
    - Hard-fails if any rule misses any criterion.
    - Emits structured revision guidance using this exact JSON block in the error message:
      ```json
      {
        "atomicity_gate_failed": true,
        "failed_rules": [
          {
            "rule_index": 0,
            "rule_text": "<original rule>",
            "failed_criteria": ["specific", "verifiable"],
            "rewrite_guidance": "Rewrite as a concrete action with observable pass/fail check."
          }
        ]
      }
      ```
  - Ensure the gate explicitly forbids invoking `record-learning` when any rule fails.
  - Renumber local sub-steps if needed so checkpoint ordering is unambiguous.

- Add learn skill test fixtures:
  - Create `client/plugin/tests/skills/learn/phase-prevent/atomicity-rules-output-required.md` with assertions that
    the phase output includes `prevention_rules` and that entries are concrete rule statements.
    - Assertions:
      1. response includes `prevention_rules` field in output JSON
      2. `prevention_rules` is a non-empty array
      3. every rule is an imperative action (not a generic aspiration)
  - Create `client/plugin/tests/skills/learn/first-use/atomicity-gate-blocks-vague-rules.md` asserting that vague
    rules are blocked and recording does not proceed.
    - Assertions:
      1. response marks atomicity gate failure before record-learning
      2. `"be more careful"` fails at least `specific` and `verifiable`
      3. response explicitly indicates record-learning is not invoked
  - Create `client/plugin/tests/skills/learn/first-use/atomicity-gate-passes-specific-rules.md` asserting specific
    rules pass the gate.
    - Assertions:
      1. `"Always validate commit hash before calling record-learning"` passes all three criteria
      2. response indicates gate pass and allows progression to record-learning checkpoint
  - Create `client/plugin/tests/skills/learn/first-use/atomicity-gate-revision-guidance.md` asserting criterion-level
    rewrite guidance is returned for rejected rules.
    - Assertions:
      1. rejection includes `failed_rules[].rule_index`
      2. rejection includes `failed_rules[].failed_criteria`
      3. rejection includes `failed_rules[].rewrite_guidance`

- Validate end-to-end behavior and regression safety:
  - Run plugin skill tests for learn fixtures:
    - `cd "${WORKTREE_PATH}" && ./client/mvnw -f client/pom.xml package`
    - `cd "${WORKTREE_PATH}" && "${WORKTREE_PATH}/client/distribution/target/jlink/claude/bin/instruction-test-runner" run-full-sprt "${WORKTREE_PATH}" "client/plugin/tests/skills/learn/first-use" "claude-haiku-4-5" "${CAT_SESSION_ID}"`
    - `cd "${WORKTREE_PATH}" && "${WORKTREE_PATH}/client/distribution/target/jlink/claude/bin/instruction-test-runner" run-full-sprt "${WORKTREE_PATH}" "client/plugin/tests/skills/learn/phase-prevent" "claude-haiku-4-5" "${CAT_SESSION_ID}"`
  - Run full verification command required by project workflow:
    - `mvn -f client/pom.xml verify -e`

- Close issue metadata in the same implementation commit:
  - Update
    `.cat/issues/v2/v2.1/add-atomicity-gate-to-learn/index.json`
    status to `"closed"` after all checks pass.

## Post-conditions

- [ ] Atomicity gate evaluates each prevention rule against `specific`, `verifiable`, and `actionable`.
- [ ] Vague prevention rules are hard-blocked before recording.
- [ ] Specific prevention rules pass the gate and allow recording.
- [ ] Rejected rules receive criterion-specific revision guidance.
- [ ] `phase-prevent.md` output schema includes required `prevention_rules` for deterministic gate input.
- [ ] Tests pass: both `run-full-sprt` commands for `learn/first-use` and `learn/phase-prevent` exit 0.
- [ ] No regressions: `mvn -f client/pom.xml verify -e` exits 0.
- [ ] E2E verification: trigger learn with a vague prevention and confirm recording is blocked; trigger with a
      specific prevention and confirm recording succeeds.
