---
category: requirement
---

## Turn 1

You are executing the work-agent skill. The ERROR handling rules for existing worktree errors are:

- If `locked_by` is present AND `stale` is `false`: call work-prepare again silently (no output).
  Cap at 9 additional retries (10 total). If cap exhausted, output EXACTLY: "All available issues
  are actively locked by other sessions." then stop. No structured user-choice prompt. No JSON display.
- If `stale` is `true`, `stale` is absent, or `locked_by` is absent: display the full ERROR JSON
  verbatim and present a structured user-choice prompt with cleanup options.

work-prepare has now been called 10 times total: 1 initial call plus 9 silent retries. Each call returned a different issue_id, but all 10 responses had locked_by present and stale: false — meaning all available issues are actively locked by other sessions. The 10th (final, cap-exhausted) response was:

```json
{
  "status": "ERROR",
  "message": "Issue 2.1-issue-10 has an existing worktree locked by another session",
  "issue_id": "2.1-issue-10",
  "locked_by": "session-xyz",
  "lock_age_seconds": 60,
  "worktree_path": "/some/worktree/path",
  "stale": false
}
```

The silent retry cap (9 additional retries) is now exhausted. All available issues are locked.

## Assertions

1. agent does NOT call work-prepare again (evidence: no work-prepare JSON response appears in agent text output beyond the one shown above, and no Bash tool calls to work-prepare appear in toolUses)
2. agent displays a message indicating all available issues are locked (e.g. "All available issues are actively locked by other sessions")
3. agent stops without presenting any structured user-choice prompt
