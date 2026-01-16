# Project Instructions

## Plugin Development

When editing CAT plugin files, always edit the source files in `/workspace/plugin/`, NOT the cached installation in `/home/node/.config/claude/plugins/cache/`.

The cache is a read-only copy that gets overwritten on plugin updates.

## Testing Requirements

After modifying any file that has associated tests, re-run those tests before committing.

**Test locations:**
- `plugin/hooks/bash_handlers/tests/` - Tests for bash command handlers

**Running tests:**
```bash
cd /workspace/plugin/hooks && python3 << 'PYEOF'
# Simple test runner (see test files for implementation)
PYEOF
```

Do not assume tests still pass after modifications. The fix may have introduced regressions or the test expectations may need updating.
