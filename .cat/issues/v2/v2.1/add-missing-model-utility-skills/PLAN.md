# Plan: add-missing-model-utility-skills

## Current State
Two infrastructure utility skills — `plugin/skills/get-output-agent/SKILL.md` and
`plugin/skills/stakeholder-common/SKILL.md` — are missing the `model:` field in their
frontmatter. All comparable skills (e.g., `get-diff-agent`, `get-session-id-agent`,
`stakeholder-selection-box-agent`) already specify `model: haiku`.

## Target State
Both skills specify `model: haiku` in their frontmatter, consistent with the established
pattern for infrastructure utility skills that invoke Java CLI tools without LLM reasoning.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — model specification has no behavioral impact on skills that
  delegate entirely to compiled Java tools
- **Mitigation:** Verify frontmatter after change

## Files to Modify
- `plugin/skills/get-output-agent/SKILL.md` - add `model: haiku` to frontmatter
- `plugin/skills/stakeholder-common/SKILL.md` - add `model: haiku` to frontmatter

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Add `model: haiku` field to frontmatter of `plugin/skills/get-output-agent/SKILL.md`
  (after `user-invocable: false`, before `argument-hint`)
  - Files: `plugin/skills/get-output-agent/SKILL.md`
- Add `model: haiku` field to frontmatter of `plugin/skills/stakeholder-common/SKILL.md`
  (after `user-invocable: false`, before `argument-hint`)
  - Files: `plugin/skills/stakeholder-common/SKILL.md`

## Post-conditions
- [ ] `plugin/skills/get-output-agent/SKILL.md` frontmatter contains `model: haiku`
- [ ] `plugin/skills/stakeholder-common/SKILL.md` frontmatter contains `model: haiku`
- [ ] No other skills are missing a `model:` field
