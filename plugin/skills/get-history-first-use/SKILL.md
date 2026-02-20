---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

# Get History Skill

**Purpose**: Access raw conversation history for debugging and analysis.

**Location**: `/home/node/.config/claude/projects/-workspace/*.jsonl`

**Session ID**: Available as `${CLAUDE_SESSION_ID}`.

## Structured Query Tool (Preferred)

Use the Java `session-analyzer` tool for structured queries. It handles mega-line JSONL correctly by parsing
the JSON structure rather than treating lines as text.

```bash
SESSION_ANALYZER="${CLAUDE_PROJECT_DIR}/client/target/jlink/bin/session-analyzer"
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"

# Search for keyword with 2 lines of surrounding context
"$SESSION_ANALYZER" search "$SESSION_FILE" "keyword" --context 2

# List all tool errors (non-zero exit codes and error patterns)
"$SESSION_ANALYZER" errors "$SESSION_FILE"

# Trace all reads/writes/edits to a file path
"$SESSION_ANALYZER" file-history "$SESSION_FILE" "config.json"

# Full session analysis (tool frequency, cache/batch/parallel candidates)
"$SESSION_ANALYZER" analyze "$SESSION_FILE"
```

Output is structured JSON, suitable for further processing or direct inspection.

## Subcommand Reference

| Subcommand | Arguments | Description |
|------------|-----------|-------------|
| `analyze` | `<file>` | Full session analysis (default when no subcommand given) |
| `search` | `<file> <keyword> [--context N]` | Find entries containing keyword with N context lines |
| `errors` | `<file>` | List tool_result entries with error indicators |
| `file-history` | `<file> <path-pattern>` | Chronological list of Read/Write/Edit/Bash ops on a file |

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
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"

# Search for agentId references in parent session
"$SESSION_ANALYZER" search "$SESSION_FILE" "agentId"
```

**Verifying what tools a subagent actually used:**
```bash
AGENT_ID="ad630cb"  # Example agentId
PARENT_SESSION="${CLAUDE_SESSION_ID}"
SUBAGENT_FILE="/home/node/.config/claude/projects/-workspace/$PARENT_SESSION/subagents/agent-$AGENT_ID.jsonl"

# Full analysis of subagent session
"$SESSION_ANALYZER" analyze "$SUBAGENT_FILE"

# Search for specific skill invocation
"$SESSION_ANALYZER" search "$SUBAGENT_FILE" "compare-docs"
```

**Note:** The agentId is included in the Task tool result output. Look for patterns like:
- `"agentId":"ad630cb"` (in JSON)
- `agentId: ad630cb` (in text output)

## Error Handling

If session ID not in context, report error - do NOT guess.
