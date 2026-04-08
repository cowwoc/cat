# Plan: fix-claude-runner-plugin-root-isolation

## Goal

When `claude-runner` creates an isolated test session (via `--plugin-source`), also set `CLAUDE_PLUGIN_ROOT`
in the child process environment to point at the isolated config dir's plugin path. This ensures that
`get-skill`, `MainClaudeTool`, and every other consumer of `CLAUDE_PLUGIN_ROOT` reads from the isolated plugin
copy — not the main cache — during SPRT test runs.

## Type

bugfix

## Background

`ClaudeRunner.java` currently sets `CLAUDE_CONFIG_DIR` to an isolated temp directory but does NOT override
`CLAUDE_PLUGIN_ROOT`. As a result:

- `get-skill` reads `CLAUDE_PLUGIN_ROOT` (main cache path) to find `first-use.md` and `SKILL.md` files
- Changes made to plugin files in the worktree (e.g., `first-use.md`, `plugin/rules/*.md`) are NEVER visible
  to SPRT test agents
- All test trials execute against the last main-cache state, regardless of what the instruction-builder committed

This means SPRT tests cannot measure the effect of worktree changes, making the test loop meaningless for
skill development iteration.

## Approach

In `ClaudeRunner.java`, after setting `CLAUDE_CONFIG_DIR`, derive the plugin root path from the isolated config
dir using the same layout as the main cache:

```
${isolated_config_dir}/plugins/cache/${vendor}/${name}/${version}
```

The vendor, name, and version values are already available in `ClaudeRunner` (used to construct
`--plugin-source`). Set `CLAUDE_PLUGIN_ROOT` to this derived path in the child process environment:

```java
// Already exists:
env.put("CLAUDE_CONFIG_DIR", isolatedConfigDir.toString());

// Add:
Path isolatedPluginRoot = isolatedConfigDir.resolve("plugins/cache/cat/cat/2.1");
env.put("CLAUDE_PLUGIN_ROOT", isolatedPluginRoot.toString());
```

No changes needed to `get-skill`, `MainClaudeTool`, or any other consumer — they all read `CLAUDE_PLUGIN_ROOT`
and will automatically use the isolated plugin.

## Why Not Sync to the Main Cache

Syncing worktree plugin files to `CLAUDE_PLUGIN_ROOT` before each SPRT run is explicitly prohibited:
- The main plugin cache is shared across all active Claude sessions; modifying it mid-session affects live
  production sessions, not just test runs
- If two instruction-builder runs execute concurrently, they would overwrite each other's files in the cache
- The isolated config dir approach is already multi-instance safe; extending it to cover `CLAUDE_PLUGIN_ROOT`
  is the correct fix

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/ClaudeRunner.java`
  - Add `env.put("CLAUDE_PLUGIN_ROOT", isolatedPluginRoot.toString())` after the `CLAUDE_CONFIG_DIR` line
  - Derive `isolatedPluginRoot` from `isolatedConfigDir` + the plugin layout path

## Post-Conditions

- [ ] `ClaudeRunner.java` sets `CLAUDE_PLUGIN_ROOT` to `${isolated_config_dir}/plugins/cache/cat/cat/2.1`
      in the child process environment when `--plugin-source` is provided
- [ ] SPRT test agents read `first-use.md` from the isolated plugin copy, not the main cache
- [ ] Changes to `plugin/rules/*.md` in the worktree are reflected in SPRT test trials
- [ ] No changes to `get-skill`, `MainClaudeTool`, or any other CLAUDE_PLUGIN_ROOT consumer
- [ ] All existing tests pass (`mvn -f client/pom.xml verify`)
