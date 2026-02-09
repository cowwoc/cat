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

When editing CAT plugin files, always edit the source files in `/workspace/plugin/`, NOT the cached installation in
`/home/node/.config/claude/plugins/cache/`.

The cache is a read-only copy that gets overwritten on plugin updates.

**Worktree Path Handling (M267):** When working in a worktree (e.g., `/workspace/.claude/cat/worktrees/task-name/`), use
relative paths like `plugin/skills/` instead of absolute paths like `/workspace/plugin/`. Absolute paths to
`/workspace/` bypass worktree isolation and modify the main workspace instead.

## Skill Step Numbering

All skill steps must be 1-based and sequential (Step 1, Step 2, Step 3, etc.).

When adding a new step to a skill:
1. Insert the step at the appropriate position
2. Renumber all subsequent steps
3. Update any external references to the renumbered steps

Avoid "half steps" (Step 4.5) or lettered sub-steps (Step 4a, 4b) unless there is a specific reason to couple multiple
steps under the same number.

## Testing Requirements

**MANDATORY: Run all tests before presenting any task for user review.**

```bash
mvn -f hooks/pom.xml test
```

All tests must pass (exit code 0) before requesting user approval.

Do not assume tests still pass after modifications. The fix may have introduced regressions or the test expectations may
need updating.

## Language Conventions

**MANDATORY:** Before editing files in a language with conventions, read the corresponding convention file.

| File Pattern | Convention File | Read Before Editing |
|--------------|-----------------|---------------------|
| `*.java` | `.claude/cat/conventions/java.md` | Any `.java` file |

**Workflow:**
1. Before your first edit to a `.java` file in a session, read `java.md`
2. Apply conventions from that file to all edits
3. If unsure about a convention, re-read the relevant section

This ensures consistent code style (Allman braces, 2-space indent, TestNG, JsonMapper for Java).
