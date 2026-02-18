<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: Rename silent-execution-pattern.md to silent-execution.md

## Goal
Rename the concept doc to a shorter name and add a reference from the skill-builder skill.
Fold both changes into the existing implementation commit (826d991f).

## Satisfies
- None (cleanup)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** History rewrite on pushed v2.1 branch
- **Mitigation:** User explicitly requested; backup branch created by rebase tool

## Files to Modify
- `plugin/concepts/silent-execution-pattern.md` - Rename to `silent-execution.md`
- `plugin/skills/skill-builder-first-use/SKILL.md` - Add reference to `plugin/concepts/silent-execution.md`
- `.claude/cat/issues/v2/v2.1/fix-skill-output-framing/PLAN.md` - Update references

## Acceptance Criteria
- [ ] `plugin/concepts/silent-execution.md` exists
- [ ] `plugin/concepts/silent-execution-pattern.md` does not exist
- [ ] skill-builder references `plugin/concepts/silent-execution.md`
- [ ] fix-skill-output-framing PLAN.md references updated
- [ ] All changes folded into commit 826d991f

## Execution Steps
1. **Rename concept file:** `git mv plugin/concepts/silent-execution-pattern.md plugin/concepts/silent-execution.md`
2. **Add reference in skill-builder:** Insert reference to `plugin/concepts/silent-execution.md` in the
   Silent Preprocessing section (after line 1233)
3. **Update PLAN.md references:** Change `silent-execution-pattern.md` to `silent-execution.md` in
   fix-skill-output-framing PLAN.md
4. **Create fixup commit** targeting 826d991f
5. **Rebase with autosquash** to fold into the target commit
6. **Force push** v2.1

## Success Criteria
- [ ] Commit 826d991f (or its replacement) contains all changes
- [ ] No separate commit for the rename/reference
- [ ] v2.1 branch history is clean
