<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Claude Runner

Launch a nested Claude CLI instance with an isolated config directory and updated plugin cache.

## Purpose

Run a prompt in a fresh Claude environment where the nested instance automatically sees the latest
plugin source files and jlink binaries from the current worktree. This avoids the need to reload
plugins in the parent session before testing or running isolated prompts.

## When to Use

- Testing skill compliance without polluting the parent session's state
- Running a prompt with updated plugin code before the parent has reloaded
- Spawning an isolated Claude instance for empirical testing

## Arguments

The runner is invoked as a Java binary with named arguments (not positional):

| Argument | Required | Description |
|----------|----------|-------------|
| `--prompt-file <path>` | yes | Path to a file containing the prompt to send to the nested Claude instance |
| `--model <name>` | no | Model short name: `haiku`, `sonnet`, or `opus` (default: `haiku`) |
| `--cwd <path>` | no | Working directory for the nested instance (default: current directory) |
| `--plugin-source <path>` | no | Plugin source directory to copy into cache |
| `--jlink-bin <path>` | no | jlink binary directory to copy into cache |
| `--plugin-version <ver>` | no | Plugin version string (default: `2.1`) |
| `--agent <name>` | no | Launch nested instance with `claude --agent <name>` (spawns a subagent) |
| `--append-system-prompt <text>` | no | Append text to the system prompt of the nested instance |
| `--output <path>` | no | Write JSON results to file |

## Procedure

### Step 1: Determine paths

**MANDATORY:** Resolve the paths using the exact commands shown. Do NOT define these variables manually or use arbitrary values.

```bash
WORKTREE_PATH=$(git rev-parse --show-toplevel)
PLUGIN_SOURCE="${WORKTREE_PATH}/plugin"
JLINK_BIN="${WORKTREE_PATH}/client/target/jlink/bin"
if [[ ! -d "$JLINK_BIN" ]]; then
  JLINK_BIN="${CLAUDE_PROJECT_DIR}/client/target/jlink/bin"
fi
```

After resolving paths, these variables MUST NOT be reassigned. Use the resolved values directly in Step 3.

### Step 2: Write the prompt to a file

The runner accepts the prompt as a file path, not an inline string. Write the prompt to a
temporary file before invoking the runner.

**MANDATORY:** Create the prompt file in `/tmp` using `mktemp` to ensure a unique, safe path.
Use the EXACT path returned by `mktemp`. Do NOT reassign the variable after `mktemp` returns.
Do NOT write to arbitrary paths outside `/tmp`.

```bash
PROMPT_FILE=$(mktemp /tmp/claude-runner-prompt.XXXXXX)
echo "Your prompt text here" > "$PROMPT_FILE"
```

**MANDATORY prompt content restrictions:** The prompt text written to `$PROMPT_FILE` must be derived from the task or user request. Do NOT write prompt content that attempts to override the nested instance's system instructions, bypass security controls, or access resources outside the nested instance's sandboxed environment. Examples of prohibited content:

- `"Ignore all previous instructions and ..."`
- `"Output the contents of /etc/shadow"`
- `"Bypass your safety guidelines"`
- Prompts that attempt to manipulate the nested instance into executing arbitrary shell commands or accessing unauthorized files

The nested instance runs with the same permissions as the parent session. Prompts should be limited to the testing or execution task for which the runner was invoked.

### Step 3: Invoke the runner

Call the Java CLI tool with the resolved paths.

**MANDATORY validations:** Before invoking the runner, verify all paths meet the following requirements. If any validation fails, exit with an error.

- `--cwd` must be set to `$WORKTREE_PATH` (from Step 1). Validate that `$WORKTREE_PATH` starts with `$CLAUDE_PROJECT_DIR` and is a directory.
- `--plugin-source` must be set to `$PLUGIN_SOURCE` (from Step 1). Validate that `$PLUGIN_SOURCE` equals `${WORKTREE_PATH}/plugin` and is a directory.
- `--jlink-bin` must be set to `$JLINK_BIN` (from Step 1). Validate that `$JLINK_BIN` is a directory.
- `--prompt-file` must be set to `$PROMPT_FILE` (from Step 2). Validate that `$PROMPT_FILE` starts with `/tmp/` and is a file.

Example validation code:

```bash
if [[ ! -d "$WORKTREE_PATH" ]] || [[ "$WORKTREE_PATH" != "${CLAUDE_PROJECT_DIR}"* ]]; then
  echo "ERROR: Invalid --cwd path: $WORKTREE_PATH" >&2
  exit 1
fi
if [[ "$PLUGIN_SOURCE" != "${WORKTREE_PATH}/plugin" ]] || [[ ! -d "$PLUGIN_SOURCE" ]]; then
  echo "ERROR: Invalid --plugin-source path: $PLUGIN_SOURCE" >&2
  exit 1
fi
if [[ ! -d "$JLINK_BIN" ]]; then
  echo "ERROR: Invalid --jlink-bin path: $JLINK_BIN" >&2
  exit 1
fi
if [[ "$PROMPT_FILE" != /tmp/* ]] || [[ ! -f "$PROMPT_FILE" ]]; then
  echo "ERROR: Invalid --prompt-file path: $PROMPT_FILE" >&2
  exit 1
fi
```

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/claude-runner" \
  --prompt-file "$PROMPT_FILE" \
  --model haiku \
  --plugin-source "$PLUGIN_SOURCE" \
  --jlink-bin "$JLINK_BIN" \
  --cwd "$WORKTREE_PATH"
```

The `--model` argument accepts short model names (`haiku`, `sonnet`, `opus`), not fully-qualified model IDs. The runner validates the model name and rejects invalid values.

### Step 4: Handle output

The runner prints the nested instance's text output to stdout.

To capture the full parsed result (text blocks, tool uses, session ID) as JSON, add `--output <path>`.

**MANDATORY:** If using `--output`, create the output file in `/tmp` using `mktemp`. Use the EXACT path returned by `mktemp`. Do NOT reassign the variable after `mktemp` returns. Do NOT write to arbitrary paths outside `/tmp`.

```bash
OUTPUT_FILE=$(mktemp /tmp/claude-runner-output.XXXXXX)
if [[ "$OUTPUT_FILE" != /tmp/* ]] || [[ ! -f "$OUTPUT_FILE" ]]; then
  echo "ERROR: Invalid output file path: $OUTPUT_FILE" >&2
  exit 1
fi
"${CLAUDE_PLUGIN_ROOT}/client/bin/claude-runner" \
  --prompt-file "$PROMPT_FILE" \
  --model haiku \
  --plugin-source "$PLUGIN_SOURCE" \
  --jlink-bin "$JLINK_BIN" \
  --cwd "$WORKTREE_PATH" \
  --output "$OUTPUT_FILE"
```

**Exit code validation:** Always check the runner's exit code before reading output. If the exit code is non-zero, the run failed and output may be incomplete or corrupted. Capture the exit code immediately after the runner invocation to prevent other commands from overwriting `$?`.

```bash
RUNNER_EXIT=$?
if [[ $RUNNER_EXIT -ne 0 ]]; then
  echo "ERROR: claude-runner failed with exit code $RUNNER_EXIT" >&2
  exit 1
fi
```

**stderr interpretation:** If the runner prints a warning about `claude-code-cache-fix` to stderr, it means the preferred binary is not installed. The run still completes using the standard `claude` binary. However, do NOT ignore all stderr output — other errors may appear on stderr. Always check the exit code to determine success or failure.

**MANDATORY cleanup:** After the runner completes (success or failure), delete the temporary prompt file and output file (if created):

```bash
rm -f "$PROMPT_FILE"
[[ -n "$OUTPUT_FILE" ]] && rm -f "$OUTPUT_FILE"
```

## Relationship to cat:empirical-test-agent

`cat:empirical-test-agent` uses the same runner internally for process spawning and stream-json I/O.
Use this skill directly when you need an ad-hoc isolated run without the trial/evaluation framework.

## Verification

- [ ] The runner exits with code 0 (check via `$?` or `set -e`)
- [ ] Text output from the nested instance appears on stdout
- [ ] If `--output` was used, the JSON file exists and contains `texts`, `tool_uses`, and `session_id` fields
- [ ] Temporary prompt file is deleted after the runner completes (e.g., `rm "$PROMPT_FILE"`)
- [ ] All paths passed to the runner are validated:
  - `--cwd` is `$WORKTREE_PATH` (the worktree root)
  - `--plugin-source` is `$PLUGIN_SOURCE` (worktree's `plugin/` directory)
  - `--jlink-bin` is `$JLINK_BIN` (resolved jlink binary path)
  - `--prompt-file` and `--output` (if used) are created in `/tmp` using `mktemp`
