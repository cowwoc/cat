# Plan: rename-task-scripts

## Parent Task
Decomposed from: 2.1-rename-task-to-issue
Sequence: 1 of 5

## Objective
Rename script files from "task" to "issue" terminology and update all internal references within those scripts.

## Scope
- Rename `find-task.sh` → `get-available-issues.sh`
- Rename `task-lock.sh` → `issue-lock.sh`
- Update internal variable names and comments in both scripts
- Update any other scripts that reference these files

## Dependencies
None (first in sequence)

## Files to Modify
- plugin/scripts/find-task.sh → plugin/scripts/get-available-issues.sh
- plugin/scripts/task-lock.sh → plugin/scripts/issue-lock.sh
- plugin/scripts/monitor-subagents.sh (if references task-lock.sh)
- plugin/scripts/merge-and-cleanup.sh (if references task-lock.sh)

## Execution Steps
1. Rename find-task.sh to get-available-issues.sh
2. Update internal references (variables, comments) in get-available-issues.sh
3. Rename task-lock.sh to issue-lock.sh
4. Update internal references in issue-lock.sh
5. Update any other scripts referencing old filenames

## Acceptance Criteria
- [ ] Scripts renamed successfully
- [ ] Internal variable names updated (TASK → ISSUE where appropriate)
- [ ] Scripts still function correctly
- [ ] No references to old script names in plugin/scripts/
