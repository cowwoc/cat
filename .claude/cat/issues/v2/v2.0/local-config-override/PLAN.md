# Plan: local-config-override

## Goal
Add support for cat-config.local.json that overrides settings in cat-config.json, which in turn override default values.

## Satisfies
None - infrastructure task

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Config loading order must be correct; existing behavior should not change
- **Mitigation:** Unit tests for config layering

## Files to Modify
- plugin/hooks/lib/config.py - Add local config loading logic (or equivalent config loader)

## Acceptance Criteria
- [ ] cat-config.local.json overrides cat-config.json settings
- [ ] cat-config.json overrides default values
- [ ] cat-config.local.json is gitignored by default (add to .gitignore template)
- [ ] Missing local config file does not cause errors
- [ ] Existing behavior unchanged when no local config exists

## Execution Steps
1. **Step 1:** Identify config loading code
   - Files: plugin/hooks/lib/ or similar
   - Verify: Locate current config loading implementation

2. **Step 2:** Add local config loading
   - Files: Config loader module
   - Verify: Local config values override base config

3. **Step 3:** Update .gitignore handling
   - Files: Templates or init process
   - Verify: cat-config.local.json is gitignored

4. **Step 4:** Add tests
   - Files: Test file for config loading
   - Verify: python3 /workspace/run_tests.py passes
