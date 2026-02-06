# Plan: fix-merge-subagent-worktree-cd

## Problem
The work-merge subagent cd'd into the worktree directory, then ran git worktree remove which deleted
the directory under it, corrupting all shell sessions (exit code 1 on every command). Root cause:
git-merge-linear skill assumes it runs FROM inside the worktree (pwd-based), and work-merge Step 3
heading says "Rebase inside the worktree" which reinforces cd behavior.

## Satisfies
None - M465 prevention

## Root Cause
- git-merge-linear/SKILL.md uses `WORKTREE_PATH=$(pwd)` and checks `if pwd == MAIN_REPO` (lines 52-68)
- Prerequisite says "You are in the issue worktree" (line 52)
- Steps 5 and Single Command Version use `cd "$MAIN_REPO"` before cleanup (lines 223, 276)
- work-merge/SKILL.md Step 3 heading says "Rebase inside the worktree" (ambiguous)

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Merge operations could break if git -C paths are wrong
- **Mitigation:** Test with a real worktree merge after changes

## Files to Modify
- plugin/skills/git-merge-linear/SKILL.md - Convert from pwd-based to git -C based operations
- plugin/skills/work-merge/SKILL.md - Clarify Step 3 heading
- plugin/agents/work-merge.md - Verify git-merge-linear is still appropriate in frontmatter

## Acceptance Criteria
- [ ] git-merge-linear no longer requires running FROM inside worktree
- [ ] All git operations use git -C instead of assuming pwd
- [ ] No `cd` into worktree paths anywhere in the skill
- [ ] work-merge Step 3 heading clarified to not suggest cd
- [ ] All tests pass
- [ ] No regressions

## Execution Steps
1. **Step 1:** Read current git-merge-linear/SKILL.md and work-merge/SKILL.md
   - Files: plugin/skills/git-merge-linear/SKILL.md, plugin/skills/work-merge/SKILL.md, plugin/agents/work-merge.md
2. **Step 2:** Refactor git-merge-linear to accept WORKTREE_PATH as parameter instead of using pwd
   - Replace `WORKTREE_PATH=$(pwd)` with parameter-based detection
   - Replace all `cd` commands with `git -C $WORKTREE_PATH` equivalents
   - Update prerequisite from "You are in the issue worktree" to "WORKTREE_PATH is set"
   - Remove `cd "$MAIN_REPO"` from cleanup step, use `git -C` or absolute paths
   - Files: plugin/skills/git-merge-linear/SKILL.md
3. **Step 3:** Update work-merge Step 3 heading
   - Change "Rebase inside the worktree" to "Rebase task branch onto base (no cd)"
   - Files: plugin/skills/work-merge/SKILL.md
4. **Step 4:** Run all tests
   - Command: python3 /workspace/run_tests.py

## Success Criteria
- [ ] No `cd` into worktree paths in either skill
- [ ] git-merge-linear works when invoked from any directory
- [ ] All tests pass
