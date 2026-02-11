# Plan: port-cleanup-display-to-java

## Goal
Add data gathering logic to GetCleanupOutput.java so it can fully replace get-cleanup-display.py, which currently
handles both data gathering (git commands, lock file parsing) and display formatting.

## Current State
`GetCleanupOutput.java` only handles display formatting via `getSurveyOutput()`, `getPlanOutput()`, and
`getVerifyOutput()` methods that require pre-gathered data as parameters. The Python script `get-cleanup-display.py`
handles both data gathering (git worktree list, lock file JSON parsing, branch listing, stale remote detection) and
display formatting.

## Target State
Java class handles complete cleanup workflow: data gathering from git and lock files, plus display formatting.
No Python subprocess spawning for cleanup display.

## Satisfies
Parent: 2.1-port-display-scripts

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Output format must remain character-for-character identical
- **Mitigation:** Diff-based comparison of Python vs Java output for each phase (survey, plan, verify)

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCleanupOutput.java` - Add data gathering methods
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/GetCleanupOutputTest.java` - Add/update tests

## Acceptance Criteria
- [ ] GetCleanupOutput.java gathers worktree data identically to Python's `get_worktrees()` (parses `git worktree list
      --porcelain` output with state, HEAD, branch)
- [ ] Lock file parsing produces identical results to Python's `get_locks()` (reads JSON lock files, calculates age)
- [ ] Branch listing matches Python's `get_cat_branches()` (filters by regex for release/, worktree, digit patterns)
- [ ] Stale remote detection matches Python's `get_stale_remotes()` (prune, filter 1-7 day old branches with
      author/relative time)
- [ ] Survey phase output is character-for-character identical to Python version
- [ ] Plan phase output is character-for-character identical to Python version
- [ ] Verify phase output is character-for-character identical to Python version
- [ ] No Python subprocess spawning remains for cleanup display
- [ ] All tests pass (`mvn -f hooks/pom.xml test`)

## Execution Steps
1. **Read `get-cleanup-display.py`** to catalog all data gathering logic per phase
2. **Read existing `GetCleanupOutput.java`** to understand current formatting methods and data records
3. **Read `.claude/cat/conventions/java.md`** for coding conventions
4. **Implement `gatherWorktrees()`** - parse `git worktree list --porcelain` output
5. **Implement `gatherLocks()`** - read JSON lock files from `.claude/cat/locks/`, calculate age
6. **Implement `gatherBranches()`** - filter git branches by CAT patterns
7. **Implement `gatherStaleRemotes()`** - fetch --prune, filter by age and author
8. **Add orchestration method** that gathers all data and calls existing formatting methods
9. **Compare output** of Java vs Python for each phase with real project data
10. **Write tests** covering: empty state, populated state, stale locks, multiple worktrees
11. **Run tests:** `mvn -f hooks/pom.xml test`

## Success Criteria
- [ ] Data gathering methods produce identical data to Python equivalents
- [ ] All three phase outputs match Python character-for-character
- [ ] All existing and new tests pass
