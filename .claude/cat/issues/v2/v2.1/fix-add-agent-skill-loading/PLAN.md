# Plan: fix-add-agent-skill-loading

## Goal
Fix the skill-loader so that `*-agent` skills (e.g., `add-agent`) correctly load their parent skill's
`first-use.md` content when invoked via the Skill tool, instead of silently returning empty content.

## Background
When `cat:add-agent` is invoked via the Skill tool, only "Base directory for this skill: ..." is shown —
no AskUserQuestion, no instructions, nothing. Investigation shows:
- `add-agent/SKILL.md` contains `!backtick"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" add-agent "$ARGUMENTS"backtick`
- skill-loader correctly writes `add` to `skills-loaded` (normalizing `add-agent` → `add`)
- But content loading looks for `add-agent/first-use.md` (which doesn't exist) and returns empty string
- The parent skill `add/first-use.md` exists and contains the full AskUserQuestion workflow

**Scope:** 20 `-agent` skills are affected — all those missing `first-use.md` where the parent skill
(same name without `-agent` suffix) has a `first-use.md`. 4 skills intentionally have no parent
(`extract-investigation-context-agent`, `stakeholder-concern-box-agent`,
`stakeholder-review-box-agent`, `stakeholder-selection-box-agent`) and are unaffected.

**Affected skills (20):**
add-agent, cleanup-agent, config-agent, empirical-test-agent, feedback-agent, get-output-agent,
get-subagent-status-agent, help-agent, init-agent, learn-agent, load-skill-agent,
optimize-execution-agent, recover-from-drift-agent, remove-agent, research-agent,
retrospective-agent, shrink-doc-agent, status-agent, statusline-agent, work-agent

## Satisfies
- None (bug fix from M469 investigation)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must not change behavior for skills that intentionally return empty (internal skills)
- **Mitigation:** Only fall back to parent when (a) `-agent` suffix detected AND (b) parent exists with first-use.md

## Files to Modify
- `client/src/main/java/.../SkillLoader.java` — add fallback: when loading `{name}-agent` and
  `{name}-agent/first-use.md` is missing, check if `{name}/first-use.md` exists and load that instead

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Read `SkillLoader.java` to understand current content loading logic
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
- Add fallback logic: after failing to find `{skill}-agent/first-use.md`, check for `{skill}/first-use.md`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
- Write a test that verifies `add-agent` returns the same content as `add` when invoked without its own
  first-use.md
  - Files: `client/src/test/java/.../SkillLoaderTest.java` (or equivalent)

## Post-conditions
- [ ] `SkillLoader.java` falls back to parent skill's `first-use.md` when `*-agent` variant has none
- [ ] Invoking `cat:add-agent` via Skill tool displays the full AskUserQuestion workflow from `add/first-use.md`
- [ ] The 4 intentionally-internal skills (no parent) continue to return empty content
- [ ] All existing tests pass
- [ ] Manual verification: invoke `cat:add-agent` — AskUserQuestion is shown for issue type selection
