# Plan

## Goal

Move getPluginRoot() and getPluginPrefix() from JvmScope/AbstractJvmScope to ClaudeScope/AbstractClaudeScope, and change AbstractClaudeStatusline to extend AbstractJvmScope directly instead of AbstractClaudeScope. This removes the CLAUDE_PLUGIN_ROOT dependency from ClaudeStatusline, fixing the statusline error when CLAUDE_PLUGIN_ROOT is not set in the statusline execution context.

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior unchanged for ClaudeHook and ClaudeTool consumers
- [ ] Tests passing
- [ ] JvmScope interface no longer declares getPluginRoot() or getPluginPrefix()
- [ ] getPluginRoot() and getPluginPrefix() accessible via ClaudeHook and ClaudeTool (through ClaudeScope)
- [ ] MainClaudeStatusline does not read CLAUDE_PLUGIN_ROOT from environment
- [ ] E2E verification: statusline command runs without error when CLAUDE_PLUGIN_ROOT is unset
