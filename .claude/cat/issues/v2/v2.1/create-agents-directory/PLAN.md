# Plan: create-agents-directory

## Current State
Plugin has stakeholders in `plugin/stakeholders/` and subagent-style skills scattered in `plugin/skills/`. 
No dedicated agents directory exists.

## Target State
Create `plugin/agents/` directory with proper structure following Claude Code subagent format 
(https://code.claude.com/docs/en/sub-agents).

## Satisfies
None - infrastructure/refactoring

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - additive change
- **Mitigation:** Existing files remain until subsequent issues move them

## Files to Modify
- plugin/agents/ - Create new directory
- plugin/agents/README.md - Document the agent format and structure

## Acceptance Criteria
- [ ] plugin/agents/ directory exists
- [ ] README.md documents the expected agent format (frontmatter fields, system prompt)
- [ ] Example agent file demonstrates the format

## Execution Steps
1. **Step 1:** Create plugin/agents/ directory
2. **Step 2:** Create README.md documenting Claude Code subagent format
3. **Step 3:** Update STATE.md to completed
