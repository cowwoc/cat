# Plan: use-patch-id-for-commit-tracking

## Current State
CAT currently embeds `Issue ID: v{major}.{minor}-{issue-name}` in commit message footers to link commits to issues. This requires modifying commit messages and makes history rewriting complex.

## Target State
Use `git patch-id --stable` to compute a stable hash of each commit's diff content, then store the mapping between patch-id and issue metadata separately. Commit messages no longer need the Issue ID footer.

## Satisfies
None - infrastructure/architectural improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Removes Issue ID footer from commit message format; existing repos may have commits with old format
- **Mitigation:** Migration script to backfill patch-id mappings for existing commits; backwards-compatible lookup that checks both formats

## Files to Modify
- plugin/concepts/commit-types.md - Remove Issue ID footer references
- plugin/concepts/issue-resolution.md - Update commit tracking documentation
- plugin/skills/git-commit/SKILL.md - Remove Issue ID footer requirement
- plugin/skills/git-squash/SKILL.md - Remove Issue ID footer requirement
- plugin/skills/work/commit-rules.md - Remove Issue ID footer requirement
- plugin/skills/work/phase-merge.md - Remove Issue ID footer from commit template
- plugin/hooks/warn-unsquashed-approval.sh - Change to use patch-id lookup
- plugin/templates/state.md - Update commit lookup commands
- plugin/templates/changelog.md - Update commit lookup commands
- plugin/scripts/lib/patch-id-mapping.sh (NEW) - Utility for patch-id operations

## Acceptance Criteria
- [ ] Behavior unchanged - issues still reliably track to commits
- [ ] All tests still pass
- [ ] No Issue ID in commit messages - footer removed from format
- [ ] patch-id --stable mapping works - can map issue metadata to commits

## Execution Steps
1. **Step 1:** Create patch-id mapping utility script
   - Files: plugin/scripts/lib/patch-id-mapping.sh
   - Functions: compute_patch_id(), store_mapping(), lookup_by_patch_id()
   - Verify: Script sources correctly

2. **Step 2:** Create mapping storage format
   - Store in .claude/cat/patch-id-map.json or similar
   - Format: { "patch-id": { "issue": "v2.1-issue-name", "commit": "abc123" } }
   - Verify: Can write and read mappings

3. **Step 3:** Update commit rules to remove Issue ID footer
   - Files: plugin/skills/git-commit/SKILL.md, plugin/skills/work/commit-rules.md
   - Remove "Issue ID:" footer requirement
   - Add step to record patch-id mapping after commit
   - Verify: No footer references remain

4. **Step 4:** Update hook to use patch-id lookup
   - Files: plugin/hooks/warn-unsquashed-approval.sh
   - Replace grep for "Issue ID:" with patch-id lookup
   - Verify: Hook still detects unsquashed commits

5. **Step 5:** Update documentation and templates
   - Files: All concept files, templates
   - Replace git log --grep="Issue ID:" with patch-id lookup commands
   - Verify: Documentation consistent

6. **Step 6:** Create migration script for existing repos
   - Scan git history for commits with "Issue ID:" or "Task ID:" footers
   - Compute patch-id for each and store mapping
   - Verify: Existing commits still resolvable
