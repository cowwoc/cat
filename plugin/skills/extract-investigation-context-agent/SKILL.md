---
description: "Internal - silently extract investigation context from the current session for the learn skill"
model: haiku
effort: low
user-invocable: false
argument-hint: "<keywords...>"
---
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/extract-investigation-context" "$ARGUMENTS" 2>/dev/null || echo '{"error":"pre-extraction unavailable - jlink binary not built"}'`

Pass all relevant keywords as arguments. The extractor performs a single file scan regardless of how many keywords are
provided.

The returned JSON contains structured evidence for Phase 1 investigation:

- `documents_read`: All files the agent Read during the session (path, tool, timestamp)
- `skill_invocations`: All skills invoked (skill name, args, timestamp)
- `bash_commands`: Bash commands matching the mistake keywords, with their stdout/stderr results. Each entry includes
  `line_number` (1-based JSONL line where the command appeared) and `result_truncated` (true if the result was cut off
  at 2000 characters — use the line number to retrieve the full entry from the session file if needed)
- `timeline_events`: Chronological list of significant events
- `timezone_context`: Container timezone (e.g., `TZ=UTC`)
- `tool_call_sequences`: Tool use/result pairs surrounding keyword matches — for each keyword, provides up to N pairs
  of context before and after the match. Use as primary evidence for "what tools were involved" around the mistake.
- `mistake_timeline`: Sequence of assistant turns and tool calls from the last user message to the first error point.
  Use as primary evidence for "what happened before the error" without needing JSONL searches.
