## Type

feature

## Goal

Add Bats tests for the WAVES_COUNT boundary detection in `plugin/skills/work-implement-agent/first-use.md` around line 242, where the wave count grep command boundary conditions are not tested. Tests must cover plans with zero waves, one wave, multiple waves, and edge cases like malformed wave headers.

## Pre-conditions

- `plugin/skills/work-implement-agent/first-use.md` contains WAVES_COUNT detection via grep at approximately line 242
- Bats test infrastructure is available in the project

## Post-conditions

- Bats tests verify WAVES_COUNT=0 when plan.md has no Sub-Agent Waves section
- Bats tests verify WAVES_COUNT=1 when plan.md has exactly one wave
- Bats tests verify WAVES_COUNT=N correctly for multi-wave plans
- Bats tests verify WAVES_COUNT detection handles edge cases (empty plan.md, malformed headers)
- Bats tests verify the relay prohibition: WAVES_COUNT must not be embedded into subagent prompt text
- All new Bats tests pass
- No regressions in existing work-implement-agent behavior
- E2E verification: confirm WAVES_COUNT is correctly determined and used for subagent orchestration
