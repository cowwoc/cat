# Plan: fix-stakeholder-selection-box-invocation

## Problem

The `stakeholder-review-agent` skill invokes `cat:stakeholder-selection-box-agent` with the CAT
agent session ID prepended as the first argument (following the convention for all other cat: skills).
However, `stakeholder-selection-box-agent` is an internal display skill that does not accept a
session ID — it expects `selected-count` as its first argument. This causes the invocation to fail
with "selected-count must be an integer", requiring a retry via the underlying binary
(`get-stakeholder-selection-box`), which wastes one Skill tool call per review session.

## Satisfies

None — correctness fix

## Root Cause

Session-aware cat: skills require a session ID as first arg (injected by the main agent or calling
skill). Internal display box skills (`stakeholder-selection-box-agent`,
`stakeholder-concern-box-agent`, `stakeholder-review-box-agent`) do not accept session IDs —
they take only display parameters. The calling skill incorrectly applies the session ID convention
to an internal display skill.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Fix must not break the session ID convention for skills that require it
- **Mitigation:** Change only the invocation of display box skills in stakeholder-review-agent

## Files to Modify

- `plugin/skills/stakeholder-review-agent/SKILL.md` — invoke display box skills without session ID
  prefix, or invoke via the underlying binary directly

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Update `stakeholder-selection-box-agent` invocation in stakeholder-review-agent to omit the
  session ID prefix (or call the underlying binary directly via Bash)
  - Files: `plugin/skills/stakeholder-review-agent/SKILL.md`
- Verify that `stakeholder-concern-box-agent` and `stakeholder-review-box-agent` invocations follow
  the same corrected pattern if they have the same issue
  - Files: `plugin/skills/stakeholder-review-agent/SKILL.md`

## Post-conditions

- [ ] `cat:stakeholder-selection-box-agent` invoked without session ID prefix
- [ ] Stakeholder review session produces selection box on first invocation without requiring retry
- [ ] No extra Skill tool calls for display box generation during review
- [ ] All existing tests pass
