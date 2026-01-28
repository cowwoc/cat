# Plan: migrate-status-displays

## Goal
Migrate status command displays to use silent `!`command`` preprocessing.

## Satisfies
- Parent: migrate-to-silent-preprocessing

## Files to Modify
- plugin/hooks/skill_handlers/status_handler.py - Analyze what OUTPUT TEMPLATE provides
- plugin/commands/status.md - Update to use new mechanism
- New: .claude/commands/cat-status-box/ - Create skill with preprocessing

## Acceptance Criteria
- [ ] Status boxes generated via silent preprocessing
- [ ] status_handler.py simplified or removed
- [ ] No manual box construction in status display
