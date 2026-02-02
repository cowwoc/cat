# Plan: fix-skill-preprocessor-args

## Current State

The `/cat:work` skill invokes `/cat:work-with-task` via the Skill tool with JSON arguments
containing `task_id`. The `work_with_task_handler` is supposed to parse this task_id and
generate progress banners via `get-progress-banner.sh`.

**Bug:** The PostToolUse handler (`skill_preprocessor_output.py`) constructs the `user_prompt`
passed to skill handlers WITHOUT including the skill arguments:

```python
"user_prompt": f"/cat:{cat_skill_name}",  # Missing args!
```

This causes `work_with_task_handler` to fail silently because it can't find the JSON arguments
containing task_id.

## Target State

Fix `skill_preprocessor_output.py` to include skill arguments in the user_prompt passed to handlers.

## Satisfies

None - bug fix

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None - fixes broken functionality
- **Mitigation:** Tests verify no regressions

## Files to Modify

- `plugin/hooks/posttool_handlers/skill_preprocessor_output.py` - Include args in user_prompt

## Acceptance Criteria

- [x] All tests still pass
- [x] Args included in user_prompt for skill handlers

## Execution Steps

1. **Step 1:** Add args extraction and inclusion in skill_preprocessor_output.py
2. **Step 2:** Run tests
