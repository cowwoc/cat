<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Run Engine

Launch a nested Codex engine process for one-off validation.

<!-- cat:include ../../common/spawn-engine/first-use.md -->

## Run

```bash
<!-- cat:include ../../include/codex-home-bootstrap.md -->
if [ -z "${CAT_PLUGIN_ROOT:-}" ] || [ -z "${CAT_PLUGIN_DATA:-}" ]; then
  echo "CAT_PLUGIN_ROOT and CAT_PLUGIN_DATA are required" >&2
  exit 1
fi

WORKTREE_PATH=$(git rev-parse --show-toplevel)
PROMPT_FILE=$(mktemp /tmp/spawn-engine-codex-prompt.XXXXXX)
echo "${PROMPT_TEXT:?PROMPT_TEXT is required}" > "$PROMPT_FILE"

"${CAT_PLUGIN_ROOT}/client/bin/codex-runner" \
  --prompt-file "$PROMPT_FILE" \
  --cwd "$WORKTREE_PATH" \
  --model "${MODEL:?MODEL is required}" \
  --effort "${EFFORT:?EFFORT is required}" \
  --output "${OUTPUT_FILE:?OUTPUT_FILE is required}"

rm -f "$PROMPT_FILE"
```

Use this skill for empirical one-off checks. Formal automated testing goes through `cat:sprt-runner`.
