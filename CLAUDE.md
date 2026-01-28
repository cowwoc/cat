# Project Instructions

## Commit Types

**CRITICAL (M255):** Claude-facing instruction files use `config:`, NOT `docs:`.

| Path | Commit Type | Reason |
|------|-------------|--------|
| `CLAUDE.md` | `config:` | Claude project instructions |
| `plugin/concepts/*.md` | `config:` | Claude reference docs |
| `plugin/commands/*.md` | `config:` | Claude commands |
| `plugin/skills/*/*.md` | `config:` | Claude skills |
| `plugin/hooks/*.py` | `config:` | Plugin source code |
| `README.md`, `docs/` | `docs:` | User-facing documentation |

Use `docs:` ONLY for files end-users read (README, API docs, etc.).

## Plugin Development

When editing CAT plugin files, always edit the source files in `/workspace/plugin/`, NOT the cached installation in `/home/node/.config/claude/plugins/cache/`.

The cache is a read-only copy that gets overwritten on plugin updates.

**Worktree Path Handling (M267):** When working in a worktree (e.g., `/workspace/.worktrees/task-name/`), use relative paths like `plugin/skills/` instead of absolute paths like `/workspace/plugin/`. Absolute paths to `/workspace/` bypass worktree isolation and modify the main workspace instead.

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

## Java Code

See `.claude/cat/conventions/java.md` for Java coding conventions (Allman braces, 2-space indent, TestNG, JsonMapper).
