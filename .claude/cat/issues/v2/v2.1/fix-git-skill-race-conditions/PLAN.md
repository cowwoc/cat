# Plan: fix-git-skill-race-conditions

## Goal
Fix race conditions across git-* skills where base branch references can advance between operations,
causing stale files to leak into commits or incorrect merge results.

## Problem
Multiple git-* skills call `git rev-parse` on the base branch at different points in their workflow.
If the base branch advances between calls (e.g., another session commits to v2.1), operations use
inconsistent refs, causing stale files to appear in commits or incorrect merge parents.

**Audit findings (ordered by severity):**

| Skill | Severity | Issue |
|-------|----------|-------|
| git-squash | CRITICAL | `git rev-parse` called separately for rebase and commit-tree parent |
| git-merge-linear | CRITICAL | BASE_BRANCH ref used at lines 157, 173, 190, 241-282 without pinning |
| git-rebase | MEDIUM | BASE_BRANCH can advance between read and rebase at lines 125-132 |
| git-amend | LOW | Push status check race at lines 31-44 |
| git-rewrite-history | LOW | Concurrent gc could remove reflog at lines 148-163 |
| git-commit | NONE | No race conditions found |

## Satisfies
None - infrastructure bugfix

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Changes affect core git workflows used by all /cat:work executions
- **Mitigation:** Pin-once pattern is simpler than current approach. Each skill's safety pattern unchanged.

## Files to Modify
- plugin/skills/git-squash/content.md - Pin base reference before rebase, reuse for commit-tree parent
- plugin/skills/git-merge-linear/content.md - Pin base reference once, reuse across all merge operations
- plugin/skills/git-rebase/content.md - Pin base reference before rebase operation
- plugin/skills/git-amend/content.md - Add note about push status race (documentation only)

## Acceptance Criteria
- [ ] git-squash: Base branch reference resolved once, reused for rebase and commit-tree
- [ ] git-merge-linear: Base branch reference resolved once, reused across verify/merge/cleanup steps
- [ ] git-rebase: Base branch reference resolved once before rebase
- [ ] git-amend: Post-amend TOCTOU detection implemented to warn if race occurred
- [ ] Pin-once pattern applied consistently: `BASE=$(git rev-parse <base-branch>)` before first use
- [ ] No race window between any operations that depend on the same base ref
- [ ] Critical Rules warning added to each modified skill
- [ ] All existing tests pass with no regressions
- [ ] git-merge-linear deterministic workflow extracted to plugin/scripts/git-merge-linear.sh
- [ ] git-squash quick workflow extracted to plugin/scripts/git-squash-quick.sh
- [ ] Scripts produce JSON output for machine parsing
- [ ] Skills reference scripts while retaining full documentation
- [ ] All 4 git operation scripts produce JSON output with status field
- [ ] Each failure mode has a distinct status code (CONFLICT, FF_FAILED, NOT_LINEAR, etc.)
- [ ] Error JSON includes enough context for agent recovery (backup_branch, conflicting_files, etc.)
- [ ] Scripts fail fast - no attempt at recovery, just clear error reporting

## Execution Steps
1. **Step 1:** Fix git-squash Quick Workflow
   - Files: plugin/skills/git-squash/content.md
   - Add pin step before rebase: `BASE=$(git rev-parse <base-branch>)`
   - Change rebase to use pinned ref: `git rebase $BASE`
   - Change commit-tree to use pinned ref: `git commit-tree $TREE -p $BASE -m "$MESSAGE"`
   - Remove separate `git rev-parse` call in commit-tree step
   - Add comment explaining why pinning is necessary (race condition prevention)
2. **Step 2:** Fix git-squash Interactive Rebase Workflow
   - Files: plugin/skills/git-squash/content.md
   - Pin base reference before any operations
   - Ensure BASE_COMMIT uses the pinned value
3. **Step 3:** Add Critical Rules warning to git-squash
   - Files: plugin/skills/git-squash/content.md
   - Add: "MANDATORY: Pin base branch reference before rebase. Do NOT call git rev-parse on the base
     branch separately for rebase and commit-tree."
4. **Step 4:** Fix git-merge-linear base ref pinning
   - Files: plugin/skills/git-merge-linear/content.md
   - Add pin step at start of merge workflow: `BASE=$(git rev-parse <base-branch>)`
   - Replace all subsequent `<base-branch>` references with `$BASE` in merge, verify, and cleanup steps
   - Ensure merge-base check, fast-forward, and post-merge verification all use same pinned ref
5. **Step 5:** Add Critical Rules warning to git-merge-linear
   - Files: plugin/skills/git-merge-linear/content.md
   - Add: "MANDATORY: Pin base branch reference at workflow start. All merge operations must use the
     pinned SHA, not the branch name."
6. **Step 6:** Fix git-rebase base ref pinning
   - Files: plugin/skills/git-rebase/content.md
   - Pin BASE_BRANCH ref before rebase operation at lines 125-132
   - Use pinned ref for the rebase command
7. **Step 7:** Add post-amend TOCTOU detection to git-amend
   - Files: plugin/skills/git-amend/content.md
   - Record OLD_HEAD before amend operation
   - After amend completes, check if OLD_HEAD was pushed to remote during the amend window
   - If race detected, warn user that force-with-lease push is needed
   - This converts a silent race into an immediately detected condition
8. **Step 8:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml test
9. **Step 9:** Extract git-merge-linear deterministic workflow to script
   - Create: plugin/scripts/git-merge-linear.sh
   - Implements: pin base, check divergence, check deletions, squash, merge-base verify, fast-forward, verify, cleanup
   - Update git-merge-linear/content.md to reference script
10. **Step 10:** Extract git-squash quick workflow to script
    - Create: plugin/scripts/git-squash-quick.sh
    - Implements: pin base, rebase, backup, commit-tree, verify, cleanup
    - Update git-squash/content.md to reference script
11. **Step 11:** Run tests to verify no regressions
    - Run: mvn -f hooks/pom.xml test
12. **Step 12:** Create git-rebase-safe.sh and git-amend-safe.sh scripts
    - Create: plugin/scripts/git-rebase-safe.sh with backup, conflict detection, fail-fast
    - Create: plugin/scripts/git-amend-safe.sh with TOCTOU detection, fail-fast
    - Update skills to reference scripts
13. **Step 13:** Enhance existing scripts with additional fail-fast error paths
    - Enhance: plugin/scripts/git-merge-linear.sh with specific error JSON for each failure mode
    - Enhance: plugin/scripts/git-squash-quick.sh with rebase conflict and verify failure handling

## Success Criteria
- [ ] Base reference pinned once in git-squash (both workflows)
- [ ] Base reference pinned once in git-merge-linear (all operations)
- [ ] Base reference pinned once in git-rebase
- [ ] Post-amend TOCTOU detection implemented in git-amend
- [ ] Warnings documented in Critical Rules sections
- [ ] All existing tests pass with no regressions
