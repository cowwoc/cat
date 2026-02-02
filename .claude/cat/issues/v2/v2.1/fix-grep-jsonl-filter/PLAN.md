# Plan: fix-grep-jsonl-filter

## Problem
The `_filter_json_content()` method in `auto_learn_mistakes.py` fails to filter grep output
that contains JSONL session history. Grep output has the format `line_number:json_content`
(e.g., `42:{"type":"assistant",...}`), but the filter only checks for lines starting with `{`.
Since grep-prefixed lines start with a digit, the JSON is not filtered, causing false positives
when session history contains patterns like "test_failure" in category tables.

## Satisfies
None - infrastructure bugfix

## Root Cause
In `_filter_json_content()` (lines 212-235), the check is:
```python
if stripped.startswith('{') and (
    '"type":' in stripped or ...
)
```

This fails for grep output format `123:{"type":...}` because the line starts with a digit.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Filter might over-aggressively filter valid error messages
- **Mitigation:** Regression test with grep output samples

## Files to Modify
- `plugin/hooks/posttool_handlers/auto_learn_mistakes.py` - Update `_filter_json_content()`

## Test Cases
- [ ] Original bug scenario - grep output with JSONL no longer triggers false positive
- [ ] Legitimate test failures still detected
- [ ] Edge cases: multiple colons in line, non-numeric prefixes

## Execution Steps
1. **Step 1:** Update `_filter_json_content()` to handle grep's `line_number:json` format
   - Strip the `line_number:` prefix before checking for JSON markers
   - Use regex: `re.match(r'^\d+:\s*', stripped)` to detect and strip prefix
   - Verify: Unit test with grep output samples

2. **Step 2:** Add regression test for this specific case
   - Test input: grep output containing session history JSONL
   - Expected: No false positive detected
   - Verify: `python3 /workspace/run_tests.py`

## Acceptance Criteria
- [ ] Bug no longer reproducible
- [ ] Regression test added
- [ ] Root cause addressed
- [ ] No new issues introduced
