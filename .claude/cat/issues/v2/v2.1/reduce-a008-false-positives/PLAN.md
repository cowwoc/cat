# Plan: reduce-a008-false-positives

## Problem
The A008 PostToolUse hook (`detect_manual_boxes.py`) fires false positives when the assistant correctly copies
verbatim output from rendering scripts like `render-add-complete.sh`. The hook detects box-drawing characters
(e.g., `╭╮╰╯│─`) in assistant output and warns about manual box construction, but it cannot distinguish between:
- **Correctly copied script output** (e.g., the Issue Created box from render-add-complete.sh)
- **Manually constructed boxes** (the actual anti-pattern A008 targets)

Evidence from session 3f82d1d1: The hook fired on assistant messages containing boxes from
`render-add-complete.sh` output that was properly copied verbatim, producing repeated false warnings
that added ~500 tokens of noise per occurrence.

## Root Cause
The hook checks for box-drawing characters in assistant text but has no way to correlate them with
preceding Bash tool calls that produced script output containing those same characters.

## Satisfies
None - bugfix for existing A008 hook

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Could miss actual manual box construction if heuristic is too permissive
- **Mitigation:** Track which Bash calls produced box characters and only flag boxes that don't match
  any recent script output

## Files to Modify
- `plugin/hooks/posttool_handlers/detect_manual_boxes.py` - Add logic to track recent script outputs
  containing box characters and suppress warnings when assistant output matches

## Execution Steps
1. **Step 1:** Read `plugin/hooks/posttool_handlers/detect_manual_boxes.py` to understand current detection logic
2. **Step 2:** Add tracking of recent Bash tool results that contain box-drawing characters (e.g., via a session-scoped
   state file or by checking the hook_data for preceding tool results)
3. **Step 3:** When box characters are detected in assistant output, check if they match content from a recent
   Bash tool result. If so, suppress the warning (the assistant correctly copied script output).
4. **Step 4:** If no matching script output found, emit the warning as before (likely manual construction)
5. **Step 5:** Run `python3 /workspace/run_tests.py` to verify no regressions

## Success Criteria
- [ ] A008 hook no longer fires when assistant copies render-add-complete.sh output verbatim
- [ ] A008 hook still fires when assistant manually constructs boxes without preceding script execution
- [ ] All tests pass
- [ ] No false positives in a test session with both script-generated and manual boxes
