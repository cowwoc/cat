# Plan: remove-unused-scripts

## Current State
Several scripts across the project are unreferenced dead code:
- `plugin/scripts/get-config-display.sh` — no active references
- `plugin/scripts/get-progress-banner.sh` — no active references
- `plugin/scripts/get-render-diff.sh` — no active references
- `plugin/scripts/measure-emoji-widths.sh` — no active references
- `plugin/scripts/lib/progress.sh` — defined but never sourced
- `plugin/scripts/lib/__init__.py` — empty placeholder
- `scripts/compress-validate-loop.py` — unused wrapper
- `scripts/compress_validate_loop.py` — unused feature implementation
- `scripts/final-fixes.py` — unused migration utility
- `scripts/fix-remaining-issues.py` — unused migration utility
- `scripts/fix-state-files-properly.py` — unused migration utility
- `scripts/migrate-state-schema.py` — unused migration utility
- `scripts/state_schema_lib.py` — only used by unused migration scripts
- `scripts/__pycache__/` — Python cache directory
- `plugin/scripts/__pycache__/` — Python cache directory
- `plugin/scripts/lib/__pycache__/` — Python cache directory

## Target State
All unused scripts and their associated `__pycache__` directories removed.

## Satisfies
None (tech debt cleanup)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Removing a script that's actually needed
- **Mitigation:** Each script was verified as unreferenced via codebase-wide grep; verify again before deletion

## Files to Modify
- All files listed above — delete

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Re-verify each script has no active references via grep
   - Files: all scripts listed above
2. **Step 2:** Remove all unused scripts and `__pycache__` directories
3. **Step 3:** Remove `scripts/tests/` directory if it only tests removed scripts
4. **Step 4:** Verify no broken references remain (grep for removed filenames)
5. **Step 5:** Run full test suite to verify no regressions

## Post-conditions
- [ ] User-visible behavior unchanged
- [ ] All tests passing
- [ ] No dead code remaining in `plugin/scripts/` or `scripts/`
- [ ] E2E: Run `mvn -f client/pom.xml test` and confirm all tests pass
