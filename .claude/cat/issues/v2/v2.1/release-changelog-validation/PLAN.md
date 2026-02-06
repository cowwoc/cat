# Plan: release-changelog-validation

## Goal
Add CHANGELOG.md validation to `/release-plugin` command that checks completeness at the deepest version level and all
ancestor versions before allowing release.

## Satisfies
None - infrastructure/workflow improvement task

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** May require understanding version hierarchy traversal
- **Mitigation:** Use existing version-paths.md concepts for traversal logic

## Files to Modify
- `.claude/commands/release-plugin/SKILL.md` - Add validation step and auto-update logic

## Acceptance Criteria
- [ ] Functionality works as described - validates CHANGELOG.md at deepest level and ancestors
- [ ] Tests written and passing - validation logic tested
- [ ] Documentation updated - skill docs explain the validation
- [ ] No regressions - existing release workflow continues to work
- [ ] All past release versions contain a comprehensive CHANGELOG.md file

## Execution Steps
1. **Step 1:** Read current release-plugin SKILL.md to understand existing structure
   - Files: `.claude/commands/release-plugin/SKILL.md`
   - Verify: File read successfully

2. **Step 2:** Add CHANGELOG validation step before merge/tag operations
   - Check CHANGELOG.md exists at deepest version level
   - Traverse up to ancestor versions and validate each CHANGELOG.md
   - Verify each CHANGELOG has required sections populated
   - Verify: New validation step documented

3. **Step 3:** Add auto-update capability if CHANGELOG is incomplete
   - Detect missing entries (issues completed but not in CHANGELOG)
   - Offer to auto-populate from STATE.md issue lists
   - Verify: Auto-update logic documented

4. **Step 4:** Test the validation workflow
   - Verify: Tests pass with `python3 /workspace/run_tests.py`
