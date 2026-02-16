# Plan: fix-empirical-test-skill-registration

## Problem
The `empirical-test` skill has `metadata.json` but is missing `SKILL.md`. Claude Code's plugin system discovers skills
via `SKILL.md` files with YAML front-matter, not `metadata.json`. The skill doesn't appear in the registered skills
list and cannot be invoked via `/cat:empirical-test`.

## Satisfies
None (bugfix for add-empirical-compliance-testing-skill)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — adding a missing registration file
- **Mitigation:** Verify skill loads after adding SKILL.md

## Files to Modify
- `plugin/skills/empirical-test/SKILL.md` - Create with YAML front-matter and load-skill.sh invocation

## Acceptance Criteria
- [ ] `plugin/skills/empirical-test/SKILL.md` exists with correct YAML front-matter
- [ ] SKILL.md invokes load-skill.sh like other skills
- [ ] `metadata.json` removed (redundant with SKILL.md)

## Execution Steps
1. **Step 1:** Create `plugin/skills/empirical-test/SKILL.md` with description and load-skill.sh invocation
   - Files: `plugin/skills/empirical-test/SKILL.md`
2. **Step 2:** Remove `plugin/skills/empirical-test/metadata.json` (redundant)
   - Files: `plugin/skills/empirical-test/metadata.json`

## Success Criteria
- [ ] Skill registers in Claude Code's skill list after plugin cache update
- [ ] SKILL.md follows same pattern as other skills (e.g., learn/SKILL.md)
