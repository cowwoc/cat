# Project Instructions

## Plugin Development

When editing CAT plugin files, always edit the source files in `/workspace/plugin/`, NOT the cached installation in `/home/node/.config/claude/plugins/cache/`.

The cache is a read-only copy that gets overwritten on plugin updates.

## Testing Requirements

**MANDATORY: Run all tests before presenting any task for user review.**

```bash
python3 /workspace/run_tests.py
```

All tests must pass (exit code 0) before requesting user approval.

**Files with tests:**

| Source File | Test File/Coverage |
|-------------|-------------------|
| `plugin/hooks/bash_handlers/validate_commit_type.py` | Commit type validation |
| `plugin/hooks/skill_handlers/add_handler.py` | Task/version display |
| `plugin/hooks/skill_handlers/status_handler.py` | Display utils, status display |
| `plugin/hooks/skill_handlers/help_handler.py` | Help display |
| `plugin/hooks/skill_handlers/work_handler.py` | Work progress display |
| `plugin/hooks/skill_handlers/cleanup_handler.py` | Cleanup display |

Do not assume tests still pass after modifications. The fix may have introduced regressions or the test expectations may need updating.
