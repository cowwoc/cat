# Plan: fix-add-agent-skill-loading

## Goal
Fix the skill-loader so that `*-agent` skills (e.g., `add-agent`) correctly load their parent skill's
`first-use.md` content when invoked via the Skill tool, and make `first-use.md` mandatory for all skills
that use skill-loader — throwing `FileNotFoundException` if neither the skill's own nor its parent's
`first-use.md` exists.

## Background
When `cat:add-agent` is invoked via the Skill tool, only "Base directory for this skill: ..." is shown —
no AskUserQuestion, no instructions, nothing. Investigation shows:
- `add-agent/SKILL.md` contains `!backtick"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" add-agent "$ARGUMENTS"backtick`
- skill-loader correctly writes `add` to `skills-loaded` (normalizing `add-agent` → `add`)
- But content loading looks for `add-agent/first-use.md` (which doesn't exist) and returns empty string
- The parent skill `add/first-use.md` exists and contains the full AskUserQuestion workflow

**Why mandatory `first-use.md`:** Skills that use `skill-loader` opt into the first-use/reference
mechanism. If a skill uses `skill-loader` but has no `first-use.md`, its instructions live only in
`SKILL.md` and get re-injected on every invocation, wasting context. A missing `first-use.md` is a
configuration error that should fail fast, not silently return empty.

**Scope:** 20 `-agent` skills are affected by the fallback fix. 4 internal skills
(`extract-investigation-context-agent`, `stakeholder-concern-box-agent`,
`stakeholder-review-box-agent`, `stakeholder-selection-box-agent`) are unaffected because they
call other binaries directly and do not use `skill-loader`.

**Affected skills (20):**
add-agent, cleanup-agent, config-agent, empirical-test-agent, feedback-agent, get-output-agent,
get-subagent-status-agent, help-agent, init-agent, learn-agent, load-skill-agent,
optimize-execution-agent, recover-from-drift-agent, remove-agent, research-agent,
retrospective-agent, shrink-doc-agent, status-agent, statusline-agent, work-agent

## Satisfies
- None (bug fix from M469 investigation)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must not break skills that don't use skill-loader (the 4 internal skills are unaffected)
- **Mitigation:** Only skills invoking skill-loader go through `loadRawContent`; the exception catches
  future misconfigurations where a skill uses skill-loader without a `first-use.md`

## Files to Modify
- `client/src/main/java/.../SkillLoader.java` — add `-agent` fallback and throw `FileNotFoundException`
  when no `first-use.md` is found
- `client/src/test/java/.../SkillLoaderTest.java` — update tests

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Add fallback logic in `loadRawContent`: when loading `{skill}-agent` and `{skill}-agent/first-use.md`
  is missing, check for `{skill}/first-use.md`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
- Change `loadRawContent` to throw `FileNotFoundException` instead of returning `""` when no
  `first-use.md` is found (neither own nor parent)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
- Update test `agentSkillWithNoParentReturnsEmpty` → `agentSkillWithNoParentThrowsException`
  - Files: `client/src/test/java/.../SkillLoaderTest.java`
- Update test `loadHandlesMissingContentFile` to expect `FileNotFoundException` instead of empty result
  - Files: `client/src/test/java/.../SkillLoaderTest.java`

## Post-conditions
- [ ] `SkillLoader.java` falls back to parent skill's `first-use.md` when `*-agent` variant has none
- [ ] `SkillLoader.java` throws `FileNotFoundException` when no `first-use.md` exists (own or parent)
- [ ] Invoking `cat:add-agent` via Skill tool displays the full AskUserQuestion workflow from `add/first-use.md`
- [ ] The 4 internal skills (which don't use skill-loader) are unaffected
- [ ] All existing tests pass
- [ ] Manual verification: invoke `cat:add-agent` — AskUserQuestion is shown for issue type selection
