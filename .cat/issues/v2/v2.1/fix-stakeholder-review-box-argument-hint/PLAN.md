# Plan: fix-stakeholder-review-box-argument-hint

## Problem

The `stakeholder-review-box` SKILL.md has `argument-hint: "<issue> <reviewers> <result> <summary>"` which does not
specify that `<reviewers>` must be comma-separated `stakeholder:status` pairs. Agents calling the binary directly infer
space-separated format from the parameter label, causing `IllegalArgumentException` at runtime.

## Satisfies

None

## Root Cause

The format `stakeholder:status,...` is only documented in `stakeholder-review/first-use.md`'s report step, not at the
point of invocation (the SKILL.md). Agents reading SKILL.md alone have no way to know the required format.

## Expected vs Actual

- **Expected:** Agent reads SKILL.md and knows `<reviewers>` must be comma-separated `stakeholder:status` pairs
- **Actual:** Agent reads SKILL.md and infers space-separated format from parameter name

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — only changes documentation strings
- **Mitigation:** Verify binary still works after argument-hint update

## Files to Modify

- `plugin/skills/stakeholder-review-box/SKILL.md` - update argument-hint to `<issue> <stakeholder:status,...> <result>
  <summary>` with format example
- `plugin/skills/stakeholder-concern-box/SKILL.md` - check for similar format ambiguity; fix if needed

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Update `stakeholder-review-box/SKILL.md` argument-hint to clarify format:
  `argument-hint: "<issue> <stakeholder:status,...> <result> <summary>"`
  - Files: `plugin/skills/stakeholder-review-box/SKILL.md`
- Add a format example comment showing `requirements:APPROVED,architecture:CONCERNS`
  - Files: `plugin/skills/stakeholder-review-box/SKILL.md`
- Check `stakeholder-concern-box/SKILL.md` for similar argument-hint ambiguity; fix if needed
  - Files: `plugin/skills/stakeholder-concern-box/SKILL.md`

## Post-conditions

- [ ] `stakeholder-review-box/SKILL.md` argument-hint shows `<stakeholder:status,...>` format
- [ ] A format example is present so callers know the exact syntax
- [ ] `stakeholder-concern-box/SKILL.md` checked and fixed if ambiguous
