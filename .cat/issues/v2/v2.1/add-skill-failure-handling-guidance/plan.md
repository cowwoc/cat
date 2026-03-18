# Plan: add-skill-failure-handling-guidance

## Goal

Add explicit guidance to skill documentation about handling skill failures. When a skill invocation returns
unexpected output (oversized, malformed, or with preprocessing errors), agents must investigate the failure
and report it, rather than creating manual workarounds.

## Parent Requirements

None — internal tooling improvement from learning M507.

## Files to Modify

- `plugin/concepts/skill-loading.md` — Add section on skill failure handling with explicit prohibition
  against skill bypass workarounds

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add "Skill Failure Handling" section to `plugin/concepts/skill-loading.md`:
  - DO NOT bypass a skill with manual construction when skill fails
  - When a skill returns unexpected output, investigate the preprocessing failure and report it
  - Use `/cat:feedback-agent` to report skill failures to the development team
  - Include example of incorrect workaround vs correct error investigation
- Update STATE.md to status: closed, progress: 100%.
  - Files: `plugin/concepts/skill-loading.md`,
    `.cat/issues/v2/v2.1/add-skill-failure-handling-guidance/STATE.md`

## Post-conditions

- [ ] `skill-loading.md` includes explicit section prohibiting skill bypass workarounds
- [ ] Documentation directs agents to report skill failures via `/cat:feedback-agent`
- [ ] Section includes concrete example of what NOT to do (manual construction) vs what TO do (investigate and report)
