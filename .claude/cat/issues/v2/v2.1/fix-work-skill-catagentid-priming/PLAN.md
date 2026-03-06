# Plan: fix-work-skill-catagentid-priming

## Problem

The work skill's `first-use.md` documentation shows an incorrect args pattern for invoking
`work-with-issue-agent`, omitting `catAgentId` as the first positional argument. Agents following this
documentation pass `issue_id` as `$0` instead of the session UUID, causing `SkillLoader` to reject the
invocation with: "catAgentId must be UUID format or UUID/subagents/{id}, not a branch name or path."

## Satisfies

None — bugfix for internal tooling documentation

## Reproduction Code

```bash
# Agent follows first-use.md line 221 and invokes:
# args: "${issue_id} ${issue_path} ..."
# SkillLoader rejects: catAgentId must be UUID format
```

## Expected vs Actual

- **Expected:** `first-use.md` shows `${CLAUDE_SESSION_ID}` as the first positional argument
- **Actual:** `first-use.md` omits `catAgentId`, causing agents to pass `issue_id` as `$0`

## Root Cause

Documentation priming — `plugin/skills/work/first-use.md` line 221 teaches the wrong invocation
pattern. The `argument-hint` in `work-with-issue-agent/SKILL.md` correctly shows
`<catAgentId> <issue_id> <issue_path> ...`, but `first-use.md` omits the first argument.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — documentation-only change
- **Mitigation:** Verify updated example matches the argument-hint in SKILL.md

## Files to Modify

- `plugin/skills/work/first-use.md` — Line 221: add `${CLAUDE_SESSION_ID}` as the first positional
  argument in the work-with-issue-agent invocation example

## Test Cases

- [ ] Updated args pattern matches argument-hint in `work-with-issue-agent/SKILL.md`
- [ ] No other skill invocations in the work skill documentation have similar priming gaps

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

1. **Read and update the work skill invocation example**
   - Files: `plugin/skills/work/first-use.md`
   - Find line 221 (args pattern for work-with-issue-agent invocation)
   - Add `${CLAUDE_SESSION_ID}` as the first positional argument
   - Verify the updated line matches the argument-hint in
     `plugin/skills/work-with-issue-agent/SKILL.md`

2. **Audit for similar priming gaps**
   - Files: `plugin/skills/work/first-use.md`, `plugin/skills/work/SKILL.md`
   - Search for other skill invocations that require catAgentId and verify they include it

## Post-conditions

- [ ] `plugin/skills/work/first-use.md` includes `${CLAUDE_SESSION_ID}` as the first positional
  argument in the work-with-issue-agent args pattern
- [ ] The args pattern matches the argument-hint in `plugin/skills/work-with-issue-agent/SKILL.md`
- [ ] No other skill invocations in the work skill documentation have similar priming gaps
