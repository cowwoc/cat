# Plan: fix-migration-21-awk-syntax-error

## Goal
Fix two bugs in `plugin/migrations/2.1.sh`: an invalid C-style comment in the Phase 6 awk command
that causes a regex compilation failure, and a stray merge conflict marker at line 786.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — both are isolated line-level fixes with no logic changes
- **Mitigation:** N/A

## Files to Modify
- `plugin/migrations/2.1.sh` - fix awk comment syntax in Phase 6 blank-line collapse block; remove
  stray `<<<<<<< HEAD` conflict marker at line 786

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Replace `/* file was all blanks - emit nothing */` C-style comment in Phase 6 awk block with a
  valid awk `# ...` comment (or remove the comment entirely since the empty block is self-evident)
  - Files: `plugin/migrations/2.1.sh` (line ~566)
- Remove stray `<<<<<<< HEAD` merge conflict marker at line 786
  - Files: `plugin/migrations/2.1.sh` (line 786)

## Post-conditions
- [ ] `plugin/migrations/2.1.sh` has no awk regex compilation errors
- [ ] Running `bash -n plugin/migrations/2.1.sh` passes without syntax errors
- [ ] Phase 6 of the migration processes `.gitignore` correctly end-to-end
- [ ] No merge conflict markers remain in `plugin/migrations/2.1.sh`
