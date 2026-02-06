# Plan: fix-add-skill-changelog-creation

## Problem

The `/cat:add` skill has explicit bash scripts for creating CHANGELOG.md when creating patch versions, but for
major/minor versions it only has prose instructions ("Create STATE.md, PLAN.md, and CHANGELOG.md") without actual
implementation. This causes version-level CHANGELOGs to not be created.

## Reproduction Code

```bash
# When /cat:add creates a minor version, it does:
mkdir -p "$VERSION_PATH/issue"
# Then says "Create STATE.md, PLAN.md, and CHANGELOG.md for minor version."
# But no bash script actually creates CHANGELOG.md
```

## Expected vs Actual

- **Expected:** CHANGELOG.md created for all version types (major, minor, patch)
- **Actual:** CHANGELOG.md only created for patch versions; major/minor have prose-only instructions

## Root Cause

Incomplete implementation in `plugin/skills/add/SKILL.md`. The patch version section (lines 1120-1136) has explicit bash
to create CHANGELOG.md, but the major version section (line 1053) and minor version section (line 1063) only have prose
instructions without corresponding bash scripts.

## Satisfies

- None (infrastructure fix)

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None - adding missing functionality
- **Mitigation:** Test all version creation paths

## Files to Modify

- `plugin/skills/add/SKILL.md` - Add explicit CHANGELOG.md creation scripts for major/minor versions

## Test Cases

- [ ] Creating a minor version creates CHANGELOG.md
- [ ] Creating a major version creates CHANGELOG.md (including initial minor)
- [ ] CHANGELOG.md follows template format
- [ ] Existing patch version creation still works

## Execution Steps

1. **Add CHANGELOG.md creation for major versions:**
   - After major STATE.md creation, add bash to create CHANGELOG.md
   - Also create CHANGELOG.md for the initial minor version (X.0)
   - Verify: Check script creates both files

2. **Add CHANGELOG.md creation for minor versions:**
   - After minor STATE.md creation, add bash to create CHANGELOG.md
   - Use same template pattern as patch version
   - Verify: Check script creates file
