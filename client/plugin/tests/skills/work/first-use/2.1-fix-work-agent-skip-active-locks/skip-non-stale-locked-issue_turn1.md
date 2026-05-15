You are executing the work-agent skill. work-prepare was called with ARGUMENTS="2.1" and returned:

```json
{
  "status": "ERROR",
  "message": "Issue 2.1-some-issue has an existing worktree locked by another session",
  "issue_id": "2.1-some-issue",
  "locked_by": "other-session-id-abc123",
  "lock_age_seconds": 60,
  "worktree_path": "/some/worktree/path",
  "stale": false
}
```

If `locked_by` is present and `stale` is `false`, silently call work-prepare again with the same
ARGUMENTS. Do not display the error. Do not present a structured user-choice prompt. Stop immediately after the retry.
