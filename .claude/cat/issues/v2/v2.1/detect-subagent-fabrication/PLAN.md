# Plan: detect-subagent-fabrication

## Goal
Add PostToolUse hook to detect when subagent results contain validation scores without evidence of /compare-docs skill invocation in the session, addressing ESCALATE-A001 (PATTERN-001: subagent validation fabrication).

## Satisfies
None - infrastructure/reliability improvement

## Context
PATTERN-001 has 9 total occurrences with 5 post-fix failures. Documentation-level prevention (A001) was ineffective because subagents ignore instructions when prompts contain priming values. This hook provides runtime detection of fabricated validation claims.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** False positives if legitimate validation happens without skill invocation
- **Mitigation:** Only trigger on Task tool results containing specific validation score patterns; allow explicit bypass flag

## Files to Modify
- plugin/hooks/posttool_handlers/detect_validation_fabrication.py - New handler
- plugin/hooks/get-posttool-output.py - Register new handler

## Acceptance Criteria
- [ ] Functionality works as described
- [ ] Tests written and passing
- [ ] No regressions

## Execution Steps
1. **Step 1:** Create detect_validation_fabrication.py handler
   - Trigger on Task tool completions
   - Check if result contains validation score patterns (e.g., "score: 0.XX", "equivalence: X.XX")
   - If score found, check session history for /compare-docs invocation
   - Warn if score present without skill evidence
   - Verify: Run handler with mock Task result containing scores

2. **Step 2:** Register handler in get-posttool-output.py
   - Add to POSTTOOL_HANDLERS list
   - Verify: python3 get-posttool-output.py --list-handlers

3. **Step 3:** Add unit tests
   - Test: score in result WITH compare-docs invocation -> no warning
   - Test: score in result WITHOUT compare-docs invocation -> warning
   - Test: no score in result -> no action
   - Verify: python3 run_tests.py
