<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Get Codex History

Use the CAT environment bootstrap from `plugin/rules/codex/cat-environment.md`, then read
`${CAT_PLUGIN_ROOT}/concepts/session-history.md` before running history commands.

Codex session logs live under `$CODEX_HOME/sessions/YYYY/MM/DD/rollout-...<thread-id>.jsonl`, or under
`~/.codex/sessions/YYYY/MM/DD/rollout-...<thread-id>.jsonl` when `CODEX_HOME` is unset.
The current Codex thread ID is `${CODEX_THREAD_ID}`.

```bash
<!-- cat:include ../../include/codex-home-bootstrap.md -->
if [ -z "${CAT_PLUGIN_ROOT:-}" ]; then
  echo "CAT_PLUGIN_ROOT is required" >&2
  exit 1
fi
SESSION_ANALYZER="${CAT_PLUGIN_ROOT}/client/bin/session-analyzer"

"$SESSION_ANALYZER" --runtime codex search "${CODEX_THREAD_ID}" "keyword" --context 2
"$SESSION_ANALYZER" --runtime codex errors "${CODEX_THREAD_ID}"
"$SESSION_ANALYZER" --runtime codex file-history "${CODEX_THREAD_ID}" "config.json"
"$SESSION_ANALYZER" --runtime codex analyze "${CODEX_THREAD_ID}"
```

Use `--codex-home <path>` to inspect an isolated Codex home:

```bash
"$SESSION_ANALYZER" --runtime codex --codex-home "$ISOLATION_CODEX_HOME" analyze "$SESSION_ID"
```

## Codex Agents

Codex stores each session as a rollout JSONL file. Agent rollout metadata records the parent thread ID, so parent
analysis discovers child threads and includes them under `agents`.

```bash
"$SESSION_ANALYZER" --runtime codex analyze "${CODEX_THREAD_ID}"
```

To inspect one specific Codex agent directly, pass that agent's thread ID or explicit rollout JSONL path:

```bash
SUBAGENT_THREAD_ID="019e11a1-de6d-7403-90ff-d508f737ce64"
"$SESSION_ANALYZER" --runtime codex analyze "$SUBAGENT_THREAD_ID"
"$SESSION_ANALYZER" --runtime codex search "$SUBAGENT_THREAD_ID" "instruction-builder" --context 2
```

If the thread ID is unknown, locate candidate rollout files by time or text unique to the agent prompt/output:

```bash
CODEX_SESSIONS="${CODEX_HOME:-$HOME/.codex}/sessions"
find "$CODEX_SESSIONS" -type f -name 'rollout-*.jsonl' -print | sort
grep -RIl "unique text from the agent task" "$CODEX_SESSIONS"

SUBAGENT_TRANSCRIPT="$CODEX_SESSIONS/YYYY/MM/DD/rollout-...jsonl"
"$SESSION_ANALYZER" --runtime codex analyze "$SUBAGENT_TRANSCRIPT"
```
