# Plan: Fix work-implement-agent STATE.md section placement guidance

## Type
bugfix

## Goal
Closed as obsolete. The issue was created to add STATE.md section placement guidance to
`plugin/skills/work-implement-agent/first-use.md`, but STATE.md was replaced by index.json
in issue 2.1-redesign-issue-file-structure, which already added appropriate index.json guidance.
No implementation changes were needed.

## Files to Modify
- `plugin/skills/work-implement-agent/first-use.md`

## Post-conditions
- [x] The outdated STATE.md guidance blocks have been removed from both the single-subagent and parallel-subagent
  delegation prompt templates
- [x] The file compiles and passes any validation checks

## Sub-Agent Waves

### Wave 1
1. Read the current `plugin/skills/work-implement-agent/first-use.md` to locate the STATE.md section placement
   guidance blocks
2. Identify the two locations where this outdated guidance appears:
   - In the single-subagent delegation prompt template (~line 390)
   - In the parallel-subagent delegation prompt template (~line 601)
3. Remove the complete STATE.md guidance block from both locations (8 lines each)
4. Commit the change with message: `bugfix: remove outdated STATE.md section placement guidance from delegation prompt`
