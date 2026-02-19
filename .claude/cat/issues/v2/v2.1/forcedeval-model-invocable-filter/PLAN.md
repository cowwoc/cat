# Plan: ForcedEvalSkills should filter on model-invocable, not user-invocable

## Goal
Change ForcedEvalSkills to use a `model-invocable` frontmatter field instead of inferring model-invocability from the
absence of `user-invocable: false`. This separates the two distinct concepts: whether a user can type `/skill-name` vs
whether the model should auto-evaluate the skill for activation on every prompt.

## Satisfies
None (bug fix / correctness improvement)

## Background

Three categories of skills exist:

| Category | User types it? | Model auto-evaluates? | Examples |
|----------|---------------|----------------------|----------|
| User-invocable only | Yes | No | `/cat:skill-builder` (user triggers, model doesn't auto-activate) |
| Model-invocable | Maybe | Yes | `/cat:work`, `/cat:learn` (model should check every prompt) |
| Internal-only | No | No | `-first-use` skills |

Currently `ForcedEvalSkills` uses `user-invocable: false` as a proxy for "don't include in forced eval." This is wrong
because some skills like `cat:skill-builder` are user-invocable but should NOT be in the forced eval list (they have low
false-positive risk and the user will invoke them explicitly). Conversely, some skills marked `user-invocable: false`
(like `cat:render-diff`, `cat:token-report`) DO appear in the SessionStart listing and should be model-evaluated.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must update all SKILL.md frontmatter files that need `model-invocable: true`
- **Mitigation:** Current forced eval list provides the ground truth for which skills should be model-invocable

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/ForcedEvalSkills.java` — change filter from
  `user-invocable` to `model-invocable`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ForcedEvalSkillsTest.java` — update tests
- All `plugin/skills/*/SKILL.md` files that should appear in forced eval — add `model-invocable: true`

## Acceptance Criteria
- [ ] New `model-invocable: true` frontmatter field added to skills that should be in forced eval
- [ ] `ForcedEvalSkills` filters on `model-invocable: true` instead of `!user-invocable: false`
- [ ] Skills without `model-invocable: true` are excluded from forced eval even if user-invocable
- [ ] Javadoc accurately describes the filtering logic
- [ ] All tests pass

## Execution Steps
1. **Add `model-invocable: true` to SKILL.md frontmatter** for all skills currently in the forced eval list
   - Files: all `plugin/skills/*/SKILL.md` that currently pass the filter
2. **Update ForcedEvalSkills.java** to check for `model-invocable: true` instead of `!user-invocable: false`
   - Rename `isUserInvocableFalse()` to `isModelInvocable()` with inverted logic
   - Update Javadoc on all `discover*` methods
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/ForcedEvalSkills.java`
3. **Update tests** to reflect the new filtering behavior
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/ForcedEvalSkillsTest.java`
4. **Run tests** to verify
   - Command: `mvn -f client/pom.xml test`
