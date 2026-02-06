# Plan: fix-user-question-acknowledgment

## Problem
Agent fails to acknowledge user questions that arrive mid-operation via system-reminder, continuing work without adding
to TaskList or responding. Existing documentation-level prevention (CAT SESSION INSTRUCTIONS) proved insufficient - M366
occurred despite instructions being present.

## Satisfies
None - infrastructure/prevention issue from M366 escalation

## Root Cause
Completion bias causes agent to prioritize finishing current work over acknowledging incoming user questions.
Documentation-level prevention relies on behavioral compliance, which fails under cognitive load.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Hook could inject reminders too aggressively if pattern matching is overly broad
- **Mitigation:** Start with narrow pattern matching, expand based on false negatives

## Files to Modify
- `plugin/hooks/posttool_handlers/user_question_reminder.py` - New handler to detect and remind
- `run_tests.py` - Add test coverage for new handler

## Test Cases
- [ ] Detects user question in system-reminder within tool result
- [ ] Injects reminder to acknowledge in additionalContext
- [ ] Does NOT trigger for non-question system-reminders
- [ ] Does NOT trigger when question is already acknowledged

## Execution Steps
1. **Step 1:** Create `user_question_reminder.py` handler
   - Pattern: Detect `system-reminder` containing user question indicators
   - Output: Return additionalContext with acknowledgment reminder
   - Verify: Unit tests pass

2. **Step 2:** Add tests to run_tests.py
   - Test question detection patterns
   - Test non-triggering cases
   - Verify: `python3 run_tests.py` passes

3. **Step 3:** Update M366 to mark prevention_implemented: true
   - Verify: M366 entry shows prevention complete
