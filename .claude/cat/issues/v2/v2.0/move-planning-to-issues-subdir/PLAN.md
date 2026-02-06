# Plan: move-planning-to-issues-subdir

## Goal
Move the planning structure from `.claude/cat/` to `.claude/cat/issues/` to better organize issue tracking data
separately from other CAT configuration.

## Satisfies
None - infrastructure/maintenance task

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** External references in plugin code, scripts, and documentation may break
- **Mitigation:** Grep for all references before and after, verify no broken paths

## Files to Modify
- All files under `.claude/cat/v*` - move to `.claude/cat/issues/v*`
- Plugin files referencing `.claude/cat/v*` paths
- Scripts referencing planning paths
- Documentation with path references

## Acceptance Criteria
- [ ] All version directories moved to `.claude/cat/issues/`
- [ ] All external references updated (plugin, scripts, docs)
- [ ] No broken path references remain
- [ ] CAT commands still work after move

## Execution Steps
1. **Find all external references:**
   - Grep for `.claude/cat/v` patterns outside of `.claude/cat/`
   - Verify: List of files to update

2. **Create issues subdirectory and move content:**
   - Files: `.claude/cat/v*` → `.claude/cat/issues/v*`
   - Verify: `ls .claude/cat/issues/`

3. **Update all external references:**
   - Files: All identified in step 1
   - Verify: Grep shows no stale references

4. **Update ROADMAP.md and PROJECT.md paths if needed:**
   - Verify: Paths resolve correctly

5. **Test CAT commands:**
   - Verify: `/cat:status` works
