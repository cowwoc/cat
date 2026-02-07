# Plan: collapse-completed-in-status

## Goal
Reduce /cat:status output size by hiding completed issues in the active minor, showing only the 5 most recently
completed plus all non-completed issues. This cuts LLM output by ~70% and proportionally reduces response latency.

## Satisfies
None (performance optimization)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Users may want to see all completed issues occasionally
- **Mitigation:** The collapsed line shows count of hidden issues; full list available via filesystem

## Files to Modify
- `plugin/scripts/get-status-display.py` - Modify active minor task rendering to collapse completed issues
- `tests/test_status_handler.py` - Add test for collapse behavior

## Acceptance Criteria
- [x] Active minor shows at most 5 completed issues (the 5 most recently completed)
- [x] Remaining completed issues are collapsed into a single summary line like "☑️ ... and N more completed"
- [x] Non-completed issues (open, in-progress, blocked) are always shown
- [x] Inactive minors are unaffected (they already show summary counts only)
- [x] All existing tests pass

## Execution Steps
1. **Step 1:** Modify `generate_status_display()` in `plugin/scripts/get-status-display.py`
   - In the active minor task loop, separate tasks into completed vs non-completed
   - Sort completed tasks by directory mtime (most recent first) as proxy for completion recency
   - Show the 5 most recent completed tasks normally
   - Add a summary line for remaining completed: "☑️ ... and N more completed"
   - Show all non-completed tasks normally
   - Files: `plugin/scripts/get-status-display.py`
2. **Step 2:** Update tests
   - Files: `tests/test_status_handler.py`
3. **Step 3:** Run all tests to verify no regressions
