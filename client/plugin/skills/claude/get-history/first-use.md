<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Get Claude History

Read `${CAT_PLUGIN_ROOT}/concepts/session-history.md` before running history commands.

Claude session logs live under `${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/*.jsonl`.
The current CAT session ID is `${CAT_SESSION_ID}`.

```bash
SESSION_ANALYZER="${CAT_PLUGIN_DATA}/client/bin/session-analyzer"

"$SESSION_ANALYZER" --runtime claude search "${CAT_SESSION_ID}" "keyword" --context 2
"$SESSION_ANALYZER" --runtime claude errors "${CAT_SESSION_ID}"
"$SESSION_ANALYZER" --runtime claude file-history "${CAT_SESSION_ID}" "config.json"
"$SESSION_ANALYZER" --runtime claude analyze "${CAT_SESSION_ID}"
```

## Claude Subagents

Claude subagent sessions are stored under the parent session. Find `agentId` values in the parent session, then pass
the subagent path to `session-analyzer`:

```bash
"$SESSION_ANALYZER" --runtime claude search "${CAT_SESSION_ID}" "agentId"

AGENT_ID="ad630cb"
"$SESSION_ANALYZER" --runtime claude analyze "${CAT_SESSION_ID}/subagents/agent-${AGENT_ID}"
"$SESSION_ANALYZER" --runtime claude search "${CAT_SESSION_ID}/subagents/agent-${AGENT_ID}" "instruction-builder"
```

The `agentId` usually appears in Task tool results as `"agentId":"..."` or `agentId: ...`.
