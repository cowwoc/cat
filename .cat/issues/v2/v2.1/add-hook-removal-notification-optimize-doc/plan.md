# Plan: add-hook-removal-notification-optimize-doc

## Goal
After priming is eliminated in Step 2f of optimize-doc, check whether any hooks guard the same
forbidden behavior. Monitor hook activity during the existing Step 2f empirical test rather than
running a separate test. If hooks exist but did not fire during the test, they are redundant —
remove them and notify the user. If hooks fired, keep them as secondary guards.

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

## Sub-Agent Waves

### Wave 1
- In Step 2f of `plugin/skills/optimize-doc/first-use.md`, fold hook monitoring into the existing
  empirical test (no separate Step 2g):
  1. Before the test, search both hook registration locations (`.claude/settings.json` and
     `plugin/hooks/hooks.json`) for hooks that guard the same forbidden behavior
  2. If matching hooks found, add hook-monitoring instructions to the empirical test subagent
     prompt (report whether any hook warnings/blocks appeared as `hooks_fired: true|false`)
  3. After BEHAVIOR_NOT_OBSERVED + no hooks fired: remove hooks permanently, notify user
  4. After BEHAVIOR_NOT_OBSERVED + hooks fired: keep hooks (still needed)
  5. If no matching hooks found: continue silently
  - Files: `plugin/skills/optimize-doc/first-use.md`

## Post-conditions
- [ ] Step 2f includes a pre-test hook search before the empirical test
- [ ] The hook search checks both `.claude/settings.json` and `plugin/hooks/hooks.json`
- [ ] Hook monitoring is added to the empirical test subagent prompt (hooks_fired field)
- [ ] If hooks exist but did not fire during the test, they are removed and user is notified
- [ ] If hooks fired during the test, they are kept silently
- [ ] If no matching hooks found, the workflow continues silently
- [ ] No separate Step 2g exists — hook logic is integrated into Step 2f
- [ ] E2E: Read the updated skill and confirm the hook monitoring workflow is present and actionable
