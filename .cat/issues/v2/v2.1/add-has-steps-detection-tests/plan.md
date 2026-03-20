## Type

feature

## Goal

Add Bats tests for the HAS_STEPS detection logic in `plugin/skills/work-implement-agent/first-use.md` to eliminate a fragility where the grep-based detection silently fails on section name variants or whitespace changes, causing plan generation to be skipped with no error. The tests must cover both valid section header variants, the absent-headers case that triggers plan-builder invocation, and edge cases.

## Pre-conditions

- `plugin/skills/work-implement-agent/first-use.md` contains the HAS_STEPS detection via `grep -qE '^## (Sub-Agent Waves|Execution Steps)'`
- Bats test infrastructure is available in the project

## Post-conditions

- Bats tests verify HAS_STEPS detection correctly identifies `## Sub-Agent Waves` header
- Bats tests verify HAS_STEPS detection correctly identifies `## Execution Steps` header
- Bats tests verify plan-builder invocation is triggered when neither header variant is present in plan.md
- Bats tests verify detection handles edge cases gracefully (whitespace variants, empty file)
- All new Bats tests pass
- No regressions in existing work-implement-agent behavior
- E2E verification: invoke work-implement-agent with a plan.md that has no execution steps and confirm plan-builder is called
