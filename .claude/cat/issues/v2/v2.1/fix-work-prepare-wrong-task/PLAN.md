# Plan: fix-work-prepare-wrong-task

## Problem
When users run `/cat:work <task-id>` (e.g., `/cat:work 2.1-migrate-api`), the prepare script ignores the specified task ID and returns the first available task by priority instead.

## Satisfies
None - infrastructure bugfix

## Reproduction Code
```
/cat:work 2.1-migrate-api
# Expected: selects and prepares 2.1-migrate-api
# Actual: selects first available task alphabetically/by priority, ignoring the argument
```

## Expected vs Actual
- **Expected:** `/cat:work <task-id>` selects and prepares the exact task specified by the user
- **Actual:** The task ID argument is ignored; the prepare script picks the first available task based on priority scoring

## Root Cause
The work skill (`plugin/skills/work/content.md`) does not parse ARGUMENTS for task ID patterns and does not pass them to `work-prepare.py` via the `--issue-id` parameter. The downstream infrastructure already supports specific task selection:
- `work-prepare.py` accepts `--issue-id` (line ~598)
- `get-available-issues.sh` handles specific issue IDs via positional argument (lines ~108-120, ~584-654)

The missing link is argument parsing in the skill content that detects task ID patterns (e.g., `2.1-task-name`) and forwards them.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Could break filter/exclusion pattern handling if argument parsing is not correctly prioritized
- **Mitigation:** Test both task ID arguments and filter arguments to verify correct routing

## Files to Modify
- `plugin/skills/work/content.md` - Add argument parsing logic to detect task ID pattern and pass `--issue-id` to work-prepare.py

## Test Cases
- [ ] `/cat:work 2.1-fix-work-prepare-wrong-task` selects exact task specified
- [ ] `/cat:work skip compression` still correctly passes exclusion pattern
- [ ] `/cat:work` with no arguments still selects by priority (existing behavior)
- [ ] `/cat:work 2.1` passes version filter correctly

## Execution Steps
1. **Step 1:** Read `plugin/skills/work/content.md` and locate the Phase 1 prepare section (around lines 62-84)
   - Files: `plugin/skills/work/content.md`
2. **Step 2:** Add argument parsing logic before the prepare script call that:
   - Checks if ARGUMENTS matches task ID pattern `^[0-9]+\.[0-9]+-[a-zA-Z0-9_-]+$`
   - If match: set `ISSUE_ID_ARG="--issue-id ${ARGUMENTS}"`
   - Also check for version-only pattern `^[0-9]+\.[0-9]+$` and pass as `--issue-id`
   - Else: fall through to existing exclusion pattern handling
3. **Step 3:** Update the python3 invocation to include `${ISSUE_ID_ARG}` parameter
4. **Step 4:** Verify by reading the modified file that the argument flow is correct

## Success Criteria
- [ ] Bug fixed: task ID arguments are forwarded to work-prepare.py
- [ ] Regression test: filter/exclusion arguments still work
- [ ] No new issues introduced