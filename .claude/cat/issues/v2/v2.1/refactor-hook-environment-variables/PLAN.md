# Plan: Refactor Hook Environment Variables

## Current State
Hooks currently try to read `CLAUDE_SESSION_ID` and other environment variables from the process environment, which Claude Code does NOT provide. This forces hooks to fail during initialization or use workarounds like sourcing session-env files. Java hooks use `JvmScope` which eagerly reads environment variables in the constructor.

## Target State
- Hooks read session-specific values from `HookInput` JSON (passed via stdin), not from environment variables
- Non-hook commands read environment variables via a new `ClaudeEnv` class
- All Java hooks return exit code 0 and communicate behavior via JSON output instead of exit codes
- `SessionStart` hook only appends to `CLAUDE_ENV_FILE` for new sessions, not resumed sessions
- Clean separation: hooks use HookInput, CLI tools use ClaudeEnv, `JvmScope` is only for scope management

## Satisfies
None - Tech debt / architectural improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** None - this is internal refactoring transparent to users
- **Scope Impact:** Affects all Java hooks and hook launcher scripts
- **Mitigation:** Comprehensive testing of all hook types; staging changes incrementally

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java` - Remove environment-dependent methods
- `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeEnv.java` (NEW) - Read env variables for non-hooks
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseHook.java` - Use HookInput for sessionId
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostBashHook.java` - Use HookInput for sessionId
- `client/src/main/java/io/github/cowwoc/cat/hooks/PreToolUseHook.java` - Use HookInput for sessionId
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseFailureHook.java` - Use HookInput for sessionId
- `client/src/main/java/io/github/cowwoc/cat/hooks/*Hook.java` (other hooks) - Use HookInput where needed
- `plugin/hooks/session-start.sh` - Only append to CLAUDE_ENV_FILE on new sessions
- `client/build-jlink.sh` - Update hook launcher scripts to use JSON output for error reporting

## Pre-conditions
- [x] PostToolUseHook has been refactored to defer handler initialization
- [x] Environment logging added to hook launcher scripts (evidence collected)
- [x] Hook documentation reviewed (https://code.claude.com/docs/en/hooks#json-output)

## Execution Steps

1. **Create ClaudeEnv class for environment variable access**
   - New file: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeEnv.java`
   - Read from `CLAUDE_SESSION_ID`, `CLAUDE_PROJECT_DIR`, `CLAUDE_PLUGIN_ROOT`, `CLAUDE_ENV_FILE`
   - Use fail-fast pattern: throw AssertionError if required vars not set
   - Files: ClaudeEnv.java

2. **Update SessionStart hook to avoid re-appending on resumed sessions**
   - Check if session is new or resumed via HookInput
   - Only append environment variables to CLAUDE_ENV_FILE for new sessions
   - Files: plugin/hooks/session-start.sh

3. **Update all Java hooks to use HookInput instead of JvmScope for session data**
   - PostToolUseHook: Already done (defer to run() method)
   - PostBashHook: Extract sessionId from HookInput
   - PreToolUseHook: Extract sessionId from HookInput
   - PostToolUseFailureHook: Extract sessionId from HookInput
   - Other hooks: Similar pattern
   - Files: All *Hook.java files in hooks package

4. **Update JvmScope to remove environment-dependent method calls**
   - Remove or make optional: methods that require CLAUDE_SESSION_ID from environment
   - Keep scope-level path resolution, remove session-specific logic
   - Files: JvmScope.java, MainJvmScope.java

5. **Update hook launcher scripts to return exit code 0 with JSON output**
   - Capture stdout and stderr
   - Always exit with code 0
   - Use JSON to report success, warnings, or errors (per Claude Code hook documentation)
   - Log errors to stderr but indicate "success" in exit code
   - Files: client/build-jlink.sh (launcher templates)

6. **Update non-hook commands to use ClaudeEnv**
   - Commands like work-prepare, progress-banner, etc. that read environment variables
   - Replace direct environment access with ClaudeEnv class calls
   - Files: Non-hook Java command files

7. **Run all tests and validate hook behavior**
   - Unit tests for ClaudeEnv
   - Integration tests for each hook type
   - Verify hooks return exit code 0 with proper JSON output
   - Files: All *Test.java files

## Post-conditions
- [ ] All Java hooks read session-specific data from HookInput, not environment
- [ ] All non-hook commands read environment via ClaudeEnv class
- [ ] All hooks return exit code 0 with JSON output indicating behavior
- [ ] SessionStart hook only appends to CLAUDE_ENV_FILE for new sessions (not resumed)
- [ ] JvmScope no longer requires environment variables for initialization
- [ ] All hook types (PostToolUse, PostBash, PreToolUse, PostToolUseFailure, etc.) pass tests
- [ ] No regressions in hook behavior or error handling
- [ ] E2E: Run `/cat:work` on a sample issue and verify all hooks execute without environment variable errors
