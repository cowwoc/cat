# Project Instructions

## Commit Types

| Path | Commit Type | Reason |
|------|-------------|--------|
| `plugin/**` (except README.md) | `feature:` / `refactor:` / `bugfix:` | Plugin source code and skills |
| `.claude/cat/issues/` | `planning:` | Issue tracking |
| `.claude/**` (other), `CLAUDE.md` | `config:` | Project configuration |
| `**/README.md`, `docs/` | `docs:` | User-facing documentation |

**Rules:**
- `plugin/` files use semantic types: `feature:` (new capability), `refactor:` (restructure), `bugfix:` (fix), `test:` (tests), `performance:` (optimization)
- `.claude/cat/issues/` files use `planning:`
- Other `.claude/` files and `CLAUDE.md` use `config:`
- `plugin/**/README.md` is `docs:`, not a plugin file
- Mixed commits: if a commit touches plugin files, the type follows the plugin work (even if `.claude/` files are also modified)
- **STATE.md belongs with implementation (M487):** When closing an issue, STATE.md updates belong in the SAME commit as the implementation work, using the implementation's commit type (feature:/bugfix:/docs:/etc), NOT in a separate planning: commit
- If a commit would touch both docs and non-docs files, split it into separate commits

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
