# Plan: Consolidate Merge Scripts into Java

## Goal
Replace three overlapping bash merge scripts with a single Java `MergeAndCleanup` CLI tool that handles the full
merge-and-cleanup workflow deterministically, eliminating the 7+ LLM failures observed per merge due to hook
workarounds and state management errors.

## Satisfies
None (infrastructure optimization from session analysis)

## Background

Three bash scripts currently handle overlapping merge responsibilities:

| Script | Lines | Role |
|--------|-------|------|
| `merge-and-cleanup.sh` | 393 | FF merge, worktree removal, branch deletion, lock release |
| `git-merge-linear.sh` | 185 | FF merge with squash, divergence checks, suspicious deletion detection |
| `git-merge-linear-optimized.sh` | 210 | FF merge requiring pre-squashed single commit |

A typical merge session requires 12 Bash tool calls with 7 hook-blocked failures because the LLM must navigate:
- M205 (checkout restriction in main worktree)
- M464 (cwd-inside-worktree safety)
- reset --hard confirmation gates
- Branch deletion of checked-out branches
- STATE.md schema trial-and-error

All of these are deterministic operations that don't require LLM judgment.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Three scripts are referenced by multiple skills; must update all callers
- **Mitigation:** New Java tool accepts same arguments; skill updates are mechanical find-and-replace

## Files to Modify
- `client/src/main/java/.../skills/MergeAndCleanup.java` - New CLI tool consolidating all three scripts
- `client/src/test/java/.../test/MergeAndCleanupTest.java` - Tests for the new tool
- `plugin/skills/work-merge-first-use/SKILL.md` - Update to invoke Java tool instead of merge-and-cleanup.sh
- `plugin/skills/git-merge-linear/SKILL.md` - Update to invoke Java tool
- `plugin/skills/git-merge-linear-first-use/SKILL.md` - Update silent execution invocation
- `plugin/skills/skill-builder-first-use/SKILL.md` - Update merge invocation references

## Files to Delete
- `plugin/scripts/merge-and-cleanup.sh` - Replaced by Java
- `plugin/scripts/git-merge-linear.sh` - Replaced by Java
- `plugin/scripts/git-merge-linear-optimized.sh` - Replaced by Java

## Acceptance Criteria
- [ ] Single `merge-and-cleanup` Java CLI tool handles: divergence check, suspicious deletion detection, ff merge
  (push + fallback to merge --ff-only), worktree removal (with cwd safety), branch deletion, lock release, backup
  branch cleanup
- [ ] Tool accepts: `--project-dir`, `--issue-id`, `--session-id`, `--worktree` (optional, auto-detected)
- [ ] Tool outputs JSON with status, commit_sha, base_branch, worktree_removed, branch_deleted, lock_released
- [ ] All three bash scripts deleted
- [ ] All skill files updated to invoke Java tool
- [ ] Merge completes in 1 tool call (no hook failures, no retries)
- [ ] STATE.md update included in the merge workflow (closed, 100%, implemented)
- [ ] Build passes with all tests green

## Execution Steps
1. **Create MergeAndCleanup.java:** Implement CLI tool with subcommands mirroring the consolidated workflow:
   validate args, detect worktree/base branch, check divergence, check suspicious deletions, verify ff-eligible,
   perform ff merge (git push . HEAD:base fallback to git merge --ff-only from main repo), cd to project dir,
   remove worktree, delete branch, release lock, clean up squash backups. Use ProcessRunner for git commands.
2. **Create MergeAndCleanupTest.java:** Test key scenarios: successful merge, diverged base, dirty worktree,
   missing cat-base, suspicious deletions
3. **Update skill files:** Replace bash script invocations with Java CLI invocations in work-merge, git-merge-linear,
   and skill-builder skills
4. **Delete bash scripts:** Remove the three replaced scripts
5. **Run tests:** `mvn -f client/pom.xml test` — all tests pass

## Success Criteria
- [ ] Merge workflow completes in exactly 1 Bash tool call from skill
- [ ] No hook-blocked failures during merge (Java bypasses per-command hooks)
- [ ] All existing merge functionality preserved (divergence check, suspicious deletion, backup cleanup)
