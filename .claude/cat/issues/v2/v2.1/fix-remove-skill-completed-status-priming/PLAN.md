# Plan: fix-remove-skill-completed-status-priming

## Goal
Fix plugin/skills/remove/first-use.md which uses 'completed' as a status check value, creating a cognitive
anchor that causes agents to write 'complete'/'completed' in STATE.md files instead of the valid 'closed'.

## Satisfies
None (infrastructure/bugfix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None
- **Mitigation:** N/A

## Files to Modify
- plugin/skills/remove/first-use.md - Change 'completed' status check to 'closed'; add INVALID note

## Pre-conditions
- [ ] All dependent issues are closed

## Post-conditions
- [ ] plugin/skills/remove/first-use.md uses 'closed' as the status check value (not 'completed')
- [ ] No occurrence of 'completed' as a status value check remains in the remove skill
- [ ] Warning message refers to 'closed' issues consistently
- [ ] An INVALID note clarifies that 'completed' and 'complete' are not valid status values