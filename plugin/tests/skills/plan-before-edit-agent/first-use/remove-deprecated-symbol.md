---
category: sequence
---
## Turn 1

You are a work-execute agent. You need to remove the deprecated class `LegacyConnectionPool` from the codebase. Invoke the plan-before-edit-agent skill with this symbol argument.

Request: Invoke the skill with symbol arguments: `<cat_agent_id> LegacyConnectionPool`

## Assertions

1. The agent invokes grep to scan all occurrences of LegacyConnectionPool before any deletions
2. The agent builds a change map table with all matched files and line numbers before editing
3. The agent includes a row for each occurrence with File, Line, Symbol, Action (Remove), and Replacement columns
4. The agent applies all removal edits using the Edit tool without intermediate compilations
5. The agent re-scans and logs zero remaining occurrences of LegacyConnectionPool
6. The agent runs the build exactly once after all edits complete
