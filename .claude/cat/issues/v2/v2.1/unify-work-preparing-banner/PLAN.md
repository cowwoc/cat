# Plan: unify-work-preparing-banner

## Problem

The `/cat:work` skill Phase 1 displays a simple `🔵 Preparing: v2.1-issue-name` banner, while `/cat:work-with-issue`
displays the full phase progression banner (`● Preparing ——— ○ Implementing ——— ○ Confirming ——— ○ Reviewing ———
○ Merging`). The two styles are visually inconsistent — the preparing phase should use the same boxed progress banner
as all other phases.

## Goal

Replace the simple "Preparing:" banner in `/cat:work` Phase 1 with the full phase progression banner (without issue
ID), using the existing `ProgressBanner.generateGenericPreparingBanner()` method.

## Satisfies

None (UX polish)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The `/cat:work` skill text references "SCRIPT OUTPUT PROGRESS BANNERS" which is injected by
  preprocessing — need to ensure the progress-banner call works without an issue ID
- **Mitigation:** `generateGenericPreparingBanner()` already handles empty issue ID; just need to wire the call

## Files to Modify

- `plugin/skills/work/first-use.md` — Replace "Progress Display" section to call `progress-banner --phase preparing`
  (no issue ID) instead of relying on the simple preparing banner

## Acceptance Criteria

- [ ] `/cat:work` Phase 1 displays the boxed phase progression banner with `◉ Preparing` highlighted
- [ ] Banner shows no issue ID (issue ID is unknown at Phase 1)
- [ ] Banner matches the visual style of the implementing/confirming/reviewing/merging banners
- [ ] `/cat:work-with-issue` still shows its own preparing banner with the issue ID once known

## Execution Steps

1. **Update work skill Progress Display section:** Replace the simple banner reference with a Bash command to call
   `"${CLAUDE_PLUGIN_ROOT}/hooks/bin/progress-banner" --phase preparing` (no issue ID argument)
   - Files: `plugin/skills/work/first-use.md`
