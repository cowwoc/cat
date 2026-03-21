# Plan

## Goal

Fix the priming defect in `plugin/skills/learn/first-use.md` Step 4c that causes agents to either:
1. Call `record-learning` directly with the full combined subagent JSON (nested structure) instead of the flat
   Phase 3 `.prevent` output, resulting in all fields silently defaulting to empty strings; or
2. Bypass the Phase 3 subagent entirely and invoke `record-learning` themselves when the subagent appears to fail.

Also correct the invalid M590 entry in `.cat/retrospectives/mistakes-2026-03.json` created by the original defect.

## Pre-conditions

(none)

## Post-conditions

- [ ] Step 4c explicitly states that `PHASE3_JSON` must be the `.prevent` key extracted from the subagent's combined
  JSON output, not the full combined JSON structure
- [ ] Step 4c includes a pre-check verifying that `category`, `description`, `root_cause`, and `prevention_type` are
  non-empty before invoking `record-learning`; if any are empty, the step fails with a clear error rather than
  silently recording empty fields
- [ ] Step 4c states that if the Phase 3 subagent returns an error or empty/invalid output, the correct action is to
  retry the subagent — not to call `record-learning` directly
- [ ] M590 entry in `.cat/retrospectives/mistakes-2026-03.json` is corrected with all fields non-empty
- [ ] E2E verification: invoke the learn workflow end-to-end and confirm the resulting mistakes JSON entry has all
  fields populated with non-empty values
- [ ] No regressions: existing learn workflow behavior is unchanged for successful Phase 3 subagent runs
