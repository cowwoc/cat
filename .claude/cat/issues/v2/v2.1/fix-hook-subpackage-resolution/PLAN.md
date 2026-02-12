# Plan: fix-hook-subpackage-resolution

## Problem
hook.sh incorrectly treats handler class names containing dots (e.g., `skills.RunGetStatusOutput`) as fully-qualified class names, passing them directly to the JVM. These are actually module-relative paths that need the module package prefix prepended.

## Satisfies
None - infrastructure bugfix

## Reproduction Code
```bash
# This fails with: Could not find or load main class skills.RunGetStatusOutput
${PLUGIN_ROOT}/hooks/hook.sh skills.RunGetStatusOutput
```

## Expected vs Actual
- **Expected:** hook.sh prepends `io.github.cowwoc.cat.hooks.` to get `io.github.cowwoc.cat.hooks.skills.RunGetStatusOutput`
- **Actual:** hook.sh sees the dot in `skills.RunGetStatusOutput` and passes it as-is, which is not a valid FQCN

## Root Cause
Line 50 of hook.sh checks `if [[ "$handler_class" == *.* ]]` and assumes any name with a dot is already fully qualified. But callers pass module-relative paths like `skills.RunGetStatusOutput` which contain dots but are not FQCNs.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Minimal - all existing callers in hooks.json use short names without dots (e.g., `GetBashPretoolOutput`), which will continue to work identically since the prefix is always prepended
- **Mitigation:** Verify all callers in hooks.json and handler scripts still work after change

## Files to Modify
- `plugin/hooks/hook.sh` - Remove the dot-detection conditional; always prepend MODULE prefix

## Test Cases
- [ ] Short names without dots still resolve correctly (e.g., `GetBashPretoolOutput` -> `io.github.cowwoc.cat.hooks.GetBashPretoolOutput`)
- [ ] Subpackage names with dots resolve correctly (e.g., `skills.RunGetStatusOutput` -> `io.github.cowwoc.cat.hooks.skills.RunGetStatusOutput`)
- [ ] All hooks.json entries still work after the change

## Execution Steps
1. **Edit plugin/hooks/hook.sh:** Replace lines 48-54 (the dot-detection conditional) with a single line: `local full_class="${MODULE}.${handler_class}"`
   - Files: `plugin/hooks/hook.sh`
2. **Copy updated hook.sh to plugin cache:** `cp plugin/hooks/hook.sh /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/hook.sh`
3. **Verify fix:** Run `CLAUDE_PROJECT_DIR=/workspace /home/node/.config/claude/plugins/cache/cat/cat/2.1/hooks/hook.sh skills.RunGetStatusOutput` and confirm it produces status output

## Success Criteria
- [ ] `hook.sh skills.RunGetStatusOutput` executes successfully and produces status output
- [ ] All existing hook.sh callers (hooks.json entries using short names) continue to work
- [ ] No regressions in hook invocations