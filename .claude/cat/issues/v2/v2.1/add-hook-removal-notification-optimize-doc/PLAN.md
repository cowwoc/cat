# Plan: add-hook-removal-notification-optimize-doc

## Goal
After priming is eliminated in Step 2f of optimize-doc, check whether any hooks warn or block the
same undesired behavior. If found, notify the user that these hooks may no longer be necessary since
the priming source has been fixed. Distinguish between project hooks (`.claude/settings.json` —
user can remove) and plugin hooks (`plugin/hooks/hooks.json` — require a plugin change to remove).

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
  (all tests report BEHAVIOR_NOT_OBSERVED), add a step to search both hook registration
  locations (`.claude/settings.json` and `plugin/hooks/hooks.json`) for hooks that guard the
  same forbidden behavior. If matching hooks exist, notify the user distinguishing by source:
  - Project hooks: user can remove from `.claude/settings.json`
  - Plugin hooks: require a plugin change to remove from `plugin/hooks/hooks.json`
  - Files: `plugin/skills/optimize-doc/first-use.md`

## Post-conditions
- [ ] Step 2f includes a hook check after priming elimination is confirmed
- [ ] The hook check searches both `.claude/settings.json` and `plugin/hooks/hooks.json`
- [ ] If hooks are found, user is notified with source distinction (project vs plugin)
- [ ] If no hooks are found, the workflow continues silently
- [ ] E2E: Read the updated step 2f and confirm the hook check instruction is present and
      actionable
