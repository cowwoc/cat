# Plan: prevent-rca-cascade-bias

## Current State
The /cat:learn skill uses past mistake conclusions to shortcut analysis. When `recurrence_of` is set,
it classifies as Quick tier (skipping Investigate phase) and assumes past RCA conclusions are correct.
phase-analyze.md asserts "previous fixes FAILED" based solely on the `recurrence_of` field without
independent verification. This creates a cascade where one wrong root cause propagates to all future
analyses of similar mistakes.

## Target State
Every RCA must establish root cause from fresh evidence, independent of past conclusions. The
`recurrence_of` field is preserved as metadata but must NOT shortcut analysis or bias conclusions.
Past conclusions are context to consider, not evidence to inherit.

## Satisfies
None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Recurrence analyses will now run full investigation (takes longer)
- **Mitigation:** Recurrence tracking and recording remain unchanged

## Files to Modify
- `plugin/skills/learn/first-use.md` - Remove `recurrence_of` as Quick tier trigger in Step 1
- `plugin/skills/learn/phase-analyze.md` - Remove bias from recurrence handling in Steps 4b and 4d
- `plugin/skills/learn/phase-prevent.md` - Remove automatic escalation assumption based on recurrence_of

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Modify `plugin/skills/learn/first-use.md` Step 1 tier classification table
   - Remove `recurrence_of is set` from Quick tier criteria
   - Quick tier should ONLY trigger for `protocol_violation` category
   - Update rationale column accordingly
   - Files: `plugin/skills/learn/first-use.md`

2. **Step 2:** Modify `plugin/skills/learn/phase-analyze.md` Step 4b (RCA Depth Verification)
   - In Question 4 (recurring_pattern, lines 119-124), replace the biased guidance
   - Change `if_yes_multiple: "Previous fixes FAILED - dig deeper into WHY they failed"` to guidance
     that says: "Investigate independently. Do NOT assume past RCA conclusions are correct. Verify the
     root cause from fresh evidence, then compare with past conclusions."
   - Change `if_3_plus_recurrences` similarly to remove the assumption that past fixes failed
   - Files: `plugin/skills/learn/phase-analyze.md`

3. **Step 3:** Modify `plugin/skills/learn/phase-analyze.md` Step 4d (Architectural Root Cause Analysis)
   - On line 174, change "When a mistake has recurrences (check `recurrence_of` field in mistakes.json),
     the fixes have failed." to: "When a mistake has recurrences, independently verify the root cause.
     Do NOT assume past analyses were correct ��� they may have had wrong conclusions that cascaded."
   - Remove the implication that multiple recurrences automatically mean fixes failed; they may mean
     past analyses identified the WRONG root cause
   - Files: `plugin/skills/learn/phase-analyze.md`

4. **Step 4:** Modify `plugin/skills/learn/phase-prevent.md` recurrence escalation (around line 277)
   - Change the blocking criteria row for recurrences from "Previous prevention failed" to:
     "Verify independently whether previous prevention was sound before escalating"
   - The recurrence_of field should trigger independent verification of whether the past prevention
     was actually addressing the correct root cause, not automatic escalation
   - Files: `plugin/skills/learn/phase-prevent.md`

## Post-conditions
- [ ] Quick tier classification in first-use.md no longer treats `recurrence_of` as a shortcut trigger
- [ ] phase-analyze.md Step 4b guides independent RCA verification rather than inheriting past conclusions
- [ ] phase-analyze.md Step 4d does not assert that recurrences mean fixes failed
- [ ] phase-prevent.md does not automatically escalate based on recurrence_of without independent verification
- [ ] Recurrence tracking preserved: `recurrence_of` field is still populated correctly in mistake records
- [ ] All tests pass after refactoring
- [ ] E2E: Review the modified skill files and confirm they no longer contain language that biases analysis toward past conclusions