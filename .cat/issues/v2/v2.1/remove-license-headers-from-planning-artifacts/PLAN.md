# Plan: Remove License Headers from Planning Artifacts

## Current State
Nine PLAN.md and STATE.md files under `.cat/issues/` contain HTML license comment banners. Per
`license-header.md`, files under `.cat/` are explicitly exempt (they are planning artifacts and runtime data,
not source code).

## Target State
No files under `.cat/` (excluding worktrees) contain license headers. The `.cat/` directory contains only
planning content, with no embedded license noise.

## Satisfies
None (housekeeping / convention compliance)

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — removing HTML comments from markdown has no functional impact
- **Mitigation:** Grep after removal to confirm zero remaining headers

## Files to Modify
- `.cat/issues/v2/v2.1/add-license-headers/PLAN.md` — remove header
- `.cat/issues/v2/v2.1/centralize-verbatim-output-skill/PLAN.md` — remove header
- `.cat/issues/v2/v2.1/centralize-verbatim-output-skill/STATE.md` — remove header
- `.cat/issues/v2/v2.1/fix-cleanup-survey-missing-worktrees/PLAN.md` — remove header
- `.cat/issues/v2/v2.1/fix-cleanup-survey-missing-worktrees/STATE.md` — remove header
- `.cat/issues/v2/v2.1/fix-git-rebase-safe-false-positive-when-base-branch-advances/PLAN.md` — remove header
- `.cat/issues/v2/v2.1/fix-git-rebase-safe-false-positive-when-base-branch-advances/STATE.md` — remove header
- `.cat/issues/v2/v2.1/refactor-config-skill-lazy-screens/PLAN.md` — remove header
- `.cat/issues/v2/v2.1/refactor-config-skill-lazy-screens/STATE.md` — remove header

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
1. **Step 1:** For each file listed in "Files to Modify", remove the HTML license comment block at the top (the `<!--
   ... -->` block containing the copyright notice). Do not modify any other content.
2. **Step 2:** Run `grep -rl "Licensed under the CAT Commercial License" .cat/ | grep -v /worktrees/` to verify
   zero matches.
3. **Step 3:** Commit with message: `refactor: remove license headers from .cat planning artifacts`


## Post-conditions
- [ ] `grep -rl "Licensed under the CAT Commercial License" .cat/ | grep -v /worktrees/` returns no output
- [ ] All 9 files are free of HTML license comment blocks
- [ ] All other file content is unchanged
