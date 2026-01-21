---
name: get-session-id
description: Reference for accessing session ID via ${CLAUDE_SESSION_ID} automatic substitution
---

# Get Session ID Skill

**Purpose**: The session ID is automatically available in skills and commands via `${CLAUDE_SESSION_ID}`.

**How It Works**:
- Claude Code substitutes `${CLAUDE_SESSION_ID}` with the actual session ID when loading skills
- No manual extraction or lookup required
- The session ID is also visible in context via SessionStart hooks for user reference

**When to Use This Skill**:
- Reference this documentation to understand how session IDs work
- The session ID is already available - just use `${CLAUDE_SESSION_ID}` in skill/command templates

## How Session IDs Work

Claude Code assigns a unique session ID (UUID v4) to each conversation session. This ID is used for:
- Naming conversation history files: `/home/node/.config/projects/-workspace/{session-id}.jsonl`
- Session-specific TODO list tracking
- Hook coordination across tools
- Task ownership in multi-instance scenarios

## Automatic Substitution in Skills

In skill and command markdown files, use `${CLAUDE_SESSION_ID}` directly:

```bash
# This gets auto-substituted when the skill loads
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"
cat "$SESSION_FILE" | jq -s 'length'
```

## Hooks and Scripts

Bash hooks receive the session ID via stdin JSON:

```bash
# Read stdin JSON and extract session_id
INPUT=$(cat)
SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty')

# Use for session-specific operations
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${SESSION_ID}.jsonl"
```

## User Visibility

The session ID is injected into conversation context via two mechanisms:
- `echo-session-id.sh`: Outputs session ID at SessionStart
- `inject-session-instructions.sh`: Includes session ID in CAT instructions

Users see the session ID in system reminders at conversation start:
```
Session ID: b6933609-ab67-467e-af26-e48c3c8c129e
```

## Usage Examples

### Example 1: In Skill Templates

```bash
# Session ID is auto-substituted - no manual lookup needed
cat /home/node/.config/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl | jq -s 'length'
```

### Example 2: Access Session History

```bash
# The session ID is already substituted when this runs
jq -s '[.[] | select(.type == "message")]' \
  "/home/node/.config/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"
```

### Example 3: In Hook Scripts (stdin JSON)

```bash
#!/bin/bash
INPUT=$(cat)
SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty')
# Now use $SESSION_ID for session-specific operations
```

## Related

- **echo-session-id.sh**: Hook that outputs session ID for user visibility
- **inject-session-instructions.sh**: Hook that includes session ID in CAT instructions
- **get-history**: Skill that uses session ID to access conversation
