# Plan: deduplicate-embedded-content

## Current State
OUTPUT TEMPLATE boxes and other content appear in multiple places:
- System reminders (preloaded by hooks)
- Command definition text (e.g., work.md)
- Skill files

This duplication wastes ~5K tokens per session.

## Target State
Single source of truth for templates, loaded on-demand from reference files.

## Satisfies
None - infrastructure/optimization task

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Hook and command file restructuring
- **Mitigation:** Verify templates still render correctly after refactor

## Files to Modify
- plugin/commands/work.md - Remove embedded templates, add reference
- plugin/hooks/skill_handlers/work_handler.py - Load templates from file
- plugin/templates/output-boxes.md (new) - Single source for box templates

## Acceptance Criteria
- [ ] Templates stored in single reference file
- [ ] Commands/skills load templates on-demand
- [ ] No duplicate template content
- [ ] All templates render correctly

## Execution Steps
1. **Step 1:** Create plugin/templates/output-boxes.md with all box templates
   - Verify: File exists with all templates

2. **Step 2:** Update work_handler.py to load templates from file
   - Verify: Handler loads templates correctly

3. **Step 3:** Remove embedded templates from work.md
   - Verify: grep for box characters shows only references
