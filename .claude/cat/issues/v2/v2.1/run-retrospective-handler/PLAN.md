# Plan: run-retrospective-handler

## Goal

Create a Python handler for the run-retrospective skill to precompute analysis output formatting, hiding Bash tool
invocations from users.

## Background

The run-retrospective skill performs pattern analysis on accumulated mistakes, evaluates action item effectiveness, and
derives new action items. The skill currently contains multiple bash snippets for gathering and analyzing data. Per
skill-builder methodology (M215), a handler should precompute all formatted outputs.

## Approach

1. Create `plugin/hooks/skill_handlers/run_retrospective_handler.py`
2. Implement retrospective data gathering:
   - Read index.json for config/state
   - Read all mistakes from split files
   - Check trigger conditions
3. Pre-compute formatted output sections:
   - Category breakdown
   - Action item effectiveness
   - Pattern identification
   - Recommendations
4. Return via `additionalContext`
5. Update skill to use OUTPUT TEMPLATE with fail-fast

## Deliverables

- [ ] `plugin/hooks/skill_handlers/run_retrospective_handler.py`
- [ ] Register handler in `plugin/hooks/skill_handlers/__init__.py`
- [ ] Update `plugin/skills/run-retrospective/SKILL.md` to use OUTPUT TEMPLATE pattern
- [ ] Tests for handler

## Acceptance Criteria

- Skill invocation shows no Bash tool calls for data gathering
- Handler produces correctly formatted analysis output
- Fail-fast if handler output missing
- All tests pass
