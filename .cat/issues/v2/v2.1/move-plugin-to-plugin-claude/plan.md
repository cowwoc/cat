# Plan: move-plugin-to-plugin-claude

## Current State
All Claude-specific plugin resources (skills, rules, agents, hooks, scripts, templates, etc.) live directly
under `plugin/`. `CLAUDE_PLUGIN_ROOT` resolves to the `plugin/` directory.

## Target State
All resources are moved to `plugin/claude/`, so that `plugin/codex/` can be added alongside it in a future
issue. `CLAUDE_PLUGIN_ROOT` is updated to resolve to `plugin/claude/`.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** HIGH
- **Breaking Changes:** `CLAUDE_PLUGIN_ROOT` path changes; any hardcoded `plugin/` references in settings,
  scripts, or skill files that do not use `${CLAUDE_PLUGIN_ROOT}` will break
- **Mitigation:** Grep for all hardcoded `plugin/` path references before and after the move; verify the
  plugin loads correctly after the change

## Files to Modify
- `plugin/` — all subdirectories and files moved into `plugin/claude/`
- `.claude/settings.json` (or equivalent) — update `CLAUDE_PLUGIN_ROOT` to point to `plugin/claude/`
- `CLAUDE.md` — update any path references from `plugin/` to `plugin/claude/`
- `plugin/migrations/` — add a migration phase to update any stored absolute paths in planning or config files
- Any file outside `plugin/` that contains a hardcoded reference to a `plugin/` subpath

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Move files
- Use `git mv` to move all contents of `plugin/` into `plugin/claude/` to preserve history
  - Files: `plugin/*` → `plugin/claude/*`

### Job 2: Update CLAUDE_PLUGIN_ROOT configuration
- Find where `CLAUDE_PLUGIN_ROOT` is defined or injected (settings.json, hooks, launcher scripts)
- Update the path from `plugin/` to `plugin/claude/`
  - Files: `.claude/settings.json`, any hook or launcher scripts that set `CLAUDE_PLUGIN_ROOT`

### Job 3: Update hardcoded references
- Grep for any remaining hardcoded `plugin/` path references outside the new `plugin/claude/` tree
- Update CLAUDE.md path references from `plugin/` to `plugin/claude/`
  - Files: `CLAUDE.md`, any scripts or configs outside `plugin/` with hardcoded plugin paths

### Job 4: Add migration script
- Add a migration phase to `plugin/claude/migrations/` that updates any stored absolute or relative paths
  in `.cat/` planning files or `.claude/` config files that reference the old `plugin/` layout
  - Files: `plugin/claude/migrations/`

## Post-conditions
- [ ] `git mv` history is intact — `git log --follow plugin/claude/skills/` shows pre-move commits
- [ ] `CLAUDE_PLUGIN_ROOT` resolves to `plugin/claude/` in all invocation contexts
- [ ] No hardcoded references to `plugin/<subdir>` remain outside `plugin/claude/` (grep clean)
- [ ] E2E: A new Claude Code session loads the plugin without errors after the move
