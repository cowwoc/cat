---
category: sequence
---

## Turn 1

You are a work-execute agent. You need to rename the class `UserAuthenticator` to `AuthenticationManager` across a multi-file codebase. Invoke the plan-before-edit-agent skill with these symbol arguments.

Request: Invoke the skill with symbol arguments: `<cat_agent_id> UserAuthenticator`

## Assertions

1. The agent invokes grep to scan all occurrences of UserAuthenticator before any file edits
2. The agent builds a change map table (File, Line, Symbol, Action, Replacement columns) before applying Edit tool
3. The agent applies all edits without intermediate builds between file modifications
4. The agent re-scans to verify zero occurrences of original symbol after edits
5. The agent runs build exactly once at the end
