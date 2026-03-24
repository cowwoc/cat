# Plan

## Goal

Fix GetAddOutput handler to read version status from index.json instead of STATE.md — readVersionData() still looks for STATE.md which was replaced by index.json in a prior migration, causing all versions to appear closed and the add-agent to return an empty version list.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: GetAddOutput.readVersionData() reads index.json for status, not STATE.md
- [ ] Regression test added: test covers readVersionData() with index.json-based version directories
- [ ] No new issues introduced
- [ ] E2E verification: running the get-add-output CLI returns a non-empty versions array when non-closed versions exist
- [ ] All existing tests in GetAddOutputPlanningDataTest are updated to use index.json format (not STATE.md)
- [ ] Missing index.json behavior is defined and tested (e.g., version treated as closed when index.json is absent)
- [ ] readVersionData() reads plan.md (lowercase) for goal summary, not PLAN.md
- [ ] parseStatus() method is removed or replaced with JSON parsing of index.json
