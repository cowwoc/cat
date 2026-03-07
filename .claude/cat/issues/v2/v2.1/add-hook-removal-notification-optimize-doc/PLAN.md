# Plan: add-hook-removal-notification-optimize-doc

## Goal
After priming is eliminated in Step 2f of optimize-doc, check whether any hooks warn or block the
same undesired behavior. If matching hooks exist, temporarily disable them and re-run the empirical
test. If the behavior still does not occur without the hooks, remove them and notify the user. If
the behavior recurs without the hooks, restore them (they are still needed as a secondary guard).

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — additive change, no existing behavior altered
- **Mitigation:** Single file edit; existing workflow unchanged

## Files to Modify
- `plugin/skills/optimize-doc/first-use.md` - Add hook removal workflow to Step 2f

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- In Step 2f of `plugin/skills/optimize-doc/first-use.md`, after confirming priming eliminated
  (all tests report BEHAVIOR_NOT_OBSERVED), add a sub-step (2g) that:
  1. Searches both hook registration locations (`.claude/settings.json` and
     `plugin/hooks/hooks.json`) for hooks that guard the same forbidden behavior
  2. If matching hooks found: temporarily disable them (comment out or remove from JSON)
  3. Re-run the empirical test with hooks disabled
  4. If BEHAVIOR_NOT_OBSERVED: remove the hooks permanently and notify the user what was removed
  5. If BEHAVIOR_OBSERVED: restore the hooks silently (still needed as secondary guard)
  6. If no matching hooks found: continue silently
  - Files: `plugin/skills/optimize-doc/first-use.md`

## Post-conditions
- [ ] Step 2f/2g includes a hook search after priming elimination is confirmed
- [ ] The hook check searches both `.claude/settings.json` and `plugin/hooks/hooks.json`
- [ ] Matching hooks are empirically tested before removal (not blindly removed)
- [ ] If hooks pass the test (behavior gone without them), they are removed and user is notified
- [ ] If hooks fail the test (behavior recurs without them), they are restored silently
- [ ] If no matching hooks found, the workflow continues silently
- [ ] E2E: Read the updated skill and confirm the hook removal workflow is present and actionable
