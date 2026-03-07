# Plan: add-hook-removal-notification-optimize-doc

## Goal
After priming is eliminated in Step 2f of optimize-doc, check whether any hooks warn or block the
same undesired behavior. If found, notify the user that these hooks can now be removed since the
priming source has been fixed.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — additive change, no existing behavior altered
- **Mitigation:** Single file edit; existing workflow unchanged

## Files to Modify
- `plugin/skills/optimize-doc/first-use.md` - Add hook check notification to Step 2f

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- In Step 2f of `plugin/skills/optimize-doc/first-use.md`, after confirming priming eliminated
  (all tests report BEHAVIOR_NOT_OBSERVED), add a step to grep hook files for patterns related
  to the forbidden behavior. If matching hooks exist, notify the user they can be removed now
  that the priming source is gone.
  - Files: `plugin/skills/optimize-doc/first-use.md`

## Post-conditions
- [ ] Step 2f includes a hook check after priming elimination is confirmed
- [ ] The hook check searches hook files for patterns related to the forbidden behavior
- [ ] If hooks are found, user is notified they can be removed
- [ ] If no hooks are found, the workflow continues silently
- [ ] E2E: Read the updated step 2f and confirm the hook check instruction is present and
      actionable
