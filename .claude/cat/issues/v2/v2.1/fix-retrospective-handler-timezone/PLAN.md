# Plan: fix-retrospective-handler-timezone

## Problem
run_retrospective_handler.py crashes with TypeError on line 168 when comparing offset-naive and offset-aware datetimes
in _check_action_effectiveness(). The handler fails silently, producing no SCRIPT OUTPUT for the retrospective skill,
causing the skill to fail-fast.

## Satisfies
None

## Reproduction Code
```python
from skill_handlers.run_retrospective_handler import RunRetrospectiveHandler
handler = RunRetrospectiveHandler()
result = handler.handle({"project_root": "/workspace"})
# TypeError: cant compare offset-naive and offset-aware datetimes
```

## Expected vs Actual
- **Expected:** Handler returns SCRIPT OUTPUT RETROSPECTIVE ANALYSIS with formatted analysis
- **Actual:** TypeError crash at line 168: `if m_dt and m_dt > completed_dt`

## Root Cause
_parse_datetime() returns timezone-aware datetimes for strings ending in Z (converts to +00:00) but some timestamp
strings in index.json action_items have timezone offsets (e.g., "2026-01-28T11:30:00-05:00") while mistake timestamps
may be naive or use Z suffix. The comparison at line 168 mixes these.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Minimal - isolated datetime handling
- **Mitigation:** Unit test covering mixed timezone formats

## Files to Modify
- plugin/hooks/skill_handlers/run_retrospective_handler.py - Normalize all datetimes to UTC-aware in _parse_datetime()

## Test Cases
- [ ] Handler completes without crash when index.json has mixed timezone formats
- [ ] _parse_datetime returns timezone-aware datetime for all valid inputs
- [ ] _check_action_effectiveness compares dates without TypeError

## Execution Steps
1. **Fix _parse_datetime():** Ensure all returned datetimes are timezone-aware (UTC). If parsed datetime is naive,
   attach UTC timezone.
2. **Add test:** Create test in tests/ covering mixed timezone comparison scenarios.
3. **Verify:** Run handler against real project data to confirm no crash.

## Success Criteria
- [ ] Handler produces SCRIPT OUTPUT when invoked against project with mixed timezone data
- [ ] All existing tests pass
- [ ] New test covers the timezone comparison edge case
