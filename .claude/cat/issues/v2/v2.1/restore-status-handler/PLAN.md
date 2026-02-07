# Plan: restore-status-handler

## Problem

`/cat:status` fails with "preprocessing FAILED" because no handler generates the "SCRIPT OUTPUT STATUS DISPLAY" content.

Commit `57e885d1` removed `StatusHandler` from `status_handler.py`, replacing it with `!`\``get-status-display.sh`\`` inline preprocessing in SKILL.md. Commit `3ab4ae96` then replaced that inline call with `load-skill.sh`, which loads `content.md` but does not call `get-status-display.sh`. Result: `content.md` expects "SCRIPT OUTPUT STATUS DISPLAY" in context, but nothing generates it.

## Satisfies

None (bugfix for regression)

## Reproduction Code

```
/cat:status
# Output: "preprocessing FAILED. STOP."
```

## Expected vs Actual

- **Expected:** Status box displayed with project progress
- **Actual:** Fail-fast error because SCRIPT OUTPUT STATUS DISPLAY is missing from context

## Root Cause

`status_handler.py` no longer registers a handler (removed in `57e885d1`). Every other verbatim output skill (help, token-report, render-diff, work, init) has a registered handler that generates SCRIPT OUTPUT. Status is the only one missing.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Minimal - re-adding a handler that calls an existing, tested script
- **Mitigation:** Run existing tests, verify `/cat:status` output

## Files to Modify

- `plugin/hooks/skill_handlers/status_handler.py` - Add `StatusHandler` class that calls `get-status-display.py` and wraps output as "SCRIPT OUTPUT STATUS DISPLAY", register it

## Execution Steps

1. **Add StatusHandler class** to `plugin/hooks/skill_handlers/status_handler.py`
   - Import `register_handler` from `. import register_handler`
   - Import `run_script` from `.base import run_script`
   - Create `StatusHandler` with `handle(self, context)` method
   - Call `get-status-display.py` via `run_python_script` with `--project-dir` argument
   - Wrap output as `SCRIPT OUTPUT STATUS DISPLAY:\n\n{output}`
   - Register with `register_handler("status", _handler)`
   - Files: `plugin/hooks/skill_handlers/status_handler.py`

2. **Run tests** to verify no regressions
   - Files: `run_tests.py`

## Success Criteria

- [ ] `python3 plugin/scripts/get-status-display.py --project-dir /workspace` produces output
- [ ] `status_handler.py` has a registered handler for "status"
- [ ] All existing tests pass
