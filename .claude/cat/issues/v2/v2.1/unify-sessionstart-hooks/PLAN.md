# Plan: unify-sessionstart-hooks

## Current State
7 separate SessionStart hooks each spawn their own process:
1. check-upgrade.sh - Version upgrade/migration handling
2. check-update-available.sh - GitHub update check with 24h cache
3. echo-session-id.sh - Session ID injection
4. check-retrospective-due.sh - Retrospective reminder (time/mistake based)
5. inject-session-instructions.sh - CAT session instructions injection
6. clear_skill_markers.py - Cleanup /tmp/cat-skills-loaded-* files
7. inject-env.sh - Persist env vars to CLAUDE_ENV_FILE

## Target State
Single Java dispatcher `GetSessionStartOutput` that runs all 7 handlers internally and returns combined additionalContext output.

## Satisfies
None

## Risk Assessment
- **Risk Level:** HIGH
- **Concerns:** check-upgrade.sh has complex migration logic with backup/restore; inject-session-instructions.sh produces large instruction blocks
- **Mitigation:** Comprehensive tests for each handler; preserve exact output format

## Files to Modify
- hooks/src/main/java/com/cat/hooks/GetSessionStartOutput.java - NEW: Main dispatcher
- hooks/src/main/java/com/cat/hooks/session/ - NEW: Handler package
- hooks/src/main/java/com/cat/hooks/session/CheckUpgrade.java - NEW
- hooks/src/main/java/com/cat/hooks/session/CheckUpdateAvailable.java - NEW
- hooks/src/main/java/com/cat/hooks/session/EchoSessionId.java - NEW
- hooks/src/main/java/com/cat/hooks/session/CheckRetrospectiveDue.java - NEW
- hooks/src/main/java/com/cat/hooks/session/InjectSessionInstructions.java - NEW
- hooks/src/main/java/com/cat/hooks/session/ClearSkillMarkers.java - NEW
- hooks/src/main/java/com/cat/hooks/session/InjectEnv.java - NEW
- hooks/src/test/java/com/cat/hooks/session/ - NEW: Tests for each handler
- plugin/hooks/hooks.json - Consolidate 7 SessionStart entries into 1 java.sh GetSessionStartOutput
- plugin/hooks/check-upgrade.sh - DELETE after migration
- plugin/hooks/check-update-available.sh - DELETE after migration
- plugin/hooks/echo-session-id.sh - DELETE after migration
- plugin/hooks/check-retrospective-due.sh - DELETE after migration
- plugin/hooks/inject-session-instructions.sh - DELETE after migration
- plugin/hooks/session_start_handlers/clear_skill_markers.py - DELETE after migration
- plugin/hooks/inject-env.sh - DELETE after migration

## Acceptance Criteria
- [ ] All 7 SessionStart handlers implemented in Java
- [ ] Combined additionalContext output matches concatenated output of individual scripts
- [ ] check-upgrade.sh migration logic preserved (version compare, backup, run migrations)
- [ ] check-update-available.sh 24h cache preserved
- [ ] check-retrospective-due.sh time+mistake hybrid logic preserved
- [ ] inject-session-instructions.sh full instruction block preserved verbatim
- [ ] clear_skill_markers.py glob cleanup preserved
- [ ] inject-env.sh env file writing preserved
- [ ] hooks.json has single SessionStart entry
- [ ] All 7 bash/python scripts deleted
- [ ] Tests pass

## Handler Interface
Create a new SessionStartHandler interface:
```java
public interface SessionStartHandler
{
  String handle(HookInput input);
}
```
Dispatcher collects all non-null results and combines into single additionalContext.

## Key Implementation Details
- check-upgrade.sh calls migration scripts - Java handler should use ProcessRunner to invoke migration scripts (or port migration logic)
- check-update-available.sh uses curl to GitHub API - Java handler should use HttpClient with 5s timeout
- echo-session-id.sh reads session_id from stdin JSON - already available in HookInput
- check-retrospective-due.sh reads retrospectives/index.json - use JsonMapper
- inject-session-instructions.sh outputs static text block - embed as resource or string constant
- clear_skill_markers.py uses glob - use java.nio.file.Files.newDirectoryStream with glob pattern
- inject-env.sh writes to file - use Files.write

## Execution Steps
1. Create SessionStartHandler interface and GetSessionStartOutput dispatcher
2. Implement each handler class with equivalent logic
3. Write tests for each handler
4. Update hooks.json to single SessionStart entry
5. Delete old bash/python scripts
6. Run full test suite

## Success Criteria
- [ ] All tests pass including new handler tests
- [ ] hooks.json SessionStart section has exactly 1 entry
- [ ] No bash/python SessionStart scripts remain