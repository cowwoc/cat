# Get History Skill

**Purpose**: Access raw conversation history for debugging and analysis.

**Location**: `/home/node/.config/claude/projects/-workspace/*.jsonl`

**Session ID**: Available as `${CLAUDE_SESSION_ID}`.

## Efficient Search Techniques

**Problem**: JSONL lines contain entire conversation turns (megabytes each). Raw grep returns unusable output.

**Solution**: Use `-o` flag to extract only matching patterns, not full lines:

```bash
HIST="/home/node/.config/claude/projects/-workspace"

# Count occurrences of specific IDs/values (most efficient)
grep -oh 'EXACT_PATTERN' "$HIST"/*.jsonl 2>/dev/null | sort | uniq -c | sort -rn

# Extract variable assignments
grep -oh 'VAR_NAME\s*=\s*"[^"]*"' "$HIST"/*.jsonl 2>/dev/null | sort -u

# Extract JSON field values
grep -oh '"field_name":\s*"[^"]*"' "$HIST"/*.jsonl 2>/dev/null | sort -u

# Extract markdown tables (split \n first, then filter)
sed 's/\\n/\n/g' "$HIST"/*.jsonl 2>/dev/null | grep -oE '\|[^|]+\|[^|]+\|[^\\]*' | sort -u | head -50
```

## Common Search Patterns

```bash
HIST="/home/node/.config/claude/projects/-workspace"

# Find specific known IDs (most reliable - list IDs you're looking for)
grep -oh 'IKne3meq5aSn9XLyUdCD\|onwK4e9ZLuTAKqWW03F9\|TxGEqnHWrfWFTfGW9XjX' "$HIST"/*.jsonl 2>/dev/null | sort | uniq -c | sort -rn

# Extract name-to-ID mappings from markdown tables
perl -pe 's/\\n/\n/g' "$HIST"/*.jsonl 2>/dev/null | grep -oE '\| *[A-Z][a-z]+ *\|[^|]*\| *`?[A-Za-z0-9]{20,22}`? *\|' | sort -u

# Count tool invocations by name
grep -oh '"name":\s*"[A-Za-z]*"' "$HIST"/*.jsonl 2>/dev/null | sort | uniq -c | sort -rn | head -20

# Find discussions with context (truncate long lines)
perl -pe 's/\\n/\n/g' "$HIST"/*.jsonl 2>/dev/null | grep -i "keyword" | grep -v "node_modules\|base64" | cut -c1-200 | head -30

# Extract code variable assignments
grep -oE '(VOICE_ID|API_KEY|CONFIG)\s*=\s*"[^"]*"' "$HIST"/*.jsonl 2>/dev/null | sort -u
```

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
HIST="/home/node/.config/claude/projects/-workspace"
PARENT_SESSION="your-parent-session-id-here"

# Extract agentIds from parent session's Task tool results
grep -oh '"agentId":"[^"]*"' "$HIST/$PARENT_SESSION.jsonl" 2>/dev/null | sort -u

# Or from raw output (agentId appears in result text)
grep -oh 'agentId: [a-f0-9]*' "$HIST/$PARENT_SESSION.jsonl" 2>/dev/null | sort -u
```

**Verifying what tools a subagent actually used:**
```bash
AGENT_ID="ad630cb"  # Example agentId
SUBAGENT_FILE="$HIST/$PARENT_SESSION/subagents/agent-$AGENT_ID.jsonl"

# Count tool invocations
grep -oh '"name":"[^"]*"' "$SUBAGENT_FILE" 2>/dev/null | sort | uniq -c | sort -rn

# Check for specific skill invocation
grep -c '"name":"Skill"' "$SUBAGENT_FILE" 2>/dev/null
grep -oh '"skill":"[^"]*"' "$SUBAGENT_FILE" 2>/dev/null | sort -u
```

**Use case: Verify subagent actually invoked a validation skill:**
```bash
# Check if subagent invoked /cat:compare-docs or /cat:shrink-doc
grep '"name":"Skill"' "$SUBAGENT_FILE" 2>/dev/null | grep -E 'compare-docs|shrink-doc'
```

**Note:** The agentId is included in the Task tool result output. Look for patterns like:
- `"agentId":"ad630cb"` (in JSON)
- `agentId: ad630cb` (in text output)

## Error Handling

If session ID not in context, report error - do NOT guess.
