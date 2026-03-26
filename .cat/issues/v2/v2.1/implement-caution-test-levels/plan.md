# Plan

## Goal

Implement the caution level test execution tiers so each level determines how much verification runs
before the approval gate. Currently caution maps to the old `verify` level (NONE/CHANGED/ALL) which
controlled file scope, not test depth. This issue redefines the behavior in terms of test depth:

## Caution Level Definitions

### low — compile only (fastest feedback)

Verification runs:
1. Pre/post condition checks (plan.md post-conditions evaluated against implementation)
2. Compilation: `mvn -f client/pom.xml compile` — confirms the code builds without errors

Unit tests and E2E tests are skipped. Suitable for simple refactors and documentation changes where
the user prioritizes speed over coverage confidence.

### medium — compile + unit tests (current default behavior)

Verification runs:
1. Pre/post condition checks
2. Compilation: `mvn -f client/pom.xml compile`
3. Unit tests: `mvn -f client/pom.xml test` — runs the full unit test suite

E2E tests are skipped. This is the current behavior and matches what most issues need.

### high — compile + unit tests + E2E tests (maximum confidence)

Verification runs:
1. Pre/post condition checks
2. Compilation: `mvn -f client/pom.xml compile`
3. Unit tests: `mvn -f client/pom.xml test`
4. E2E tests: invoke the built jlink artifacts against a real worktree scenario to confirm the change
   works end-to-end in its actual runtime context (not just unit-level)

The E2E invocation runs the same verification described in each issue's E2E post-condition. The verify
subagent is responsible for determining the appropriate E2E command from the issue's plan.md.

## Pre-conditions

(none)

## Post-conditions

- [ ] caution=low: verify phase runs pre/post condition checks and `mvn compile`; `mvn test` skipped
- [ ] caution=medium: verify phase runs pre/post condition checks, `mvn compile`, and `mvn test` (current behavior)
- [ ] caution=high: verify phase runs pre/post condition checks, `mvn compile`, `mvn test`, and E2E tests
- [ ] Compilation step (`mvn compile`) added as an explicit verify step (currently absent)
- [ ] E2E test execution is gated on caution=high
- [ ] Verify subagent reads caution level from effective config before deciding which steps to run
- [ ] Unit tests for caution level routing logic in the verify subagent/handler
- [ ] No regressions in existing caution=medium workflows
- [ ] E2E: run /cat:work at caution=low and confirm only compile runs; caution=medium confirm unit tests run; caution=high confirm E2E step executes
