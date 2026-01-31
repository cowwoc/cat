# Plan: implement-a009-render-diff-enforcement

## Goal
Add PreToolUse hook on AskUserQuestion to enforce render-diff usage at approval gates, preventing PATTERN-009 mistakes.

## Satisfies
- A009 action item from retrospective R006

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** May need tuning of approval keyword detection
- **Mitigation:** Use clear approval patterns, allow override with explicit flag

## Files to Modify
- plugin/hooks/pretool_handlers/enforce_render_diff.py - New handler (or update existing warn-approval-without-renderdiff.sh)

## Acceptance Criteria
- [ ] Hook detects approval-related AskUserQuestion calls
- [ ] Warning emitted when render-diff skill wasn't invoked recently
- [ ] No false positives on non-approval questions
- [ ] Tests pass

## Execution Steps
1. Check existing warn-approval-without-renderdiff.sh
2. Enhance or create new handler
3. Add tests
4. Verify with run_tests.py
