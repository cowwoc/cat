# Plan

## Goal

Refactor the scope hierarchy to:

1. Move `getPluginRoot()` and `getPluginPrefix()` from `JvmScope`/`AbstractJvmScope` into `ClaudeHook` and `ClaudeTool` (and their abstract implementations)
2. Remove `ClaudeScope` interface and `AbstractClaudeScope` class entirely, duplicating their methods and field implementations into both `AbstractClaudeHook` and `AbstractClaudeTool` (duplication is intentional — these two hierarchies serve different execution contexts)
3. Change `AbstractClaudeStatusline` to extend `AbstractJvmScope` directly (no longer needs plugin-root or Claude-config concerns)
4. Remove `CLAUDE_PLUGIN_ROOT` from `MainClaudeStatusline` — it reads `projectPath` from the stdin JSON `workspace.project_dir` field instead of env var

This fixes the statusline error "CLAUDE_PLUGIN_ROOT is not set" because the statusline execution context does not provide plugin environment variables.

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior unchanged for ClaudeHook and ClaudeTool consumers
- [ ] Tests passing
- [ ] `JvmScope` no longer declares `getPluginRoot()` or `getPluginPrefix()`
- [ ] `ClaudeScope` interface and `AbstractClaudeScope` class deleted
- [ ] `getPluginRoot()`, `getPluginPrefix()`, `getClaudeConfigPath()`, `getClaudeSessionsPath()`, `getClaudeSessionPath()` declared on both `ClaudeHook` and `ClaudeTool`
- [ ] `AbstractClaudeHook` and `AbstractClaudeTool` contain the implementations formerly in `AbstractJvmScope` and `AbstractClaudeScope`
- [ ] `AbstractClaudeStatusline` extends `AbstractJvmScope` directly
- [ ] `MainClaudeStatusline` does not read `CLAUDE_PLUGIN_ROOT` from environment
- [ ] E2E verification: statusline command runs without error when `CLAUDE_PLUGIN_ROOT` is unset
