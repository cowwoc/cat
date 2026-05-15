---
category: requirement
---

## Turn 1

You are executing the work-agent skill. The ERROR handling rules for existing worktree errors are:

- If `locked_by` is present AND `stale` is `false`: call work-prepare again silently (no output).
- If `stale` is `true`, `stale` is absent, or `locked_by` is absent: output the full ERROR JSON
  object verbatim as a ```json code block (starting with `{`, ending with `}`, ALL fields
  included), then present a structured user-choice prompt with "Clean up and retry" / "Abort" options.

work-prepare returned the following ERROR response:
```json
{
  "status": "ERROR",
  "message": "Issue 2.1-some-issue has an existing worktree locked by another session",
  "issue_id": "2.1-some-issue",
  "locked_by": "other-session-id-abc123",
  "lock_age_seconds": 20000,
  "worktree_path": "/some/worktree/path",
  "stale": true
}
```

## Assertions

1. agent displays the ERROR response verbatim to the user
2. agent presents a structured user-choice prompt (offering user options for how to proceed)
3. agent does NOT silently retry work-prepare without user input
