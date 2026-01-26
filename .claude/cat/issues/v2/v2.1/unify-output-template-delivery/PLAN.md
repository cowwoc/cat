# Plan: unify-output-template-delivery

## Goal
Update all skills/commands that use output templates to work consistently whether invoked by the user directly (via /cat:*) or by the agent via the Skill tool, using the PostToolUse hook approach.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Need to update multiple handler files and ensure backwards compatibility
- **Mitigation:** Test each skill both ways (user invocation and agent invocation)

## Files to Modify
- `plugin/hooks/skill_handlers/*.py` - Verify all handlers work with PostToolUse
- `plugin/hooks/posttool_handlers/skill_precompute.py` - May need to handle more skills
- `plugin/commands/*.md` - Update documentation if needed

## Acceptance Criteria
- [ ] Functionality works as described - output templates delivered for both invocation methods
- [ ] Tests written and passing - test coverage for PostToolUse handler
- [ ] No regressions - existing UserPromptSubmit path still works

## Execution Steps
1. **Audit skill handlers:** Identify all handlers that generate output templates
   - Files: `plugin/hooks/skill_handlers/`
   - Verify: List handlers with `handle()` methods returning content

2. **Update PostToolUse handler:** Ensure skill_precompute.py handles all relevant skills
   - Files: `plugin/hooks/posttool_handlers/skill_precompute.py`
   - Verify: Run tests

3. **Test both invocation paths:** For each skill with output templates
   - Verify: User invocation via `/cat:*` works
   - Verify: Agent invocation via `Skill(skill="cat:*")` works

4. **Update documentation:** Note the dual-path support in relevant skill docs
   - Files: `plugin/commands/*.md` as needed
   - Verify: Documentation reflects the change
