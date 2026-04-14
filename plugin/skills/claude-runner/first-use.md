<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Claude Runner

Launch a nested Claude CLI instance with an isolated config directory and updated plugin cache.

## Purpose

Run a prompt in a fresh Claude environment where plugin source files and jlink binaries are copied from the
current worktree into an isolated config directory. This avoids needing `/reload-plugins` in the parent
session — the nested instance sees the latest plugin code automatically.

## When to Use

- Testing skill compliance without polluting the parent session's state
- Running a prompt with updated plugin code before the parent has reloaded
- Spawning an isolated Claude instance for empirical testing

## Arguments

| Position | Name | Required | Description |
|----------|------|----------|-------------|
| 1 | `prompt` | yes | Path to a file containing the prompt to send to the nested Claude instance |
| 2 | `model` | no | Model to use: haiku, sonnet, or opus (default: haiku) |
| 3 | `cwd` | no | Working directory for the nested instance (default: current directory) |

## How It Works

1. Copies `CLAUDE_CONFIG_DIR` to a temporary directory
2. Overwrites the plugin cache in the copy with:
   - Plugin source files from the current worktree (`plugin/`)
   - Built jlink binaries from `client/target/jlink/bin/`
3. Launches `claude -p` with `CLAUDE_CONFIG_DIR` pointing to the temporary copy
4. Sends the prompt via stream-json input format
5. Returns the parsed output (text blocks, tool uses, session ID)
6. Deletes the temporary config directory

## Usage

```bash
# Determine paths
WORKTREE_PATH=$(git rev-parse --show-toplevel)
PLUGIN_SOURCE="${WORKTREE_PATH}/plugin"
JLINK_BIN="${WORKTREE_PATH}/client/target/jlink/bin"
if [[ ! -d "$JLINK_BIN" ]]; then
  JLINK_BIN="${CLAUDE_PROJECT_DIR}/client/target/jlink/bin"
fi

# Write prompt to a file, then run via the Java CLI tool
echo "Your test prompt here" > /tmp/my-prompt.txt
"${CLAUDE_PLUGIN_ROOT}/client/bin/claude-runner" \
  --prompt /tmp/my-prompt.txt \
  --model <model> \
  --plugin-source "$PLUGIN_SOURCE" \
  --jlink-bin "$JLINK_BIN" \
  --cwd "$WORKTREE_PATH"
```

## Output

The tool prints the nested instance's text output to stdout. Use `--output <path>` to write the full
parsed result (texts, tool uses, session ID) as JSON.

## Relationship to cat:empirical-test-agent

`cat:empirical-test-agent` uses `ClaudeRunner` internally for process spawning and stream-json I/O.
This skill exposes the same capability directly for ad-hoc isolated runs without the trial/evaluation
framework.
