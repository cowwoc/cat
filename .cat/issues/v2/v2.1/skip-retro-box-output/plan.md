# Plan: skip-retro-box-output

## Current State
`plugin/skills/retrospective-agent/first-use.md` routing table unconditionally lists "Output verbatim"
as an action for the Analysis data path, causing a large duplicate box to be shown before the Step 9
summary which already covers all the same information.

## Target State
For the analysis data path only, skip the verbatim box output. The routing table will direct the agent
directly to steps 5-9. Status message and error message paths remain unchanged — they still output
verbatim and stop.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — the information content is preserved in Step 9; only the duplicate
  verbatim box is removed
- **Mitigation:** Verify routing table and CRITICAL note are consistent after change

## Files to Modify
- `plugin/skills/retrospective-agent/first-use.md` — routing table line 29: remove "Output verbatim,
  then" from the Analysis data action

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- In `plugin/skills/retrospective-agent/first-use.md`, line 29: change the Analysis data routing
  table row from:
  `| Analysis data | **MANDATORY:** Output verbatim, then continue with workflow steps 5-9. Do NOT skip post-handler steps. |`
  to:
  `| Analysis data | **MANDATORY:** Continue with workflow steps 5-9. Do NOT skip post-handler steps. |`
- Verify the CRITICAL note (line 33) does not say "Output verbatim" for the analysis data path
  (it currently does not — confirm no change needed)
- Update `.cat/issues/v2/v2.1/skip-retro-box-output/index.json` in the same commit: set `status` to `closed`
- Commit with message: `bugfix: skip verbatim box output for analysis data path in retrospective-agent`

### Job 2 (fix: missing progress field)
- Update `.cat/issues/v2/v2.1/skip-retro-box-output/index.json`: add `"progress": 100` field (currently absent)
- Commit with message: `planning: add progress field to index.json`

## Post-conditions
- [ ] `plugin/skills/retrospective-agent/first-use.md` routing table updated: analysis data row no
  longer says "Output verbatim"
- [ ] Status message and error message rows unchanged
- [ ] CRITICAL note does not reference verbatim output for analysis data
- [ ] index.json status is `closed`
