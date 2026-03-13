# Plan: fix-skill-invocation-missing-catagentid

## Problem

When agents invoke skills via the Skill tool, they pass branch names, skill names, or other wrong values as the
catAgentId instead of the UUID injected by SubagentStartHook. This causes `IllegalArgumentException` in `GetSkill`
at runtime.

Observed failures:
- `cat:stakeholder-review-agent` called with branch name `2.1-pass-file-paths-to-subagents` instead of UUID
- `cat:load-skill-agent` called with agent name `collect-results-agent` instead of UUID

This likely affects all skills, not just the two observed cases.

## Parent Requirements

None

## Reproduction Code

```
Skill(cat:stakeholder-review-agent, args: "2.1-pass-file-paths-to-subagents ...")
// Throws: IllegalArgumentException: catAgentId '2.1-pass-file-paths-to-subagents' does not match a valid format
```

## Expected vs Actual

- **Expected:** Agents pass the UUID catAgentId (injected by SessionStart/SubagentStart hooks) as the first argument
  when invoking any skill
- **Actual:** Agents pass branch names, skill names, or other identifiers instead of the UUID

## Root Cause

To be determined during implementation. Likely causes:
1. SessionStart hook instruction not prominent enough for agents to follow
2. Skill SKILL.md argument-hint fields don't mention catAgentId requirement
3. Agents confuse skill arguments with catAgentId

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — only changes documentation/instructions
- **Mitigation:** Empirical testing to verify agents pass correct catAgentId after fix

## Files to Modify

To be determined during investigation — likely skill instruction files and/or session start hook content.

## Test Cases

- [ ] Agent correctly passes UUID catAgentId when invoking skills
- [ ] Agent does not pass branch names or skill names as catAgentId

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Investigate which skills are affected by missing catAgentId guidance
- Identify root cause: where agents get the wrong value from
- Update skill instructions or session hook to fix the pattern

## Post-conditions

- [ ] All affected skill invocations receive valid UUID catAgentId
- [ ] No regressions in skill loading
- [ ] E2E: Invoke at least one affected skill (e.g., cat:stakeholder-review-agent) and confirm catAgentId is passed
  correctly
