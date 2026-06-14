---
agents: ["main"]
---
# Commit Types

Use these commit types by path:

| Path | Commit Type | Reason |
|------|-------------|--------|
| `plugin/**` (except README.md, concepts/) | `feature:` / `refactor:` / `bugfix:` | Plugin source code and skills |
| `plugin/concepts/` | `config:` | Plugin bundled reference docs (agent-facing) |
| `client/**` | `feature:` / `refactor:` / `bugfix:` / `test:` | Java client source code |
| `.cat/issues/` | `planning:` | Issue tracking |
| `.claude/**` (other), `AGENTS.md` | `config:` | Project configuration |
| `**/README.md`, `docs/` | `docs:` | User-facing documentation |

Rules:
- `plugin/` and `client/` files use semantic types: `feature:` (new capability), `refactor:` (restructure),
  `bugfix:` (fix), `test:` (tests), `performance:` (optimization)
- `.cat/issues/` files use `planning:`
- Other `.claude/` files and `AGENTS.md` use `config:`
- `plugin/**/README.md` is `docs:`, not a plugin file
- Mixed commits: if a commit touches plugin files, the type follows the plugin work (even if `.claude/` files are also modified)
- Convention changes belong with their application: when adding a new convention to a language/style rule (for example
  `java.md`) and applying it in the same session, include both in the same commit
- `index.json` belongs with implementation: when closing an issue, `index.json` updates belong in the same implementation
  commit and use the implementation's commit type (not a separate `planning:` commit)
- If a commit would touch both docs and non-docs files, split into separate commits
- Do not report uncommitted implementation as review-ready
- Do not update closed issue `plan.md` or `index.json` unless explicitly asked; exception: automated migrations under
  `plugin/migrations/` may process closed issues
