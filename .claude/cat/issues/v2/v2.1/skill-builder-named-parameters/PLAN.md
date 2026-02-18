# Plan: skill-builder-named-parameters

## Current State
Skills that need arguments use `${ARGUMENTS}` in preprocessor directives, but `${ARGUMENTS}` is not resolved by
SkillLoader. It gets treated as an unset shell variable and expands to empty string, breaking CLI invocations.
The M358 fix over-engineered a workaround by routing through load-skill.sh instead of fixing the root cause.

## Target State
Skills declare named parameters in YAML frontmatter. SkillLoader parses the `parameters:` field, maps positional
args to parameter names, and resolves `${PARAM_NAME}` variables in skill content. Skills reference parameters by
name (e.g., `${SELECTED_COUNT}`) instead of raw `${ARGUMENTS}`.

## Satisfies
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Skills using `${ARGUMENTS}` must migrate to named parameters
- **Mitigation:** Only 2 skills use `${ARGUMENTS}` (stakeholder-selection-box, stakeholder-concern-box)

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` - Add named parameter resolution
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` - Add tests for named params
- `plugin/scripts/load-skill.sh` - Pass skill args to SkillLoader
- `plugin/skills/stakeholder-selection-box/SKILL.md` - Use named parameters
- `plugin/skills/stakeholder-concern-box/SKILL.md` - Use named parameters
- `plugin/skills/skill-builder/SKILL.md` - Document named parameter pattern

## Acceptance Criteria
- [ ] SkillLoader resolves named parameters from frontmatter `parameters:` field
- [ ] stakeholder-selection-box uses named params (selected_count, total_count, running, skipped)
- [ ] stakeholder-concern-box uses named params (severity, stakeholder, description, location)
- [ ] Tests verify named parameter substitution
- [ ] skill-builder documentation updated to recommend named parameters
- [ ] M358 over-engineered changes reverted (no load-skill.sh routing for these skills)
- [ ] All existing tests pass

## Execution Steps
1. **Add named parameter support to SkillLoader.java:**
   - Parse `parameters:` YAML frontmatter field (list of parameter names)
   - Accept args string from Skill tool invocation
   - Split args positionally and map to parameter names
   - Resolve `${PARAM_NAME}` (uppercase) in `substituteVars()` alongside existing built-in variables
   - Unknown parameters remain as literal text (existing behavior for unknown vars)

2. **Update load-skill.sh to pass args:**
   - Accept optional 4th argument for skill args
   - Pass to SkillLoader as 5th positional argument

3. **Migrate stakeholder-selection-box/SKILL.md:**
   - Add `parameters: [selected_count, total_count, running, skipped]` to frontmatter
   - Change directive to: `!`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/get-stakeholder-selection-box" "${SELECTED_COUNT}" "${TOTAL_COUNT}" "${RUNNING}" "${SKIPPED}"``

4. **Migrate stakeholder-concern-box/SKILL.md:**
   - Add `parameters: [severity, stakeholder, description, location]` to frontmatter
   - Change directive to: `!`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/get-stakeholder-concern-box" "${SEVERITY}" "${STAKEHOLDER}" "${DESCRIPTION}" "${LOCATION}"``

5. **Revert M358 over-engineered changes (if present in worktree):**
   - Remove -first-use companions for stakeholder-selection-box and stakeholder-concern-box
   - Ensure skills call CLI directly via preprocessor directive, not through load-skill.sh

6. **Add tests to SkillLoaderTest.java:**
   - Test: named parameters resolved from frontmatter + args
   - Test: missing args leave parameters unresolved
   - Test: extra args beyond parameter count are ignored

7. **Update skill-builder/SKILL.md:**
   - Add section documenting named parameter pattern
   - Recommend named params over ${ARGUMENTS} for all new skills
   - Show frontmatter example with `parameters:` field

8. **Run tests:** `mvn -f client/pom.xml test`
