# Plan: fail-fast-environment-variables

## Goal
Replace fallback patterns in scripts that silently use default paths with fail-fast behavior that errors when required environment variables are not set.

## Satisfies
None - infrastructure/cleanup task

## Current State
Several scripts use bash fallback patterns like `${VAR:-default}` for path variables, which can silently use incorrect defaults when called from unexpected contexts.

## Target State
Scripts require explicit arguments or environment variables and fail immediately with clear error messages when requirements are not met.

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Scripts that relied on fallback defaults will fail until callers provide required arguments
- **Mitigation:** Add clear error messages explaining what's required

## Files to Modify
- `plugin/scripts/register-hook.sh` - Change `${CLAUDE_CONFIG_DIR:-$HOME/.claude}` to fail-fast
- `plugin/scripts/status-data.sh` - Require CAT_DIR argument instead of defaulting to `.claude/cat/issues`
- `plugin/scripts/lib/version-utils.sh` - Require cat_dir parameter in get_task_dir() and get_version_dir()

## Acceptance Criteria
- [ ] register-hook.sh fails with clear error if CLAUDE_CONFIG_DIR not set
- [ ] status-data.sh fails with clear error if no argument provided
- [ ] version-utils.sh functions fail with clear error if cat_dir not provided
- [ ] All tests pass

## Execution Steps
1. **Step 1:** Update register-hook.sh
   - Files: plugin/scripts/register-hook.sh
   - Change: `CLAUDE_DIR="${CLAUDE_CONFIG_DIR:?CLAUDE_CONFIG_DIR must be set}"`
   - Verify: Script fails with clear message when env var missing

2. **Step 2:** Update status-data.sh
   - Files: plugin/scripts/status-data.sh
   - Change: Add argument validation at start of script
   - Verify: Script fails with usage message when no argument provided

3. **Step 3:** Update version-utils.sh
   - Files: plugin/scripts/lib/version-utils.sh
   - Change: Make cat_dir parameter required in get_task_dir() and get_version_dir()
   - Verify: Functions fail with clear message when cat_dir not provided

4. **Step 4:** Run tests
   - Verify: `python3 /workspace/run_tests.py` passes
