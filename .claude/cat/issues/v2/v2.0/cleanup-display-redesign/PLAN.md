# Plan: cleanup-display-redesign

## Goal
Improve the visual output of `/cat:cleanup` command with box-drawing displays for survey results, cleanup plan
confirmation, and completion verification.

## Satisfies
- None (UX improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Emoji width calculations must be accurate; table column alignment
- **Mitigation:** Use existing display_width() pattern from status_handler.py

## Files to Modify
- plugin/hooks/skill_handlers/cleanup_handler.py - Create new handler for preprocessor output
- plugin/commands/cleanup.md - Update survey/plan/completion output to use preprocessor displays

## Acceptance Criteria
- [ ] Survey results shown in bordered table with type/artifact/status columns
- [ ] Cleanup plan shows bordered box with grouped removal items
- [ ] Completion shows verification counts in bordered box
- [ ] Output uses same box-drawing style as /cat:status
- [ ] Emoji alignment is correct (verified via hook preprocessor output)

## Execution Steps
1. **Create cleanup_handler.py**
   - Files: plugin/hooks/skill_handlers/cleanup_handler.py
   - Verify: Handler registered and returns preprocessor output for each phase

2. **Update cleanup.md output steps**
   - Files: plugin/commands/cleanup.md
   - Verify: Skill outputs preprocessor boxes without recalculation
