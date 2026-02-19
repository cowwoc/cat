# Plan: apply-verbatim-echo-pattern-to-skills

## Problem

Commit e4060507 optimized `status-first-use/SKILL.md` instruction wording from vague "The user wants you to respond
with the contents of the latest tag verbatim" to explicit imperative "Echo the content inside the LATEST tag below. Do
not summarize, interpret, or add commentary." This achieved 100% compliance in empirical testing. Other skills still use
the old wording pattern.

## Satisfies

None (infrastructure improvement)

## Files to Modify

- `plugin/skills/cleanup-first-use/SKILL.md` line 63 - replace old wording with new imperative pattern
- `plugin/skills/run-retrospective-first-use/SKILL.md` line 23 - replace old wording with new imperative pattern
- `plugin/skills/skill-builder-first-use/SKILL.md` lines 1456, 1466 - update template/documentation to reflect new
  pattern

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — mechanical text replacement
- **Mitigation:** Verify wording matches the status skill pattern

## Acceptance Criteria

- [ ] All "The user wants you to respond with the contents of the latest" instances replaced
- [ ] New wording uses "Echo the content inside the LATEST `<output skill="X">` tag below. Do not summarize, interpret,
  or add commentary."
- [ ] skill-builder template updated to document the new pattern
- [ ] Tests pass

## Execution Steps

1. Update `cleanup-first-use/SKILL.md` line 63
2. Update `run-retrospective-first-use/SKILL.md` line 23
3. Update `skill-builder-first-use/SKILL.md` template and documentation (lines 1456, 1466)
4. Run tests
