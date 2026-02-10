# Plan: remove-cat-java-home

## Problem

`hook.sh` requires `CAT_JAVA_HOME` environment variable to locate the jlink runtime, but this variable is set by
`session_start.sh` via `export` and does not propagate to subsequent hook invocations. This causes all Java-based hooks
to fail with "CAT_JAVA_HOME not set" error, breaking `/cat:status` and all PreToolUse/PostToolUse hooks.

## Satisfies

None

## Reproduction Code

```bash
echo '{"session_id":"test","user_prompt":"/cat:status"}' | \
  CLAUDE_PLUGIN_ROOT=/path/to/plugin hook.sh GetSkillOutput
# Output: Error: CAT_JAVA_HOME not set. Run session_start.sh first.
```

## Expected vs Actual

- **Expected:** `hook.sh` locates the jlink runtime and invokes the Java handler
- **Actual:** `hook.sh` fails because `CAT_JAVA_HOME` is empty/unset

## Root Cause

Environment variables exported by `session_start.sh` do not persist into the Claude Code process for subsequent hook
invocations. The jlink bundle is always co-located with `hook.sh` at the same directory level, making `CAT_JAVA_HOME`
unnecessary.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Minimal - the jlink bundle path is deterministic (same directory as hook.sh)
- **Mitigation:** Test hook.sh invocation with and without CAT_JAVA_HOME to verify self-relative resolution works

## Files to Modify

- `plugin/hooks/hook.sh` - Replace `CAT_JAVA_HOME` references with `SCRIPT_DIR` derived from `BASH_SOURCE[0]`
- `plugin/hooks/session_start.sh` - Remove the `export CAT_JAVA_HOME` line (line 194)
- `plugin/hooks/README.md` - Update documentation references to remove CAT_JAVA_HOME

## Test Cases

- [ ] `hook.sh` resolves java binary without `CAT_JAVA_HOME` set
- [ ] `hook.sh` resolves AOT cache and AppCDS paths correctly
- [ ] `hook.sh` still works if `CAT_JAVA_HOME` happens to be set (no breakage)
- [ ] `session_start.sh` no longer exports `CAT_JAVA_HOME`

## Execution Steps

1. **Step 1:** Update `plugin/hooks/hook.sh`
   - Add `SCRIPT_DIR` resolution at top of script: `readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"`
   - Replace `find_java()` function: remove `CAT_JAVA_HOME` check, use `"${SCRIPT_DIR}/bin/java"` instead
   - Update `run_handler()`: replace `CAT_JAVA_HOME` in AOT cache path with `SCRIPT_DIR`, replace in AppCDS path with `SCRIPT_DIR`
   - Update file header comment: remove `CAT_JAVA_HOME` from Environment section, add note that java is resolved relative to script location
   - Files: `plugin/hooks/hook.sh`

2. **Step 2:** Update `plugin/hooks/session_start.sh`
   - Remove the line `export CAT_JAVA_HOME="$jdk_path"` (line 194)
   - Files: `plugin/hooks/session_start.sh`

3. **Step 3:** Update `plugin/hooks/README.md`
   - Remove `CAT_JAVA_HOME` from the environment variables table
   - Remove or update the example that shows `CAT_JAVA_HOME=/path/to/runtime ./hook.sh ValidationHandler`
   - Update the session_start.sh description that mentions "Exports CAT_JAVA_HOME for hook.sh"
   - Files: `plugin/hooks/README.md`

4. **Step 4:** Test the changes
   - Run: `echo '{}' | plugin/hooks/hook.sh GetSkillOutput` (without CAT_JAVA_HOME set)
   - Verify java binary is found and handler is invoked
   - Files: none (manual verification)

## Success Criteria

- [ ] `hook.sh` invokes Java handlers successfully without `CAT_JAVA_HOME` being set
- [ ] No references to `CAT_JAVA_HOME` remain in `hook.sh` or `session_start.sh`
- [ ] README.md documentation is updated to reflect the change