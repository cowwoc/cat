# Codex-Friendly Help Output

## Objective
Redesign `$cat:help` output so it renders cleanly in the Codex terminal UI.

## Background
Codex TUI renders headings, emphasis, inline code, lists, links, and code blocks, but it does not render
GitHub-flavored Markdown tables. Pipe tables remain raw text, and headings keep their literal `#` markers with styling.
The help output should therefore use terminal-friendly sections and compact lists instead of Markdown tables.

## Requirements
- Replace Markdown pipe tables in `$cat:help` output with Codex-friendly list or aligned text formats.
- Preserve the same user-facing information:
  - user-facing skills
  - work scopes
  - project structure
  - branch naming
- Keep the first screen scanable in a terminal.
- Avoid nested bullets where a short labeled line is clearer.
- Avoid decorative boxes or layouts that depend on exact terminal width.

## Proposed Output
````markdown
# CAT Command Reference

Use dollar-prefixed skill mentions to select a CAT workflow explicitly.

## Start Here

- `$cat:init` - Set up a new or existing project.
- `$cat:status` - See what's happening and what to do next.
- `$cat:config` - Change trust level and workflow preferences.
- `$cat:cleanup` - Remove stale locks and abandoned worktrees.

## Work Scope

Ask the agent to work at different scopes:

- `Next issue` - Work through all incomplete issues.
- `Work on v1 issues` - Work on all issues in `v1.x.x`.
- `Work on v1.0 issues` - Work on all issues in `v1.0.x`.
- `Work on v1.0.1 issues` - Work on all issues in `v1.0.1`.
- `Work on 1.0-parse` - Work on one specific issue.

Behavior:
- Auto-continues to the next issue when trust is `medium` or `high`.
- Creates a worktree and issue branch per issue.
- Runs an approval gate when trust is below `high`.

## Project Structure

CAT supports two layouts:

- 2-level: `MAJOR -> MINOR -> ISSUE`
- 3-level: `MAJOR -> MINOR -> PATCH -> ISSUE`

```text
.cat/
├── project.md
├── roadmap.md
├── config.json
└── v{major}/
    └── v{major}.{minor}/
        ├── {issue-name}/
        └── v{major}.{minor}.{patch}/
            └── {issue-name}/
```

Issue changelog content is embedded in commit messages.

## Branch Naming

- Issue, 2-level: `{major}.{minor}-{issue-name}`
  Example: `1.0-parse-tokens`
- Issue, 3-level: `{major}.{minor}.{patch}-{issue-name}`
  Example: `1.0.1-fix-edge-case`
- Subagent: `{issue-branch}-sub-{uuid}`
  Example: `1.0-parse-tokens-sub-a1b2c3`
````

## Acceptance Criteria
- `$cat:help` no longer emits Markdown pipe tables.
- The output remains readable when rendered by Codex TUI.
- The output preserves all information from the current help response.
- Any skill tests or snapshot tests that cover help output are updated or added.
- `mvn -f client/pom.xml verify -e` passes.

## Post-conditions
- User-facing help is optimized for Codex terminal rendering.
- No behavior changes outside `$cat:help` output.
