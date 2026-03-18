# Plan: fix-inject-env-resume-session-id

## Problem
On session resume, `InjectEnv` returns early without writing env vars. `CLAUDE_ENV_FILE` points to the
startup session directory, but Claude Code's env loader reads from the resumed session directory. This
means `CLAUDE_PROJECT_DIR` and `CLAUDE_PLUGIN_ROOT` are not available in resumed sessions.

## Root Cause
`InjectEnv.handle()` returns early for `source="resume"` (and `source="compact"`), skipping env injection
entirely. While `writeToResumedSessionDir()` exists as a workaround for upstream bug
[#24775](https://github.com/anthropics/claude-code/issues/24775), it is only called during `source="startup"`,
when `session_id` in stdin equals the startup session ID — the same directory `CLAUDE_ENV_FILE` already
points to — making it a no-op.

## Fix
When `source` is `"resume"` or `"compact"`, write env vars to the session directory derived from `session_id`
in stdin JSON. The correct path is constructed by replacing the session ID segment in `CLAUDE_ENV_FILE` with
the resumed session ID:

```
correctedPath = CLAUDE_ENV_FILE.parent.parent / session_id / CLAUDE_ENV_FILE.filename
```

This reuses the existing `writeToResumedSessionDir()` logic but invokes it for resume/compact events rather
than only for startup.

Tag the fix with: `// Workaround for https://github.com/anthropics/claude-code/issues/24775`

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** May write duplicate content if Claude Code fixes #24775 upstream, but since we use
  `APPEND` mode and content is idempotent export statements, this is harmless.
- **Mitigation:** Symlink security check prevents path traversal attacks.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java` — handle resume/compact events

## Post-conditions
- [ ] `InjectEnv.handle()` writes env vars for `source="resume"` to the session directory derived from
  `session_id` in stdin JSON
- [ ] `InjectEnv.handle()` writes env vars for `source="compact"` to the session directory derived from
  `session_id` in stdin JSON
- [ ] Symlink security check applies to the derived path
- [ ] Workaround comment references upstream issue URL
- [ ] Tests cover resume and compact source values
- [ ] `mvn -f client/pom.xml verify` exits with code 0

## Execution Waves

### Wave 1
1. **Step 1:** Update `InjectEnv.handle()` to write env vars for `source="resume"` and `source="compact"` by
   calling `writeToResumedSessionDir()` with the `session_id` from stdin JSON.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java`
2. **Step 2:** Update the class-level and method-level Javadoc to reflect the new behavior for resume/compact
   sources.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java`
3. **Step 3:** Add tests to `SessionStartHookTest` covering `source="resume"` and `source="compact"` writing to
   the derived session directory.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionStartHookTest.java`
4. **Step 4:** Run `mvn -f client/pom.xml verify` to ensure build and all tests pass.
