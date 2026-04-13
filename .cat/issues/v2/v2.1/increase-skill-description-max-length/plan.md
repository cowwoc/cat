# Plan: increase-skill-description-max-length

## Goal

Update the maximum skill description length from 250 to 1536 characters. Claude Code 2.1.105 raised
its listing cap from 250 to 1,536 characters (see
https://github.com/anthropics/claude-code/blob/main/CHANGELOG.md#21105), so our validation limit
should match.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Test boundary values hardcode 250; any test asserting exact error messages referencing "250" will fail
- **Mitigation:** Update constant in one place per class; grep for all "250" references in tests before changing

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/SkillFrontmatter.java` — change `MAX_DESCRIPTION_LENGTH = 250` to `1536`
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/UpdateSkillDescription.java` — change `MAX_DESCRIPTION_LENGTH = 250` to `1536`; update error messages that mention "250"
- `plugin/skills/instruction-builder-agent/first-use.md` — update all wizard references from `250` to `1536`
- `client/src/test/java/io/github/cowwoc/cat/client/test/SkillValidatorTest.java` — update boundary values (251-char reject → 1537-char reject, 250-char accept → 1536-char accept)
- `client/src/test/java/io/github/cowwoc/cat/client/test/UpdateSkillDescriptionTest.java` — same boundary updates

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Update the constant and error messages

- Change `MAX_DESCRIPTION_LENGTH` from `250` to `1536` in `SkillFrontmatter.java`
- Change `MAX_DESCRIPTION_LENGTH` from `250` to `1536` in `UpdateSkillDescription.java`
  - Update all error message strings that reference the literal "250"

### Job 2: Update the instruction-builder wizard

- In `plugin/skills/instruction-builder-agent/first-use.md`, replace every occurrence of `250` that refers to
  the description character limit with `1536`

### Job 3: Update tests

- In `SkillValidatorTest.java`, update boundary test inputs and expected messages from 250/251 to 1536/1537
- In `UpdateSkillDescriptionTest.java`, update boundary test inputs and expected messages from 250/251 to 1536/1537
- Run `mvn -f client/pom.xml verify -e` and confirm all tests pass

## Post-conditions

- [ ] `SkillFrontmatter.MAX_DESCRIPTION_LENGTH` equals `1536`
- [ ] `UpdateSkillDescription.MAX_DESCRIPTION_LENGTH` equals `1536`
- [ ] All error messages reference `1536`, not `250`
- [ ] All tests pass (`mvn -f client/pom.xml verify -e` exits 0)
- [ ] E2E: invoke the description validator with a 1536-character description and confirm it is accepted; invoke with a 1537-character description and confirm it is rejected with the correct limit in the error message
