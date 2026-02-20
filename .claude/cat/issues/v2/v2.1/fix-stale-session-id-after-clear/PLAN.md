# Plan: fix-stale-session-id-after-clear

## Problem
After `/clear`, Claude Code fires SessionStart with a new session_id in stdin JSON. InjectEnv writes the updated
CLAUDE_SESSION_ID to the new session's env directory. However, Claude Code's Bash tool caches loaded env from the
OLD session's directory and doesn't invalidate the cache after `/clear`. This causes `$CLAUDE_SESSION_ID` in Bash to
retain the stale value, breaking lock verification and worktree operations.

## Root Cause
Claude Code's session env loader caches loaded env in memory. After `/clear`, the cache still serves files from the
old session directory. InjectEnv only writes to the current and resumed session directories, missing the old cached
directory.

## Fix
Write env content to ALL existing session-env sibling directories, not just the current and resumed ones. This ensures
that regardless of which directory Claude Code's cache reads from, it gets the current CLAUDE_SESSION_ID.

Tag with: `// WORKAROUND: https://github.com/anthropics/claude-code/issues/14433`

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Writing to extra directories is a minor overhead; symlink check prevents security issues
- **Mitigation:** Existing symlink security checks apply to all directories

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java` - Add method to write to all sibling
  session-env directories

## Acceptance Criteria
- [ ] InjectEnv writes env content to all existing session-env sibling directories
- [ ] Symlink security check applied to each sibling directory
- [ ] Workaround comment references issue URL
- [ ] `mvn -f client/pom.xml verify` passes

## Execution Steps
1. **Step 1:** Add a `writeToAllSessionDirs` method to InjectEnv that iterates all subdirectories of sessionEnvBase
   and writes the env content to each one (skipping symlinks and the directories already written to).
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java`
2. **Step 2:** Call `writeToAllSessionDirs` from `handle()` after the existing writes, passing sessionEnvBase,
   envPath, and content. Collect warnings from all writes.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java`
3. **Step 3:** Add workaround comment `// WORKAROUND: https://github.com/anthropics/claude-code/issues/14433`
   above the new method.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java`
4. **Step 4:** Run `mvn -f client/pom.xml verify` to ensure build passes.

## Success Criteria
- [ ] `mvn -f client/pom.xml verify` exits with code 0
- [ ] InjectEnv writes to all session-env sibling directories
