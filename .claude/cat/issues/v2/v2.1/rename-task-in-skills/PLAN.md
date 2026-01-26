# Plan: rename-task-in-skills

## Parent Task
Decomposed from: 2.1-rename-task-to-issue
Sequence: 2 of 5

## Objective
Replace "task" terminology with "issue" in all skill files.

## Scope
- Update all SKILL.md files in plugin/skills/
- Update variable names, comments, documentation
- Preserve functionality

## Dependencies
- rename-task-scripts (script filenames must be updated first)

## Files to Modify
- plugin/skills/spawn-subagent/SKILL.md
- plugin/skills/stakeholder-review/SKILL.md
- plugin/skills/token-report/SKILL.md
- plugin/skills/git-commit/SKILL.md
- plugin/skills/git-merge-linear/SKILL.md
- plugin/skills/collect-results/SKILL.md
- plugin/skills/learn-from-mistakes/SKILL.md
- plugin/skills/safe-remove-code/SKILL.md
- plugin/skills/decompose-task/SKILL.md (rename to decompose-issue)
- plugin/skills/parallel-execute/SKILL.md
- plugin/skills/command-optimizer/SKILL.md
- plugin/skills/monitor-subagents/SKILL.md
- plugin/skills/merge-subagent/SKILL.md
- plugin/skills/validate-git-safety/SKILL.md
- plugin/skills/git-squash/SKILL.md

## Execution Steps
1. Replace "task" with "issue" in all skill files
2. Update references to old script names (find-task.sh → get-available-issues.sh, etc.)
3. Update directory name: decompose-task → decompose-issue
4. Verify no "task" references remain (except changelogs)

## Acceptance Criteria
- [ ] All skill files updated
- [ ] decompose-task directory renamed to decompose-issue
- [ ] Script references point to new names
- [ ] No "task" terminology in skill files
