# Plan: self-discover-env-vars

## Problem
Environment variables set by CAT hooks (CLAUDE_PLUGIN_ROOT, CLAUDE_PROJECT_DIR, CLAUDE_SESSION_ID) don't persist across
Bash tool invocations. Each Bash call spawns a fresh shell that doesn't inherit hook-set environment variables, causing
scripts to fail with "NOT SET" errors.

## Satisfies
None - infrastructure/bugfix task

## Reproduction Code
```bash
# In /cat:work, after hooks have run:
echo "CLAUDE_PLUGIN_ROOT: ${CLAUDE_PLUGIN_ROOT:-NOT SET}"
# Output: CLAUDE_PLUGIN_ROOT: NOT SET
```

## Expected vs Actual
- **Expected:** Scripts can access CLAUDE_PLUGIN_ROOT, CLAUDE_PROJECT_DIR, and CLAUDE_SESSION_ID
- **Actual:** All three variables are "NOT SET" in Bash tool invocations

## Root Cause
Claude Code hooks run in a different process context than Bash tool invocations. Environment variables don't cross this
boundary.

## Solution: Hybrid Approach
1. **CLAUDE_PLUGIN_ROOT**: Scripts self-discover from their own location
2. **CLAUDE_PROJECT_DIR**: Scripts walk up directory tree to find `.claude/cat/`
3. **CLAUDE_SESSION_ID**: Must be passed explicitly as argument (no way to discover)

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Scripts that currently work with manual env var setting will continue to work
- **Mitigation:** Add tests for self-discovery functions

## Files to Modify
- `plugin/scripts/get-available-issues.sh` - Add self-discovery, require --session-id

## Test Cases
- [ ] Script works when called directly (no env vars set)
- [ ] Script fails gracefully when --session-id not provided
- [ ] Self-discovery finds correct paths from various working directories

## Acceptance Criteria
- [ ] Bug no longer reproducible - scripts work without hook-set environment variables
- [ ] Regression test added - tests prevent this bug from recurring
- [ ] Root cause addressed - hybrid approach handles all three variables appropriately

## Execution Steps
1. **Add self-discovery functions** to get-available-issues.sh
   - Derive CLAUDE_PLUGIN_ROOT from script location
   - Find CLAUDE_PROJECT_DIR by walking up to `.claude/cat/`
   - Require --session-id as mandatory argument
   - Verify: Script works when env vars not set

2. **Add tests** for self-discovery
   - Test path discovery from various directories
   - Test failure when --session-id missing
   - Verify: `python3 /workspace/run_tests.py` passes
