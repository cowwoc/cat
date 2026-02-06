# Plan: cleanup-python-files

## Metadata
- **Parent:** migrate-python-to-java
- **Wave:** 5 (sequential - after test migration)
- **Estimated Tokens:** 10K

## Current State
Python hook handlers and test files coexist with Java equivalents. After all handlers and tests are migrated, the Python files are redundant.

## Target State
All Python hook handler files and test files removed. Only Java implementations remain.

## Satisfies
None - cleanup task

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Accidentally removing files still in use
- **Mitigation:** Verify Java equivalents exist and pass tests before each deletion

## Files to Remove

### Python Hook Handlers (`plugin/hooks/`)
- `get-skill-output.py`
- `get-read-pretool-output.py`
- `get-posttool-output.py`
- `get-bash-pretool-output.py`
- `get-bash-posttool-output.py`
- `get-read-posttool-output.py`
- `enforce-worktree-isolation.py`
- `enforce-status-output.py`
- `invoke-handler.py`
- `lib/` directory (config.py, etc.)

### Python Handler Directories (`plugin/hooks/`)
- `bash_handlers/` (13 files)
- `bash_posttool_handlers/` (4 files)
- `skill_handlers/` (16 files)
- `posttool_handlers/` (6 files)
- `prompt_handlers/` (4 files)
- `read_posttool_handlers/` (1 file)
- `read_pretool_handlers/` (1 file)

### Python Test Files
- `tests/handlers/` (12 files)
- `tests/scripts/` (3 files)
- `tests/test_*.py` (4 files)
- `tests/conftest.py`
- `run_tests.py`

## Dependencies
- migrate-python-tests (all tests must be migrated to Java first)

## Execution Steps
1. **Verify Java tests pass** - `mvn test` must succeed before any deletion
2. **Remove Python entry point scripts** - `plugin/hooks/get-*.py`, `enforce-*.py`, `invoke-handler.py`
3. **Remove Python handler directories** - All handler subdirectories under `plugin/hooks/`
4. **Remove Python lib directory** - `plugin/hooks/lib/`
5. **Remove Python test files** - All `tests/` Python files
6. **Remove run_tests.py** - Python test runner no longer needed
7. **Verify no Python references remain** in hooks.json
8. **Run `mvn test`** to verify Java tests still pass after cleanup

## Acceptance Criteria
- [ ] No Python `.py` files remain in `plugin/hooks/` (except standalone shell-helper scripts)
- [ ] No Python test files remain in `tests/`
- [ ] `run_tests.py` removed
- [ ] hooks.json contains no `python3` commands
- [ ] `mvn test` passes after all removals
