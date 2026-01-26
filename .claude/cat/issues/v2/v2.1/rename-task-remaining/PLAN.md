# Plan: rename-task-remaining

## Parent Task
Decomposed from: 2.1-rename-task-to-issue
Sequence: 5 of 5

## Objective
Replace "task" terminology with "issue" in remaining files (templates, references, Python handlers, hooks).

## Scope
- Update template files
- Update reference documentation
- Update Python handlers in plugin/hooks/
- Update any remaining files with "task" terminology

## Dependencies
- rename-task-scripts
- rename-task-in-skills
- rename-task-in-concepts
- rename-task-in-commands

## Files to Modify
- plugin/templates/*.md (any with "task")
- plugin/hooks/skill_handlers/*.py
- plugin/hooks/bash_handlers/*.py
- Any other files discovered with grep

## Execution Steps
1. Update template files
2. Update Python handlers
3. Run comprehensive grep to find any remaining "task" references
4. Update any discovered files
5. Verify changelogs still contain historical "task" references (preserve)

## Acceptance Criteria
- [ ] All template files updated
- [ ] All Python handlers updated
- [ ] No "task" terminology anywhere except changelogs
- [ ] Changelogs preserve historical accuracy
