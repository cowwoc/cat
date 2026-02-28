# Plan: add-line-length-verification-reminder

## Goal
Add an active line-length verification check to phase-prevent.md so agents verify all added lines
comply with the 120-character limit after editing any Markdown or Java file.

## Satisfies
None - action item A023 from retrospective (PATTERN-019)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Check-step must be actionable, not vague
- **Mitigation:** Reference the exact limit (120 chars) and the source rule (common.md)

## Files to Modify
- `plugin/skills/learn/phase-prevent.md` - add a check-step in Step 4 (or the appropriate step for
  post-edit verification) instructing agents to count characters in each added line after editing any
  Markdown or Java file and wrap any line exceeding 120 characters

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Read phase-prevent.md:** Identify Step 4 (post-edit verification) and the current list of checks
   - Files: `plugin/skills/learn/phase-prevent.md`
2. **Add line-length check-step:** Insert an explicit check: after editing any Markdown or Java file,
   verify all added lines comply with the 120-character limit; wrap any that exceed it
   - Files: `plugin/skills/learn/phase-prevent.md`
3. **Run tests:** Execute `mvn -f client/pom.xml verify` to verify no regressions
   - Files: None (validation step)
4. **Commit:** Commit the changes
   - Files: `plugin/skills/learn/phase-prevent.md`

## Post-conditions
- [ ] `phase-prevent.md` contains a check-step requiring line-length verification after Markdown/Java edits
- [ ] Check-step specifies the 120-character limit
- [ ] Check-step instructs wrapping any lines that exceed the limit
- [ ] All tests pass (`mvn -f client/pom.xml verify` exits 0)
