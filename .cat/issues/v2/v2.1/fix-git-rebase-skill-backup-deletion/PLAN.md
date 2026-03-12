# Plan: fix-git-rebase-skill-backup-deletion

## Problem
The git-rebase skill's Result Handling table omits backup deletion from the OK status row.
When a rebase attempt fails (ERROR or CONFLICT) and creates a backup branch, then a subsequent retry
succeeds (OK), the backup from the failed attempt is not automatically cleaned up. The `backup_cleaned: true`
field in the OK response only covers the backup created for the current successful attempt, not backups from
prior failed attempts.

## Satisfies
None

## Reproduction
1. Run git-rebase → receives ERROR (`backup-before-rebase-TIMESTAMP` created)
2. Handle error, retry git-rebase → receives OK (`backup_cleaned: true` for the new attempt's backup)
3. The backup from step 1 is never deleted — agent has no instruction to delete it

## Expected vs Actual
- **Expected:** After successful rebase, all backup branches (including from failed prior attempts) are deleted
- **Actual:** Prior attempt backup remains as an orphaned branch

## Root Cause
The OK status row says "Report commits rebased, verify no content changes" with no mention of backup
deletion. No "On OK status" section exists to provide detailed guidance (unlike CONFLICT and ERROR which both
have dedicated sections). Agents rely on `backup_cleaned: true` and assume all cleanup is complete, but this
only covers the current attempt's backup.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None — documentation-only change to skill markdown
- **Mitigation:** Additive change; no behavior modification

## Files to Modify
- `plugin/skills/git-rebase-agent/first-use.md` — Update OK row in Result Handling table; add "On OK
  status" section with backup deletion instructions covering both current and prior failed attempt backups

## Test Cases
- [ ] OK status row includes backup deletion instruction
- [ ] "On OK status" section added with instruction to delete backup and any prior attempt backups
- [ ] CONFLICT and ERROR rows remain consistent

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Update the Result Handling table OK row and add an "On OK status" section in
  `plugin/skills/git-rebase-agent/first-use.md`:
  - Change OK row Agent Recovery Action from "Report commits rebased, verify no content changes" to:
    "Report commits rebased, verify no content changes. Delete backup: `git branch -D <backup_branch>`.
    If retrying after a prior failed attempt, also delete that attempt's backup branch."
  - Add an "On OK status" section immediately before the existing "On CONFLICT status" section:
    ```
    **On OK status:** After a successful rebase:
    - Delete the backup: `git branch -D <backup_branch>`
    - If this rebase was a retry after a prior failed attempt, also delete the prior attempt's backup
      branch
    - The backup exists only during verification — leaving it permanently clutters the repository
    ```
  - Files: `plugin/skills/git-rebase-agent/first-use.md`

## Post-conditions
- [ ] OK status row in Result Handling table includes explicit backup deletion instruction
- [ ] An "On OK status" section exists instructing agents to delete both current and prior attempt backups
- [ ] All three status rows (OK, CONFLICT, ERROR) mention backup deletion
- [ ] E2E: Read `plugin/skills/git-rebase-agent/first-use.md` and confirm all three status rows mention
  backup deletion and an "On OK status" section exists with the retry guidance
