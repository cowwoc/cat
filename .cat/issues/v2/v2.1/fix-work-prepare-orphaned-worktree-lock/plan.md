## Goal

Fix two bugs in the orphaned worktree recovery path:

1. **Redundant skill-level lock verification in `work-implement-agent`:** Step 2 reads the lock file and compares
   `session_id` against `$CLAUDE_SESSION_ID`. This is redundant because the Java CLI already verifies lock ownership
   before returning READY. It is also incorrect because `$CLAUDE_SESSION_ID` may reflect a previous session when
   using the env-file workaround for upstream bug #24775.

2. **Incorrect lock acquisition in `work-prepare-agent` Orphaned Worktree Recovery Protocol:** Step 4 calls
   `issue-lock acquire "${issue_id}" "${CLAUDE_SESSION_ID}"` directly. When the existing lock belongs to a different
   session, `IssueLock.acquire()` returns LOCKED (not acquired), so the lock transfer silently fails. The correct
   approach is to call `work-prepare --arguments "resume ${issue_id}"`, which triggers
   `forceResumeWithExistingWorktree()` — force-releases the old lock and acquires a new one for the current session,
   then returns READY with the full worktree context.

## Post-conditions

- [ ] `work-implement-agent/first-use.md` Step 2 (Verify Lock Ownership bash block) is removed; all subsequent steps
  are renumbered sequentially (Step 3→2, Step 4→3, Step 5→4); the skill header description no longer mentions
  "verifies lock ownership"
- [ ] `work-prepare-agent/first-use.md` Orphaned Worktree Recovery Protocol step 4 calls
  `work-prepare --arguments "resume ${issue_id}"` instead of
  `issue-lock acquire "${issue_id}" "${CLAUDE_SESSION_ID}"`
- [ ] When `work-prepare` returns READY after resume, the protocol reads `worktree_path` from the JSON response
  rather than constructing it independently; `has_existing_work: true` is forwarded from that response
- [ ] All existing tests pass (`mvn -f client/pom.xml verify -e`)
