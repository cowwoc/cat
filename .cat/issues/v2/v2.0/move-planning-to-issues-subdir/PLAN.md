# Plan: move-planning-to-issues-subdir

## Goal
Move the planning structure from `.cat/` to `.cat/issues/` to better organize issue tracking data
separately from other CAT configuration.

## Satisfies
None - infrastructure/maintenance task

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** External references in plugin code, scripts, and documentation may break
- **Mitigation:** Grep for all references before and after, verify no broken paths

## Files to Modify
- All files under `.cat/v*` - move to `.cat/issues/v*`
- Plugin files referencing `.cat/v*` paths
- Scripts referencing planning paths
- Documentation with path references

## Post-conditions
- [ ] All version directories moved to `.cat/issues/`
- [ ] All external references updated (plugin, scripts, docs)
- [ ] No broken path references remain
- [ ] CAT commands still work after move

## Execution Waves

### Wave 1
1. **Find all external references:**
   - Grep for `.cat/v` patterns outside of `.cat/`
   - Verify: List of files to update

2. **Create issues subdirectory and move content:**
   - Files: `.cat/v*` → `.cat/issues/v*`
   - Verify: `ls .cat/issues/`

3. **Update all external references:**
   - Files: All identified in step 1
   - Verify: Grep shows no stale references

4. **Update ROADMAP.md and PROJECT.md paths if needed:**
   - Verify: Paths resolve correctly

5. **Test CAT commands:**
   - Verify: `/cat:status` works
