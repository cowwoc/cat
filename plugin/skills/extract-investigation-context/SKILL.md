---
description: "Silently extract investigation context from the current session for the learn skill"
user-invocable: false
---
!`"/workspace/client/target/jlink/bin/extract-investigation-context" "/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl" $ARGUMENTS 2>/dev/null || echo '{"error":"pre-extraction unavailable - jlink binary not built"}'`
