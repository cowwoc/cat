<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Run Engine

Launch a nested Claude engine process for one-off validation.

<!-- cat:include ../../common/spawn-engine/first-use.md -->

## Run

```bash
WORKTREE_PATH=$(git rev-parse --show-toplevel)
PLUGIN_SOURCE="${WORKTREE_PATH}/plugin"
JLINK_BIN="${WORKTREE_PATH}/client/distribution/target/jlink/claude/bin"
if [[ ! -d "$JLINK_BIN" ]]; then
  JLINK_BIN="${CLAUDE_PROJECT_DIR}/client/distribution/target/jlink/claude/bin"
fi

PROMPT_FILE=$(mktemp /tmp/spawn-engine-claude-prompt.XXXXXX)
echo "${PROMPT_TEXT:?PROMPT_TEXT is required}" > "$PROMPT_FILE"

"${CLAUDE_PLUGIN_ROOT}/client/bin/claude-runner" \
  --prompt-file "$PROMPT_FILE" \
  --model "${MODEL:?MODEL is required}" \
  --effort "${EFFORT:?EFFORT is required}" \
  --plugin-source "$PLUGIN_SOURCE" \
  --jlink-bin "$JLINK_BIN" \
  --cwd "$WORKTREE_PATH" \
  --output "${OUTPUT_FILE:?OUTPUT_FILE is required}"

rm -f "$PROMPT_FILE"
```

Use this skill for empirical one-off checks. Formal automated testing goes through `cat:sprt-runner`.
