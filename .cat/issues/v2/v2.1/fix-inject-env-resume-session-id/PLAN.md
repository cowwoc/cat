# Plan: fix-inject-env-resume-session-id

## Problem
`InjectEnv` relies on `writeToAllSessionDirs()` to propagate env vars to the resumed session directory.
This works only when the resumed session's directory already exists as a leftover from the previous run.
If Claude Code cleans up old session-env directories, the resumed session dir won't exist at startup time
and env vars will never reach it, causing `CLAUDE_PROJECT_DIR` and `CLAUDE_PLUGIN_ROOT` to be unset in
resumed sessions.

## Root Cause
Upstream bug [#24775](https://github.com/anthropics/claude-code/issues/24775): during the `source="resume"`
SessionStart event, `CLAUDE_ENV_FILE` still points to the startup session directory, while the env loader
reads from the resumed session directory. `writeToResumedSessionDir()` exists to handle this but is only
called during `source="startup"`, when `session_id` from stdin equals the startup directory — making it a
no-op.

## Fix
When `source="resume"`, write env vars to the session directory derived from `session_id` in stdin JSON.
Construct the correct path as:

```
sessionEnvBase = CLAUDE_ENV_FILE.parent.parent
correctedPath  = sessionEnvBase / session_id / CLAUDE_ENV_FILE.filename
```

This reuses `writeToResumedSessionDir()` with the resumed session ID. The early-return guard for
non-startup sources must be relaxed to allow writes for `source="resume"`.

Tag the new code path with: `// Workaround for https://github.com/anthropics/claude-code/issues/24775`

`writeToResumedSessionDir()` must use `TRUNCATE_EXISTING` (overwrite) instead of `APPEND` when writing to the resumed
session directory. Rationale: if `writeToAllSessionDirs()` already wrote to that dir during startup (because the
leftover dir existed), appending during resume would create duplicate entries. Each hook writes to its own numbered
file, so overwriting is safe.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** If Claude Code fixes #24775 upstream and `writeToAllSessionDirs()` already wrote to the resumed
  session dir at startup, the resume write would overwrite with identical content — harmless.
- **Mitigation:** Existing symlink security check in `writeToResumedSessionDir()` applies to the derived
  path.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java` — handle `source="resume"`

## Post-conditions
- [ ] `InjectEnv.handle()` writes env vars to the derived resumed session directory when `source="resume"`
- [ ] Symlink security check applies to the derived path
- [ ] Workaround comment references upstream issue URL
- [ ] Tests cover `source="resume"` writing to the correct derived directory
- [ ] Writing to the resumed session directory uses TRUNCATE_EXISTING (overwrite), not APPEND
- [ ] `mvn -f client/pom.xml verify` exits with code 0

## Sub-Agent Waves

### Wave 1
1. **Step 1:** Relax the early-return guard in `handle()` to allow `source="resume"` through, then call
   `writeToResumedSessionDir()` with the resumed `session_id` and return. Update `writeToResumedSessionDir()` to use
   `TRUNCATE_EXISTING` (overwrite) instead of `APPEND` when writing to the resumed session directory.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java`
2. **Step 2:** Update class-level and method-level Javadoc to document the new `source="resume"` behavior.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java`
3. **Step 3:** Add tests covering `source="resume"` writing to the derived session directory (and NOT
   to the startup directory).
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionStartHookTest.java`
4. **Step 4:** Run `mvn -f client/pom.xml verify` to confirm build and tests pass.
