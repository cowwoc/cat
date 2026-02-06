# Plan: rename-task-to-issue

## Current State
The codebase uses "task" terminology throughout skill files, templates, references, and documentation.

## Target State
All references to "task" are renamed to "issue" for consistency with common issue tracker terminology, except in
changelogs which preserve historical accuracy.

## Satisfies
None - terminology standardization

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** User-facing terminology changes; existing planning structures use "task" in directory names
- **Mitigation:** Comprehensive search/replace with manual review; preserve changelog references

## Files to Modify
- All skill .md files in plugin/skills/
- Template files in .claude/cat/templates/
- Reference documentation in .claude/cat/references/
- Any Python/shell scripts referencing "task"

## Execution Steps
1. **Step 1:** Inventory all files containing "task" terminology
   - Files: plugin/, .claude/cat/
   - Verify: grep -r "task" shows complete list

2. **Step 2:** Replace "task" with "issue" in skill files
   - Files: plugin/skills/*.md
   - Verify: grep -r "task" plugin/skills/ returns no matches

3. **Step 3:** Replace in templates and references
   - Files: .claude/cat/templates/, .claude/cat/references/
   - Verify: grep confirms replacement

4. **Step 4:** Update and rename script files
   - Rename: `find-task.sh` → `get-available-issues.sh`
   - Rename: `task-lock.sh` → `issue-lock.sh`
   - Files: *.py, *.sh with task references
   - Update all references to renamed scripts in skills/commands
   - Verify: Scripts still function correctly

5. **Step 5:** Verify changelogs preserved
   - Verify: Changelogs still contain historical "task" references

## Acceptance Criteria
- [ ] No mention of "task" in any file except changelogs
- [ ] All "issue" terminology is grammatically correct
- [ ] Scripts renamed: find-task.sh → get-available-issues.sh, task-lock.sh → issue-lock.sh
- [ ] All references to old script names updated
- [ ] Existing functionality unchanged
