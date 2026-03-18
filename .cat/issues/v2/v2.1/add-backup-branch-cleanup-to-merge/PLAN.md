# Plan: add-backup-branch-cleanup-to-merge

## Goal

Update the work-merge skill's REBASE_CONFLICT handler to include mandatory backup branch cleanup,
preventing orphaned backup branches from accumulating in the repository.

## Satisfies

- None (prevention for M458)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — documentation-only change to skill instructions
- **Mitigation:** N/A

## Files to Modify

- `plugin/skills/work-merge-agent/first-use.md` — update REBASE_CONFLICT handler table entry and add
  bash code snippet for backup branch cleanup

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Update REBASE_CONFLICT table row in work-merge-agent/first-use.md to mention backup_branch deletion
  - Files: `plugin/skills/work-merge-agent/first-use.md`
- Add bash code snippet below the table showing how to extract backup_branch from REBASE_CONFLICT
  JSON and delete it with `git branch -D`
  - Files: `plugin/skills/work-merge-agent/first-use.md`
- Clarify that backup_branch cleanup is mandatory regardless of whether STOP is followed or
  conflict is manually resolved
  - Files: `plugin/skills/work-merge-agent/first-use.md`

## Post-conditions

- [ ] REBASE_CONFLICT table row mentions backup_branch deletion
- [ ] Bash code snippet shows how to extract and delete backup branch
- [ ] Documentation clarifies cleanup is mandatory in all REBASE_CONFLICT scenarios
