# Plan: skip-retro-box-output

## Current State
`plugin/skills/retrospective-agent/first-use.md` Step 2 unconditionally outputs the
pre-rendered `<output type="retrospective">` box verbatim before proceeding to Steps 5-9
(the analysis data path). This results in a large duplicate box being shown to the user
before the Step 9 summary, which already covers all the same information.

## Target State
For the analysis data path only, skip Step 2 (verbatim box output). Execute Steps 5-9
and present only the Step 9 summary to the user. The status message and error message
paths are unchanged — they still output verbatim and stop.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — the information content is preserved in Step 9; only the
  duplicate verbatim box is removed
- **Mitigation:** E2E test by running a retrospective and verifying output

## Files to Modify
- `plugin/skills/retrospective-agent/first-use.md` — remove "Output verbatim" from the
  analysis data path in the routing table; update Step 2 to clarify it only applies to
  status/error paths

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Modify `plugin/skills/retrospective-agent/first-use.md`:
  - In the routing table (after "The result is one of three types"), change the Analysis
    data action from "Output verbatim, then continue with workflow steps 5-9" to just
    "Continue with workflow steps 5-9"
  - Update Step 2 ("Output verbatim") to clarify it only applies when the content is a
    status message or error message, not analysis data
  - Update Step 3 to reflect that the agent stops only after step 2 (status/error paths),
    not after analysis data

## Post-conditions
- [ ] `plugin/skills/retrospective-agent/first-use.md` routing table updated: analysis
  data row no longer says "Output verbatim"
- [ ] Steps 1-3 updated: Step 2 scoped to status/error paths only
- [ ] No change to status message or error message handling
- [ ] E2E: Running a retrospective produces only the Step 9 summary output with no
  preceding verbatim box
