# Plan: rename-task-in-concepts

## Parent Task
Decomposed from: 2.1-rename-task-to-issue
Sequence: 3 of 5

## Objective
Replace "task" terminology with "issue" in all concept documentation files.

## Scope
- Update all .md files in plugin/concepts/
- Rename files with "task" in name (task-resolution.md, duplicate-task.md)
- Update variable names, comments, documentation
- Preserve functionality

## Dependencies
- rename-task-scripts (script filenames must be updated first)

## Files to Modify
- plugin/concepts/merge-and-cleanup.md
- plugin/concepts/version-completion.md
- plugin/concepts/work.md
- plugin/concepts/version-paths.md
- plugin/concepts/tdd.md
- plugin/concepts/workflow-output.md
- plugin/concepts/subagent-delegation.md
- plugin/concepts/agent-architecture.md
- plugin/concepts/hierarchy.md
- plugin/concepts/commit-types.md
- plugin/concepts/error-handling.md
- plugin/concepts/task-resolution.md → issue-resolution.md
- plugin/concepts/duplicate-task.md → duplicate-issue.md
- plugin/concepts/token-warning.md

## Execution Steps
1. Rename task-resolution.md to issue-resolution.md
2. Rename duplicate-task.md to duplicate-issue.md
3. Replace "task" with "issue" in all concept files
4. Update references to old script names
5. Update references to old file names

## Acceptance Criteria
- [ ] All concept files updated
- [ ] Files renamed appropriately
- [ ] Script references point to new names
- [ ] No "task" terminology in concept files
