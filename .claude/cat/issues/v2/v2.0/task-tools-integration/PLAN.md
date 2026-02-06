# Plan: task-tools-integration

## Current State
CAT uses TodoWrite tool with PreCompact/SessionStart hooks to backup and restore task state across context compaction.
This requires:
- `save-todowrite.sh` PreCompact hook
- `restore-todowrite.sh` SessionStart hook
- Backup files in `.claude/backups/todowrite/`

## Target State
Replace TodoWrite backup/restore mechanism with Claude Code's native Task tools (TaskCreate, TaskUpdate, TaskList,
TaskGet). These persist natively across context compaction without hooks.

## Satisfies
None (infrastructure/internal improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - Task tools provide same visibility as TodoWrite
- **Mitigation:** Test task persistence across compaction before removing hooks

## Files to Modify
- `plugin/hooks/hooks.json` - Remove PreCompact and SessionStart TodoWrite hooks
- `plugin/hooks/save-todowrite.sh` - Delete
- `plugin/hooks/restore-todowrite.sh` - Delete
- Skills that track progress - Add TaskCreate/TaskUpdate calls

## Acceptance Criteria
- [ ] TodoWrite backup hooks removed from hooks.json
- [ ] save-todowrite.sh and restore-todowrite.sh deleted
- [ ] Skills create/update Task tool entries for progress visibility
- [ ] Tasks survive context compaction without backup hooks

## Execution Steps
1. **Remove TodoWrite hooks**
   - Files: `plugin/hooks/hooks.json`
   - Remove PreCompact and SessionStart entries for TodoWrite backup/restore
   - Verify: Hook no longer fires on compaction

2. **Delete backup scripts**
   - Files: `plugin/hooks/save-todowrite.sh`, `plugin/hooks/restore-todowrite.sh`
   - Delete both files
   - Verify: No orphaned references

3. **Update skills to use Task tools**
   - Identify skills that should track progress
   - Add TaskCreate when starting work
   - Add TaskUpdate when completing work
   - Verify: Tasks visible in Claude Code UI
