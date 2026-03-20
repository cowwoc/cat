## Type

feature

## Goal

Add Bats tests for the plan-builder-agent invocation logic in `plugin/skills/work-implement-agent/first-use.md` around line 176, where the conditional invocation of plan-builder is not tested. Tests must verify plan-builder is called with correct arguments when HAS_STEPS is false, and is NOT called when HAS_STEPS is true.

## Pre-conditions

- `plugin/skills/work-implement-agent/first-use.md` contains plan-builder-agent invocation conditional on HAS_STEPS at approximately line 176
- Bats test infrastructure is available in the project

## Post-conditions

- Bats tests verify plan-builder-agent is invoked when plan.md has no execution steps section
- Bats tests verify plan-builder-agent is NOT invoked when plan.md already has an execution steps section
- Bats tests verify plan-builder-agent is called with correct catAgentId, effort, mode=revise, and issue path arguments
- Bats tests verify the plan.md is updated after plan-builder-agent completes
- All new Bats tests pass
- No regressions in existing work-implement-agent behavior
- E2E verification: run work-implement-agent with a lightweight plan.md and confirm plan-builder is invoked and steps are generated
