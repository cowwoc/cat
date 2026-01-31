# Plan: add-opus-model-guidance

## Goal

Add comprehensive Opus model guidance to the delegate skill documentation, covering when Opus
subagents are appropriate (rare cases) and when needing Opus signals work shouldn't be delegated.

## Satisfies

None - infrastructure/documentation improvement

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None significant - documentation-only change
- **Mitigation:** Review for consistency with existing model selection guidance

## Files to Modify

- `plugin/skills/delegate/SKILL.md` - Add Opus to model selection table and guidance
- `plugin/concepts/subagent-delegation.md` - Add notes on validation task model selection (optional)

## Acceptance Criteria

- [ ] Behavior unchanged - existing model selection guidance preserved
- [ ] All tests still pass
- [ ] Code quality improved - model selection table now complete (haiku/sonnet/opus)
- [ ] Technical debt reduced - addresses gap in model selection documentation

## Execution Steps

1. **Update model selection table in delegate/SKILL.md**
   - Add Opus row for complex planning/analysis cases
   - Files: `plugin/skills/delegate/SKILL.md`
   - Verify: Table includes all three models with clear guidance

2. **Add "When to use Opus" section**
   - Document rare cases: two-stage planning, security analysis, critical validation gates
   - Include "signal to reconsider delegation" guidance
   - Files: `plugin/skills/delegate/SKILL.md`
   - Verify: Section clearly explains Opus is exceptional, not default

3. **Add validation task guidance**
   - Document asymmetric failure costs for validation gates
   - Note that critical validation (like compare-docs on important docs) may warrant Opus
   - Files: `plugin/skills/delegate/SKILL.md`
   - Verify: Guidance helps users make informed model choices for validation tasks

4. **Run tests**
   - Command: `python3 /workspace/run_tests.py`
   - Verify: All tests pass

5. **Commit changes**
   - Commit message: `config: add Opus model guidance to delegate skill`
