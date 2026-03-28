# Plan

## Goal

Update work-agent to skip non-stale locked issues and find the next available issue automatically,
silently. work-prepare should expose a `stale: bool` field in its ERROR response (based on
IssueLock.STALE_LOCK_THRESHOLD = 4 hours), so work-agent can decide whether to skip or offer cleanup
without duplicating the threshold.

When work-prepare returns ERROR with an existing worktree locked by another session:
- If `stale == false` (lock_age_seconds < 14400): work-agent silently retries work-prepare to find
  the next available issue, without presenting any AskUserQuestion dialog
- If `stale == true` (lock_age_seconds >= 14400): work-agent preserves the existing cleanup-offer
  behavior (Clean up and retry / Abort)

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: work-agent no longer offers cleanup when encountering a non-stale locked issue
- [ ] work-agent automatically retries work-prepare to find the next available issue when it receives
      ERROR with `stale == false`, without presenting any AskUserQuestion dialog
- [ ] work-prepare ERROR response (existing worktree locked by another session) includes a boolean
      `stale` field, set to `true` when `lock_age_seconds >= 14400`, `false` otherwise
- [ ] Staleness threshold sourced exclusively from `IssueLock.STALE_LOCK_THRESHOLD` in Java — no
      hardcoded `14400` appears in skill Markdown code
- [ ] Regression test added: work-agent skips non-stale locked issues and finds next available issue
- [ ] Regression test added: work-agent preserves the cleanup-offer behavior when `stale == true`
- [ ] No new issues introduced
- [ ] E2E verification: reproduce the scenario (work-prepare returns ERROR with `stale == false`) and
      confirm work-agent automatically finds the next available issue without any dialog
