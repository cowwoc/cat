# Plan: fix-phase17-migration-test

## Goal

Fix the failing test `phase17MigrationQualifiesBareNamesAndAllSubissuesClosedBehavesCorrectly` in
`IssueDiscoveryTest.java`. The test creates files in the old `STATE.md` Markdown format but the production code
now reads `index.json` (JSON format), so the test infrastructure needs to be updated to match.

## Approach

Update four areas in the test:

1. **`createDecomposedParentWithBareName`** — write `index.json` (JSON) instead of `STATE.md` (Markdown), and also
   write `plan.md` so `isCorrupt=false`.
2. **`applyPhase17Migration`** — modify `decomposedInto` JSON array entries instead of Markdown list entries.
3. **`parentStatePath`** variable — point to `index.json` instead of `STATE.md`.
4. **Content assertions** — check JSON format (`"2.1-sub-task"`) instead of Markdown format (`- 2.1-sub-task`).
5. **Sub-task state path** — the test writes a closed state to `STATE.md`; update to write `index.json`.

## Acceptance Criteria

- [ ] Test `phase17MigrationQualifiesBareNamesAndAllSubissuesClosedBehavesCorrectly` passes
- [ ] All other tests in `IssueDiscoveryTest` continue to pass
- [ ] All tests in the project pass (`mvn -f client/pom.xml test`)
