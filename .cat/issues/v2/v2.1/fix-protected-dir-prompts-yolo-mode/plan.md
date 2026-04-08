# Plan: fix-protected-dir-prompts-yolo-mode

## Problem
Since Claude Code v2.1.78, writes to protected directories (`.git/`, `.claude/`, `.vscode/`, `.idea/`) trigger
permission confirmation prompts even when running with `--dangerously-skip-permissions` (bypassPermissions mode).
This creates friction in automated workflows that have explicitly opted into unrestricted permissions.

## Parent Requirements
None

## Root Cause
The file-safety checker creates a synthetic permission rule before the `bypassPermissions` setting is consulted,
so the bypass cannot override it. This is a known Claude Code bug (anthropics/claude-code#36044).

## Expected vs Actual
- **Expected:** `--dangerously-skip-permissions` suppresses all permission prompts including protected directory writes
- **Actual:** Protected directory writes still prompt even in bypassPermissions mode

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None — the hook only fires when `permission_mode` is `"bypassPermissions"`; normal mode behavior is unchanged
- **Mitigation:** The `permission_mode` check ensures the hook is a no-op in default/other modes

## Files to Modify
- `.claude/hooks/auto-approve-protected-dirs.sh` — New hook script: reads `permission_mode`, auto-approves only in bypassPermissions mode
- `.claude/settings.json` — Register the hook under `PermissionRequest` for `Edit` and `Write` matchers

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Create `.claude/hooks/auto-approve-protected-dirs.sh`:
  - Read stdin JSON
  - Extract `permission_mode` field using grep/sed (no jq)
  - If `permission_mode != "bypassPermissions"`, exit 0 with no output (hook defers)
  - Extract `tool_input.file_path` field
  - If path is under a protected directory (contains `/.git/`, `/.claude/`, `/.vscode/`, or `/.idea/`), output the allow decision:
    `{"hookSpecificOutput":{"hookEventName":"PermissionRequest","decision":{"behavior":"allow"}}}`
  - Make the script executable (`chmod +x`)
  - Files: `.claude/hooks/auto-approve-protected-dirs.sh`
- Update `.claude/settings.json` — add `PermissionRequest` hook entries for matchers `Edit` and `Write`, each
  invoking `.claude/hooks/auto-approve-protected-dirs.sh`
  - Files: `.claude/settings.json`

## Post-conditions
- [ ] Protected directory writes no longer prompt for permission when running in bypassPermissions mode
- [ ] Normal mode behavior unchanged — prompts still appear when not in bypassPermissions mode
- [ ] E2E: Hook script exits with no output when `permission_mode` is not `"bypassPermissions"`
- [ ] E2E: Hook script outputs allow decision when `permission_mode` is `"bypassPermissions"` and path is under a protected directory
