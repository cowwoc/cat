# Plan: Untrack cat-config.local.json from Git

## Goal
Remove `cat-config.local.json` from git tracking so it stays local to each machine and is never committed.

## Satisfies
None (housekeeping)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — file is already in `.gitignore`; removing tracking only affects git index
- **Mitigation:** `git rm --cached` leaves the on-disk file intact

## Files to Modify
- `plugin/templates/gitignore` — add `cat-config.local.json` rule (already done in prior commit)
- `.claude/cat/cat-config.local.json` — remove from git tracking (git rm --cached)
- `.claude/cat/issues/v2/v2.1/STATE.md` — add issue to Issues Pending list

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** Update `v2.1/STATE.md` — add `untrack-cat-config-local` to the Issues Pending list.
   - Files: `.claude/cat/issues/v2/v2.1/STATE.md`

2. **Step 2:** Commit the `plugin/templates/gitignore` update and STATE.md update.
   - Commit type: `config:`
   - Message: `config: add cat-config.local.json to gitignore template and v2.1 issues`

3. **Step 3:** Run `git rm --cached .claude/cat/cat-config.local.json` to remove the file from git tracking
   without deleting it from disk.

4. **Step 4:** Commit the removal.
   - Commit type: `config:`
   - Message: `config: untrack cat-config.local.json — keep as local-only file`

## Post-conditions
- [ ] `git ls-files .claude/cat/cat-config.local.json` returns empty (file is untracked)
- [ ] `.claude/cat/cat-config.local.json` still exists on disk
- [ ] `plugin/templates/gitignore` contains a `cat-config.local.json` entry
- [ ] `.gitignore` contains a `cat-config.local.json` entry
