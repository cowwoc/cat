# Plan

## Goal

Update `plugin/skills/learn/phase-prevent.md` to explicitly prohibit saving prevention to auto-memory
(MEMORY.md). Prevention must be committed to project or plugin files, not persisted only in the
session-local auto-memory system.

## Post-conditions

- [ ] `phase-prevent.md` contains an explicit rule stating that saving prevention to MEMORY.md
  does not satisfy the prevention requirement
- [ ] The rule names the acceptable target locations: `plugin/rules/`, `plugin/skills/`,
  `plugin/concepts/`, `.claude/rules/` (per the audience of the rule)
- [ ] The rule is placed before the prevention level selection step so agents see it before choosing
  a prevention approach
- [ ] No regressions: existing prevention hierarchy and level guidance is unchanged
- [ ] Build passes: `mvn -f client/pom.xml verify -e` exits 0
