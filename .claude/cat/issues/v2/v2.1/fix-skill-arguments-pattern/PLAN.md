# Plan: fix-skill-arguments-pattern

## Problem
Two skills use `${ARGUMENTS}` in preprocessor `!` backtick commands instead of the correct `argument-hint` + `$N`
positional reference pattern. This causes failures when the skills are invoked without arguments (0 args passed instead
of expected count) and is unsafe for shell expansion.

## Satisfies
None

## Reproduction Code
```
Skill tool:
  skill: "cat:stakeholder-review-box"
  # No args provided
```

## Expected vs Actual
- **Expected:** Clear error about missing arguments from argument-hint validation
- **Actual:** Shell error: "Expected 4 arguments but got 0" from the binary

## Root Cause
Two skills pass arguments via `${ARGUMENTS}` variable expansion in preprocessor commands instead of using the
`argument-hint` frontmatter + `$N` positional references pattern.

**Affected skills:**
1. `plugin/skills/stakeholder-review-box/SKILL.md` - uses `${ARGUMENTS}` instead of `"$0" "$1" "$2" "$3"`
2. `plugin/skills/extract-investigation-context/SKILL.md` - uses `$ARGUMENTS` instead of `$N` references

**Correct pattern** (from stakeholder-concern-box/SKILL.md):
```yaml
---
description: Internal - renders a stakeholder concern box during review
user-invocable: false
argument-hint: "<severity> <stakeholder> <description> <location>"
---
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-stakeholder-concern-box" "$0" "$1" "$2" "$3"`
```

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Skills could break if argument indices are wrong
- **Mitigation:** Compare with working skills (stakeholder-concern-box, stakeholder-selection-box)

## Files to Modify
- `plugin/skills/stakeholder-review-box/SKILL.md` - Replace `${ARGUMENTS}` with argument-hint + `"$0" "$1" "$2" "$3"`
- `plugin/skills/extract-investigation-context/SKILL.md` - Replace `$ARGUMENTS` with argument-hint + `$N` references.
  Note: this skill takes variable-length keyword arguments after a hardcoded session file path. Use
  `argument-hint: "<keywords...>"` and pass `"$0"` for the keywords string.

## Test Cases
- [ ] stakeholder-review-box invoked with 4 args produces expected box output
- [ ] extract-investigation-context invoked with keywords produces expected output
- [ ] Both skills have argument-hint in frontmatter
- [ ] Neither skill references ${ARGUMENTS} or $ARGUMENTS

## Execution Steps
1. **Step 1:** Update `plugin/skills/stakeholder-review-box/SKILL.md`
   - Add `argument-hint: "<issue> <reviewers> <result> <summary>"` to frontmatter
   - Replace `${ARGUMENTS}` with `"$0" "$1" "$2" "$3"` in the preprocessor command
   - Files: `plugin/skills/stakeholder-review-box/SKILL.md`

2. **Step 2:** Update `plugin/skills/extract-investigation-context/SKILL.md`
   - Add `argument-hint: "<keywords...>"` to frontmatter
   - Replace `$ARGUMENTS` with `"$0"` in the preprocessor command (keywords passed as single string)
   - Also fix the hardcoded `/workspace/` path to use `${CLAUDE_PLUGIN_ROOT}` or relative path
   - Files: `plugin/skills/extract-investigation-context/SKILL.md`

3. **Step 3:** Verify no other SKILL.md files use `${ARGUMENTS}` or `$ARGUMENTS` in preprocessor commands
   - Run grep across plugin/skills/*/SKILL.md
   - Confirm zero matches

4. **Step 4:** Run tests
   - Run `mvn -f client/pom.xml test`

## Post-conditions
- [ ] All test cases pass
- [ ] No regressions in related functionality
- [ ] Zero grep matches for ARGUMENTS in SKILL.md preprocessor commands
