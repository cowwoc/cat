# Plan: render-diff-precomputation

## Goal
Pre-compute rendered diff output for approval gates so the agent can display it directly without visible Bash tool invocations.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Handler must correctly detect base branch
- **Mitigation:** Multiple fallback strategies for base branch detection

## Files to Modify
- `plugin/hooks/skill_handlers/__init__.py` - Register new handler
- `plugin/hooks/skill_handlers/render_diff_handler.py` - New handler (created)
- `plugin/skills/render-diff/SKILL.md` - Document pre-computed output pattern

## Acceptance Criteria
- [ ] Handler registered in __init__.py
- [ ] Handler pre-computes diff on skill invocation
- [ ] SKILL.md documents PRE-COMPUTED RENDER-DIFF OUTPUT pattern
- [ ] Approval gates display clean output without Bash visibility

## Execution Steps
1. Create render_diff_handler.py with base branch detection
2. Register handler in __init__.py
3. Update SKILL.md with pre-computed output documentation
