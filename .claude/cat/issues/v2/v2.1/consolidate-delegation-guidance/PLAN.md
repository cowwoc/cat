# Plan: consolidate-delegation-guidance

## Problem
Confusion about delegation mechanics causes multiple mistake patterns (M367, M371, M372, M373):
- Task tool (subagent spawner) confused with TaskCreate (todo tracker)
- Manual Task tool spawning instead of using /cat:delegate for batch work
- Polling TaskOutput instead of blocking calls
- Delegation prompts omitting CRITICAL REQUIREMENTS

## Satisfies
- A013 (action item from retrospective)

## Root Cause
Documentation scattered across multiple files without clear disambiguation. System-reminder mentions TaskCreate for
tracking, priming agents to conflate it with Task tool for spawning.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None - documentation only
- **Mitigation:** Review for clarity

## Files to Modify
- plugin/concepts/subagent-delegation.md - Add clear Task vs TaskCreate distinction
- plugin/skills/delegate/SKILL.md - Emphasize blocking TaskOutput calls

## Test Cases
- [ ] Original confusion scenarios - now have clear guidance
- [ ] Documentation is unambiguous

## Execution Steps
1. **Step 1:** Update subagent-delegation.md
   - Add explicit Task vs TaskCreate distinction table
   - Add blocking TaskOutput guidance
   - Verify: Read file and confirm clarity

2. **Step 2:** Update delegate/SKILL.md
   - Add CRITICAL REQUIREMENTS checklist requirement
   - Verify: Read file and confirm inclusion
