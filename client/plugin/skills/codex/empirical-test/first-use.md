<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Empirical Test

Run controlled compliance experiments against the current runtime.

Use `empirical-test-runner --runtime codex` and select the model and effort explicitly. Append-system-prompt is
optional supplemental instruction text; do not copy project instructions into it because the nested runtime loads its
normal project context by itself.

## Run

```bash
if [ -z "${CAT_PLUGIN_DATA:-}" ]; then
  echo "CAT_PLUGIN_DATA is required" >&2
  exit 1
fi
RUNNER="${CAT_PLUGIN_DATA}/client/bin/empirical-test-runner"
"$RUNNER" \
  --runtime codex \
  --model "${MODEL:?MODEL is required}" \
  --effort "${EFFORT:?EFFORT is required}" \
  --prompt-file "${PROMPT_FILE:?PROMPT_FILE is required}" \
  --output "${OUTPUT_FILE:?OUTPUT_FILE is required}"
```

## Inspect

```bash
if [ -z "${CAT_PLUGIN_DATA:-}" ]; then
  echo "CAT_PLUGIN_DATA is required" >&2
  exit 1
fi
SESSION_ANALYZER="${CAT_PLUGIN_DATA}/client/bin/session-analyzer"
"$SESSION_ANALYZER" --runtime codex analyze "${TRIAL_SESSION_ID:?TRIAL_SESSION_ID is required}"
"$SESSION_ANALYZER" --runtime codex errors "${TRIAL_SESSION_ID:?TRIAL_SESSION_ID is required}"
"$SESSION_ANALYZER" --runtime codex search "${TRIAL_SESSION_ID:?TRIAL_SESSION_ID is required}" "keyword" --context 5
```

Treat results as evidence for this runtime only. If behavior must be compared with another runtime, run that runtime's
own `cat:empirical-test` skill separately and compare the output files outside this skill invocation.
