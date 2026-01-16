# Plan: complete-patch-version-support

## Problem
The flexible-version-schema task added infrastructure for parsing patch versions (v1.0.1 format) but did NOT implement the UI or workflow commands to actually create patch versions. Currently:
- ✅ Parsing/validation supports v1.0.1 format (version-utils.sh, validate-worktree-branch.sh)
- ❌ `/cat:add` only offers Task, Minor, Major options - no Patch
- ❌ add.md has no patch_* steps (patch_create, patch_update_parent, patch_update_roadmap)
- ❌ remove.md has no patch_* steps (patch_delete, patch_update_parent, patch_update_roadmap)

## Satisfies
- Completes the partial patch version implementation from flexible-version-schema

## Root Cause
The flexible-version-schema task focused on infrastructure (parsing) but skipped the add/remove command updates.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Regression Risk:** Could affect existing minor/major workflows if not careful
- **Mitigation:** Follow the existing pattern for minor version steps exactly

## Files to Modify
- plugin/commands/add.md
  - Add "Patch version" option to select_type step
  - Add patch_select_minor, patch_create, patch_update_parent, patch_update_roadmap steps
- plugin/commands/remove.md
  - Add patch_delete, patch_update_parent, patch_update_roadmap steps
- plugin/commands/status.md (possibly)
  - Ensure patch versions display correctly

## Test Cases
- [ ] `/cat:add` shows "Patch version" option
- [ ] Creating a patch version (v1.0.1) creates correct directory structure
- [ ] Patch version appears in parent minor STATE.md
- [ ] Removing a patch version cleans up correctly
- [ ] Status command displays patch versions

## Execution Steps
1. **Add "Patch version" option to add.md select_type**
   - Add option: "Patch version" - Add a patch version to an existing minor
   - Verify: grep shows 4 options (Task, Minor, Major, Patch)

2. **Add patch workflow steps to add.md**
   - patch_select_minor: Select which minor to add patch to
   - patch_ask_number: Auto-increment patch number
   - patch_create: Create directory structure
   - patch_update_parent: Update minor STATE.md with patch list
   - patch_update_roadmap: Update ROADMAP.md (if needed)
   - Verify: All steps have explicit bash commands with verification

3. **Add patch removal steps to remove.md**
   - patch_delete: Remove patch directory
   - patch_update_parent: Remove from minor STATE.md
   - patch_update_roadmap: Update ROADMAP.md
   - Verify: All steps have explicit bash commands with verification

4. **Test full workflow**
   - Create v2.0.1 patch version
   - Verify directory structure and STATE.md updates
   - Remove patch version
   - Verify cleanup
