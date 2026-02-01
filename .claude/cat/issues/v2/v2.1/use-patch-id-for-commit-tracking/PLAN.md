# Plan: use-patch-id-for-commit-tracking

## Current State
CAT currently embeds `Issue ID: v{major}.{minor}-{issue-name}` in commit message footers to link commits to issues. This requires modifying commit messages and makes history rewriting complex.

## Target State
Remove Issue ID footers entirely. Track commits via STATE.md file history instead:

- STATE.md is updated in the **same commit** as implementation changes
- To find commits for an issue: `git log -- .claude/cat/issues/v2/v2.1/issue-name/STATE.md`
- No commit hashes stored in STATE.md = no maintenance after rebase

**Key insight:** The git history of STATE.md itself tracks implementation commits. No need to store hashes.

### Finding Commits for an Issue

```bash
# Find all commits that touched this issue
git log --oneline -- .claude/cat/issues/v2/v2.1/issue-name/

# Find the implementation commit (last commit that set status to completed)
git log --oneline -1 -- .claude/cat/issues/v2/v2.1/issue-name/STATE.md
```

### Rule: STATE.md in Same Commit

When implementation changes, STATE.md **must** be updated in the **same commit**.
This ensures `git log -- STATE.md` returns the exact implementation commits.

## Satisfies
None - infrastructure/architectural improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Removes Issue ID footer from commit message format
- **Mitigation:** Simple approach - just remove footers and document git log lookup

## Files to Modify
- plugin/concepts/commit-types.md - Remove Issue ID footer references, document git log lookup
- plugin/skills/git-commit/SKILL.md - Remove Issue ID footer requirement
- plugin/skills/git-squash/SKILL.md - Remove Issue ID footer requirement
- plugin/skills/work/commit-rules.md - Remove Issue ID footer requirement
- plugin/templates/state.md - Remove hash format, show simple "Resolution: implemented"

## Acceptance Criteria
- [ ] No Issue ID in commit messages - footer removed from format
- [ ] All tests still pass
- [ ] Documentation shows how to find commits via git log
- [ ] STATE.md uses simple "Resolution: implemented" (no hash)

## Execution Steps
1. **Step 1:** Update commit-types.md
   - Files: plugin/concepts/commit-types.md
   - Remove Issue ID footer documentation
   - Add "Finding Commits by Issue" section showing git log approach
   - Verify: Documentation is clear

2. **Step 2:** Remove Issue ID footer from commit rules
   - Files: plugin/skills/git-commit/SKILL.md, plugin/skills/work/commit-rules.md
   - Remove "Issue ID:" footer requirement entirely
   - Verify: No footer references remain

3. **Step 3:** Update git-squash skill
   - Files: plugin/skills/git-squash/SKILL.md
   - Remove Issue ID from examples
   - Verify: Clean examples without footers

4. **Step 4:** Update STATE.md template
   - Files: plugin/templates/state.md
   - Use simple "Resolution: implemented" (no hash)
   - Document that git log tracks implementation history
   - Verify: Template is simplified
