<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Codex Runner

Launch a nested `codex exec` process from a prompt file through CAT's Java runner.

## Purpose

Run a prompt in a fresh non-interactive Codex process and capture parsed text, tool-use, write-content, and optional
raw JSONL output.

## Arguments

Pass both `MODEL` and `EFFORT` explicitly. The runner does not infer the parent Codex session's model or reasoning
effort.

| Argument | Required | Description |
|----------|----------|-------------|
| `PROMPT_FILE` | yes | Path to a file containing the prompt to send to the nested Codex instance |
| `MODEL` | yes | Codex model ID or alias accepted by `codex exec --model` |
| `EFFORT` | yes | Codex reasoning effort: `minimal`, `low`, `medium`, `high`, or `xhigh` |
| `WORKTREE_PATH` | no | Working directory for the nested instance. In this skill, default it to `git rev-parse --show-toplevel`; in the Java CLI, omitted `--cwd` uses the current CAT project path. |
| `OUTPUT_FILE` | no | Path for parsed runner JSON. If omitted, parsed JSON is not written to disk; text output is still printed to stdout. |
| `JSONL_FILE` | no | Path for full `--json` event output. If omitted, raw Codex JSONL events are not persisted. |

## Omitted Optional Arguments

- If `--cwd` is omitted, the Java runner uses the current CAT project path from the active Codex scope. This skill still passes `--cwd "$WORKTREE_PATH"` explicitly for reproducibility.
- If `--output` is omitted, the runner prints the final parsed text output to stdout but does not write parsed JSON to disk.
- If `--jsonl-output` is omitted, the full Codex `--json` event stream is parsed in memory but not written to disk.

## Procedure

### Step 1: Resolve paths

```bash
WORKTREE_PATH=$(git rev-parse --show-toplevel)
```

If the nested run must see updated CAT plugin code, ensure the Codex plugin installation points at the current
worktree's `client/plugin/` directory or publish/stage the matching release artifact before invoking this runner.
Codex local plugin source is resolved through the Codex plugin installation/marketplace layer.

### Step 2: Write the prompt

Create the prompt file under `.cat/work/tmp`:

```bash
mkdir -p .cat/work/tmp
PROMPT_FILE=$(mktemp -p .cat/work/tmp codex-runner-prompt-XXXXXX)
cat > "$PROMPT_FILE" <<'PROMPT'
Your prompt text here
PROMPT
```

The prompt must be derived from the task or user request. Do not include prompt-injection text that attempts to
override system instructions, bypass safety controls, or access unauthorized resources.

### Step 3: Run Codex

Build the client if the jlink image is not present:

```bash
CLIENT_BIN="${WORKTREE_PATH}/client/cli/target/jlink/bin"
if [[ ! -x "$CLIENT_BIN/codex-runner" ]]; then
  (cd "$WORKTREE_PATH/client/cli" && ./build-jlink.sh)
fi
```

Capture both parsed runner output and the raw JSONL event stream:

```bash
mkdir -p .cat/work/tmp
OUTPUT_FILE=$(mktemp -p .cat/work/tmp codex-runner-output-XXXXXX.json)
JSONL_FILE=$(mktemp -p .cat/work/tmp codex-runner-jsonl-XXXXXX.jsonl)

"$CLIENT_BIN/codex-runner" \
  --prompt-file "$PROMPT_FILE" \
  --cwd "$WORKTREE_PATH" \
  --model "$MODEL" \
  --effort "$EFFORT" \
  --output "$OUTPUT_FILE" \
  --jsonl-output "$JSONL_FILE"
RUNNER_EXIT=$?
if [[ $RUNNER_EXIT -ne 0 ]]; then
  echo "ERROR: codex-runner failed with exit code $RUNNER_EXIT" >&2
  exit $RUNNER_EXIT
fi
```

### Step 4: Inspect output

Parsed runner output is in `$OUTPUT_FILE`. The full execution event stream is in `$JSONL_FILE`.

```bash
jq . "$OUTPUT_FILE"
```

The parsed JSON includes `texts`, `toolUses`, `writeContents`, `turns`, and `sessionId`. Codex JSONL event names differ
from other runtime stream formats, so do not pass Codex JSONL to tools that expect a different runner output format.

### Step 5: Clean up

Remove temporary files unless the caller explicitly asked to keep them:

```bash
rm -f "$PROMPT_FILE" "$OUTPUT_FILE" "$JSONL_FILE"
```

## Relationship To Runtime Subagents

Runtime subagents and runners solve different problems:

- Runtime subagents use native Codex agent definitions and are the default for CAT orchestration.
- Runners spawn fresh CLI processes for empirical or isolated validation and are not a substitute for native
  subagent delegation.

## Relationship To Other Runners

The two runners are not output-compatible:

- `cat:codex-runner` invokes the Java `codex-runner` binary, which wraps `codex exec --json` and emits parsed JSON.
- Other runtime runners may expose similar fields but use different raw event formats.

## Verification

- [ ] `codex-runner` exits with code 0
- [ ] `$OUTPUT_FILE` contains parsed runner JSON
- [ ] `$JSONL_FILE` contains Codex JSONL events
- [ ] Temporary files are deleted unless intentionally retained
