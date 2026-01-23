# Project Instructions

## Plugin Development

When editing CAT plugin files, always edit the source files in `/workspace/plugin/`, NOT the cached installation in `/home/node/.config/claude/plugins/cache/`.

The cache is a read-only copy that gets overwritten on plugin updates.

## Testing Requirements

After modifying any file that has associated tests, re-run those tests before committing.

**Files with tests:**

| Source File | Test File |
|-------------|-----------|
| `plugin/hooks/bash_handlers/validate_commit_type.py` | `plugin/hooks/bash_handlers/tests/test_validate_commit_type.py` |

**Running tests:**
```bash
cd /workspace/plugin/hooks && python3 bash_handlers/tests/run_tests.py
```

Do not assume tests still pass after modifications. The fix may have introduced regressions or the test expectations may need updating.
