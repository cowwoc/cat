<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Get History Skill

**Purpose**: Access raw conversation history for debugging and analysis.

**Location**: `/home/node/.config/claude/projects/-workspace/*.jsonl`

**Session ID**: Available as `${CLAUDE_SESSION_ID}`.

## Structured Query Tool (Preferred)

Use the Java `session-analyzer` tool for structured queries. It handles mega-line JSONL correctly by parsing
the JSON structure rather than treating lines as text.

```bash
SESSION_ANALYZER="${CLAUDE_PLUGIN_ROOT}/client/bin/session-analyzer"

# Search for keyword with 2 lines of surrounding context
"$SESSION_ANALYZER" search "${CLAUDE_SESSION_ID}" "keyword" --context 2

# List all tool errors (non-zero exit codes and error patterns)
"$SESSION_ANALYZER" errors "${CLAUDE_SESSION_ID}"

# Trace all reads/writes/edits to a file path
"$SESSION_ANALYZER" file-history "${CLAUDE_SESSION_ID}" "config.json"

# Full session analysis (tool frequency, cache/batch/parallel candidates)
"$SESSION_ANALYZER" analyze "${CLAUDE_SESSION_ID}"
```

Output is structured JSON, suitable for further processing or direct inspection.

## Subcommand Reference

| Subcommand | Arguments | Description |
|------------|-----------|-------------|
| `analyze` | `<session-id>` | Full session analysis (default when no subcommand given) |
| `search` | `<session-id> <keyword> [--context N]` | Find entries containing keyword with N context lines |
| `errors` | `<session-id>` | List tool_result entries with error indicators |
| `file-history` | `<session-id> <path-pattern>` | Chronological list of Read/Write/Edit/Bash ops on a file |

## Entry Types

- `type: "summary"` - Conversation summary
- `type: "message"` - User/assistant messages
- `type: "tool_use"` - Tool invocations
- `type: "tool_result"` - Tool outputs

## Subagent Session Navigation

Subagent sessions are stored in a subdirectory of the parent session, NOT at the root level.

**Storage path:**
```
{parent-session-id}/subagents/agent-{agentId}.jsonl
```

**Finding agentId from parent session:**
```bash
# Search for agentId references in parent session
"$SESSION_ANALYZER" search "${CLAUDE_SESSION_ID}" "agentId"
```

**Verifying what tools a subagent actually used:**
```bash
AGENT_ID="ad630cb"  # Example agentId

# Full analysis of subagent session (session-analyzer resolves the path internally)
"$SESSION_ANALYZER" analyze "${CLAUDE_SESSION_ID}/subagents/agent-$AGENT_ID"

# Search for specific skill invocation
"$SESSION_ANALYZER" search "${CLAUDE_SESSION_ID}/subagents/agent-$AGENT_ID" "compare-docs"
```

**Note:** The agentId is included in the Task tool result output. Look for patterns like:
- `"agentId":"ad630cb"` (in JSON)
- `agentId: ad630cb` (in text output)

## Error Handling

If session ID not in context, report error - do NOT guess.
