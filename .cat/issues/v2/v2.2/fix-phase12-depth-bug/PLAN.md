# Plan: fix-phase12-depth-bug

## Problem
Phase 12 in `plugin/migrations/2.1.sh` uses `find ... -mindepth 5 -maxdepth 5` to find issue-level
STATE.md files, but those files live at depth 4 (path: v*/v*.*/issue-name/STATE.md). This means
Phase 12 silently processes zero issue-level STATE.md files, so the deprecated Last Updated and
Completed fields are never actually removed.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** None (Phase 12 currently does nothing; fix makes it work as intended)
- **Mitigation:** Run migration on a copy of data and verify fields are removed before deploying

## Files to Modify
- `plugin/migrations/2.1.sh` — fix Phase 12 find depth from 5 to 4

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Change Phase 12 find command from `-mindepth 5 -maxdepth 5` to `-mindepth 4 -maxdepth 4`
  - Files: `plugin/migrations/2.1.sh`
- Update the comment (line ~883) to correctly state "depth 4 from issues/"
- Re-run the migration to actually remove deprecated fields from open issue STATE.md files

## Post-conditions
- [ ] Phase 12 find command uses `-mindepth 4 -maxdepth 4`
- [ ] Comment on line ~883 updated to state "depth 4"
- [ ] Migration runs and removes Last Updated / Completed fields from open issue STATE.md files
