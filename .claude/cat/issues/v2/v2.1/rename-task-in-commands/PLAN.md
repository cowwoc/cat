# Plan: rename-task-in-commands

## Parent Task
Decomposed from: 2.1-rename-task-to-issue
Sequence: 4 of 5

## Objective
Replace "task" terminology with "issue" in all command files.

## Scope
- Update all .md files in plugin/commands/
- Update variable names, comments, documentation
- Preserve functionality

## Dependencies
- rename-task-scripts (script filenames must be updated first)

## Files to Modify
- plugin/commands/cleanup.md
- plugin/commands/work.md
- plugin/commands/config.md
- plugin/commands/init.md
- plugin/commands/add.md
- plugin/commands/remove.md
- plugin/commands/research.md
- plugin/commands/status.md

## Execution Steps
1. Replace "task" with "issue" in all command files
2. Update references to old script names
3. Update any terminology in user-facing messages

## Acceptance Criteria
- [ ] All command files updated
- [ ] Script references point to new names
- [ ] No "task" terminology in command files
