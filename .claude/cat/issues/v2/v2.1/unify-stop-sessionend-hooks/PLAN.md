# Plan: unify-stop-sessionend-hooks

## Current State
- Stop: java.sh EnforceStatusOutput (already Java)
- SessionEnd: session-unlock.sh (bash)

Stop hook is already Java. SessionEnd has 1 bash script that needs migration.

## Target State
New GetSessionEndOutput Java dispatcher for SessionEnd. Stop hook remains as-is (already Java).

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** session-unlock.sh handles lock file cleanup with stale lock detection
- **Mitigation:** Simple file operations; straightforward to port

## Files to Modify
- plugin/hooks/java/src/main/java/com/cat/hooks/GetSessionEndOutput.java - NEW: Dispatcher
- plugin/hooks/java/src/main/java/com/cat/hooks/session/SessionUnlock.java - NEW: Lock cleanup handler
- plugin/hooks/java/src/test/java/com/cat/hooks/session/SessionUnlockTest.java - NEW
- plugin/hooks/hooks.json - Replace session-unlock.sh with java.sh GetSessionEndOutput
- plugin/hooks/session-unlock.sh - DELETE

## Acceptance Criteria
- [ ] session-unlock.sh lock cleanup logic ported to Java
- [ ] Project lock removal preserved
- [ ] Task lock cleanup preserved
- [ ] Legacy worktree lock cleanup preserved
- [ ] Stale lock detection (24h) preserved
- [ ] hooks.json SessionEnd uses Java dispatcher
- [ ] session-unlock.sh deleted
- [ ] Tests pass

## Key Implementation Details
- session-unlock.sh removes .claude/cat/locks/${PROJECT_NAME}.lock
- Cleans task locks owned by current session
- Cleans legacy worktree locks
- Removes stale locks older than 24 hours
- Requires CLAUDE_PROJECT_DIR env var (fail-fast if missing)

## Execution Steps
1. Create SessionUnlock handler class
2. Create GetSessionEndOutput dispatcher
3. Write tests
4. Update hooks.json
5. Delete session-unlock.sh
6. Run full test suite

## Success Criteria
- [ ] All tests pass
- [ ] No bash SessionEnd scripts remain
- [ ] hooks.json SessionEnd entry uses java.sh