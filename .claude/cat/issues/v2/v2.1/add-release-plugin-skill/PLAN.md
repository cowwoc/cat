# Plan: add-release-plugin-skill

## Current State
The release-plugin command exists at `.claude/commands/release-plugin/SKILL.md` as a command rather than a skill.

## Target State
Migrate the release-plugin definition to `.claude/skills/release-plugin/SKILL.md` as a proper skill, and delete the old command location.

## Satisfies
None - infrastructure reorganization

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Command path changes from `/release-plugin` command to `/release-plugin` skill
- **Mitigation:** Skills and commands are invoked the same way by users

## Files to Modify
- `.claude/skills/release-plugin/SKILL.md` - Create skill definition (migrated from command)
- `.claude/commands/release-plugin/SKILL.md` - Delete old command location

## Execution Steps
1. **Step 1:** Create `.claude/skills/release-plugin/SKILL.md` with the release workflow
   - Files: `.claude/skills/release-plugin/SKILL.md`
2. **Step 2:** Delete `.claude/commands/release-plugin/SKILL.md`
   - Files: `.claude/commands/release-plugin/SKILL.md`

## Success Criteria
- [ ] Skill file exists at `.claude/skills/release-plugin/SKILL.md`
- [ ] Old command at `.claude/commands/release-plugin/SKILL.md` is deleted
- [ ] Skill content matches or improves on the original command