# Plan: add-display-redesign

## Goal
Improve the visual output of `/cat:add` command with box-drawing displays and hook-based preprocessor output for reliable emoji alignment.

## Satisfies
- None (UX improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Emoji width calculations must be accurate
- **Mitigation:** Use existing display_width() pattern from status_handler.py

## Files to Modify
- plugin/hooks/skill_handlers/add_handler.py - Create new handler for preprocessor output
- plugin/commands/add.md - Update completion output to use preprocessor display

## Acceptance Criteria
- [ ] Task completion shows bordered box with success indicator
- [ ] Version completion shows bordered box with requirements summary
- [ ] Output uses same box-drawing style as /cat:status
- [ ] Emoji alignment is correct (verified via hook preprocessor output)
- [ ] Next steps shown in compact single-line format

## Execution Steps
1. **Create add_handler.py**
   - Files: plugin/hooks/skill_handlers/add_handler.py
   - Verify: Handler registered and returns preprocessor output

2. **Update add.md completion steps**
   - Files: plugin/commands/add.md
   - Verify: Skill outputs preprocessor box without recalculation
