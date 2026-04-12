# Project Instructions

## Commit Types

| Path | Commit Type | Reason |
|------|-------------|--------|
| `plugin/**` (except README.md, concepts/) | `feature:` / `refactor:` / `bugfix:` | Plugin source code and skills |
| `plugin/concepts/` | `config:` | Plugin's bundled reference docs (Claude-facing) |
| `client/**` | `feature:` / `refactor:` / `bugfix:` / `test:` | Java client source code |
| `.cat/issues/` | `planning:` | Issue tracking |
| `.claude/**` (other), `CLAUDE.md` | `config:` | Project configuration |
| `**/README.md`, `docs/` | `docs:` | User-facing documentation |
**Rules:**
- `plugin/` and `client/` files use semantic types: `feature:` (new capability), `refactor:` (restructure),
  `bugfix:` (fix), `test:` (tests), `performance:` (optimization)
- `.cat/issues/` files use `planning:`
- Other `.claude/` files and `CLAUDE.md` use `config:`
- `plugin/**/README.md` is `docs:`, not a plugin file
- Mixed commits: if a commit touches plugin files, the type follows the plugin work (even if `.claude/` files are also modified)
- **Convention changes belong with their application:** When adding a new convention to a language or style file (e.g., `java.md`) AND applying it across files in the same session, include the convention file change in the SAME commit as the files that apply it. Group by topic (establish + apply = one unit), not by file location.
- **index.json belongs with implementation:** When closing an issue, index.json updates belong in the SAME commit as the implementation work, using the implementation's commit type (feature:/bugfix:/docs:/etc), NOT in a separate planning: commit
- If a commit would touch both docs and non-docs files, split it into separate commits
- **Do not update closed issue files:** Never modify plan.md or index.json of closed issues unless the user explicitly
  instructs you to. Closed issues are historical records. **Exception:** Automated migration scripts under
  `plugin/migrations/` must process all issues including closed ones to ensure consistent file formats across the
  entire issue tree.

## Issue Workflow vs Direct Implementation

When a user says "update skill X to do Y", "modify plugin Z to support W", or "upgrade client/pom.xml to use
JDK 26", treat this as a feature request requiring the CAT issue workflow — do NOT directly read, analyze, or
edit `plugin/` or `client/` source files.

**Correct interpretation:** Create an issue via `/cat:add-agent` that captures the requested change as work to be
done. The issue's plan.md describes what to update and why. Implementation happens later via `/cat:work-agent`.

**Wrong interpretation:** Reading source code, analyzing it, and proposing or making edits inline — whether
to `plugin/` files, `client/` files, or configuration files inside those directories (e.g., `client/pom.xml`).

**The distinction:** Direct edits bypass worktree isolation and skip the planning, review, and merge process
that protects the codebase. Every `plugin/` or `client/` change goes through an issue — even when the user
frames the request as an immediate action or a "simple update."

**Exception:** If the user explicitly asks for a "quick fix" or "one-line change" AND the change is trivial
enough to fit in a single commit, create a minimal worktree branch via `/cat:add-agent` + `/cat:work-agent` (still not
direct edits to main workspace, and still not a raw `git worktree add` without a CAT issue).

## Approval Gate Workflow

**MANDATORY: Squash commits by topic before EVERY approval gate, even after making code changes in response to user feedback.**

When presenting an issue for the approval gate (merge):
1. All implementation commits must be squashed by topic into logical groups
2. If the user provides feedback and additional changes are made, the squashed commits must be re-squashed before returning to the approval gate
3. Do NOT present an approval gate with a higher commit count than the previous squash attempt, even if individual commits are "correct"
4. The rule applies whether this is the first time presenting the approval gate or a re-presentation after addressing user feedback

**Why:** The merge workflow enforces exactly 1 commit ahead of the target branch. Squashing before the approval gate ensures the commits are final and properly organized. Re-squashing after changes maintains this invariant and prevents the approval gate workflow from being bypassed by incremental commits.

**Pattern:**
- Initial implementation: multiple commits → squash by topic → present approval gate
- User feedback: make changes → re-squash ALL commits (not just new ones) → present approval gate
- Additional feedback: make changes → re-squash ALL commits → present approval gate

The squashing is not a one-time operation; it is part of every approval gate presentation.

## Testing Requirements

**MANDATORY: Invoke `cat:tdd-implementation-agent` before implementing any bugfix or feature with testable inputs/outputs.**

This applies to all implementation contexts: formal `/cat:work-agent` issue workflows AND ad-hoc user-requested fixes.
Write failing tests first, then implement the fix to make them pass.

**MANDATORY: Run all tests before presenting any task for user review.**

```bash
mvn -f client/pom.xml verify -e
```

All tests must pass (exit code 0) before requesting user approval. Do not assume tests still pass after
modifications — the fix may have introduced regressions or the test expectations may need updating.

## License Headers

**MANDATORY:** All new source files must include a license header at the top. Before adding a header to any file, you
**must** read `.claude/rules/license-header.md` — it contains the exact header text, the copyright year (2026),
file-type-specific comment syntax, and the complete list of exemptions. Some file types are exempt; check the
exemptions before adding a header.
