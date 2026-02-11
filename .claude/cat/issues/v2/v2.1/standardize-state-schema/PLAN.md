# Plan: standardize-state-schema

## Current State
Issue STATE.md files across 361 issues use inconsistent keys. Analysis found:
- 5 mandatory keys used inconsistently (Dependencies missing in 2, Last Updated missing in 99, Resolution missing in 101 closed issues)
- 13+ non-standard keys appearing in minorities of files (Completed, Version, Tokens Used, Created From, Started, Reason, Decomposed At, Decomposed, Blocks, Duplicate Of, Assignee, Priority, Worktree, etc.)
- Non-standard Resolution values (verbose descriptions instead of standardized values)

## Target State
All issue STATE.md files conform to a standardized schema:

### Mandatory Keys (all issues)
- **Status:** open | in-progress | closed
- **Progress:** 0-100%
- **Dependencies:** [] or [issue-id-1, issue-id-2]
- **Blocks:** [] or [issue-id-1, issue-id-2]
- **Last Updated:** YYYY-MM-DD

### Mandatory for Closed Issues
- **Resolution:** implemented | duplicate (issue-id) | obsolete (explanation) | won't-fix (explanation) | not-applicable (explanation)

### Optional Keys
- **Parent:** issue-id (for decomposed sub-issues, renamed from "Created From")

### Removed Keys
- **Completed** - determined by Last Updated on closed issues
- **Version** - determined by parent folder name
- **Tokens Used** - removed
- **Started** - removed
- **Reason** - folded into Resolution value as parenthetical
- **Decomposed At** - removed
- **Decomposed** - removed
- **Duplicate Of** - folded into Resolution as "duplicate (issue-id)"
- **Completed At** - removed (variant of Completed)
- **Assignee** - removed
- **Priority** - removed
- **Worktree** - removed
- **Merged** - removed
- **Commit** - removed
- **Note** - removed
- **Scope Note** - removed
- **Completion Notes** - removed
- **Closed Reason** - removed
- **Obsolete Reason** - removed
- **Abandoned** - removed

## Satisfies
None - infrastructure cleanup

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - STATE.md files are read by CAT skills/scripts only
- **Mitigation:** Migration script processes all files atomically; git provides rollback

## Files to Modify
- All ~361 issue STATE.md files under `.claude/cat/issues/`
- `.claude/cat/conventions/state-schema.md` (new - documents the schema)
- STATE.md template in skill add (references in create-issue.py)

## Execution Steps
1. **Step 1:** Create `.claude/cat/conventions/state-schema.md` documenting the standardized schema
   - Files: `.claude/cat/conventions/state-schema.md`
2. **Step 2:** Write a Python migration script that processes all issue STATE.md files:
   - Read each file, parse keys
   - Add missing mandatory keys (Dependencies: [], Blocks: [], Last Updated with file git date)
   - Add Resolution: implemented for closed issues missing it
   - Rename "Created From" to "Parent"
   - Fold "Reason" into Resolution parenthetical
   - Fold "Duplicate Of" into Resolution as "duplicate (issue-id)"
   - Fold "Closed Reason"/"Obsolete Reason"/"Abandoned" into Resolution parenthetical
   - Remove all non-standard keys (Completed, Version, Tokens Used, Started, Decomposed At, Decomposed, Completed At, Assignee, Priority, Worktree, Merged, Commit, Note, Scope Note, Completion Notes)
   - Preserve any content after the key section (Sub-issues tables, Summary sections, etc.)
   - Files: `scripts/migrate-state-schema.py`
3. **Step 3:** Run migration script on all STATE.md files
4. **Step 4:** Update the STATE.md template in create-issue.py to include Blocks: [] field
   - Files: plugin scripts that generate STATE.md
5. **Step 5:** Verify migration results - spot check 10+ files across different categories
6. **Step 6:** Commit all changes

## Success Criteria
- [ ] All issue STATE.md files have exactly the mandatory keys
- [ ] No issue STATE.md contains any removed key
- [ ] All closed issues have a Resolution field
- [ ] All Resolution values use standardized format
- [ ] Parent key used where Created From existed
- [ ] Blocks key present in all files (may be empty)
- [ ] Schema convention document exists