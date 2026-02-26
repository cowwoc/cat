---
description: "Internal - silently extract investigation context from the current session for the learn skill"
model: haiku
user-invocable: false
argument-hint: "<keywords...>"
---
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/extract-investigation-context" "/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl" $ARGUMENTS 2>/dev/null || echo '{"error":"pre-extraction unavailable - jlink binary not built"}'`
