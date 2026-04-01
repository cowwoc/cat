# Fix AOT Training Plugin Prefix Derivation

## Problem

After commit `645372d34` ("fix skill-triggers guard comparison failing due to plugin prefix mismatch"),
`mvn -f client/pom.xml verify` fails during the AOT training phase with:

```
AssertionError: Cannot determine plugin prefix from path: /workspace/plugin.
Expected structure: .../{prefix}/{slug}/{version}/
```

The AOT training creates a `MainClaudeHook` scope, which constructs `PreToolUseHook`, which constructs
`RequireSkillForCommand`. That class calls `scope.getPluginPrefix()` in `loadGuards()` (line 111 of
`RequireSkillForCommand.java`). During AOT training, `CLAUDE_PLUGIN_ROOT` is set to
`${PROJECT_DIR%/*}/plugin` (i.e., `/workspace/plugin`), which doesn't match the expected
`{prefix}/{slug}/{version}/` path structure.

## Root Cause

`build-jlink.sh` line 310 sets `CLAUDE_PLUGIN_ROOT="${PROJECT_DIR%/*}/plugin"` for the AOT training JVM.
`derivePluginPrefix()` in `AbstractClaudePluginScope` expects three levels of parent directories
(version → slug → prefix) but `/workspace/plugin` only has `/workspace` → `/`.

## Fix

In `build-jlink.sh`, change the `CLAUDE_PLUGIN_ROOT` for the AOT training invocation to use a synthetic
path that satisfies the `{prefix}/{slug}/{version}/` invariant:

```bash
# Before (line 310):
CLAUDE_PLUGIN_ROOT="${PROJECT_DIR%/*}/plugin"

# After:
# CLAUDE_PLUGIN_ROOT must follow the {prefix}/{slug}/{version}/ structure expected by
# derivePluginPrefix(). Use a synthetic path under /tmp to satisfy this invariant.
local aot_plugin_root="/tmp/aot/cat/cat/2.1"
mkdir -p "$aot_plugin_root"
CLAUDE_PLUGIN_ROOT="$aot_plugin_root"
```

## Files to Change

1. `client/build-jlink.sh` — Update `CLAUDE_PLUGIN_ROOT` in `generate_startup_archives()` to use a
   synthetic path with the correct directory structure.

## Post-conditions

- `mvn -f client/pom.xml verify` passes (exit code 0)
- AOT training completes successfully
- The generated jlink image works correctly
