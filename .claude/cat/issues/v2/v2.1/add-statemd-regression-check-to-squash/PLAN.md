# Plan: Add Concurrent Modification Detection to GitSquash

## Goal

Add concurrent modification detection to GitSquash.java that warns when files were modified on both the base branch
and the issue branch. This is a general-purpose safety check that helps callers verify auto-resolved rebase results.
Replaces the bash script git-squash-quick.sh with a Java implementation using the commit-tree approach.

## Satisfies

None (infrastructure/prevention from M363)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Warnings for intentional concurrent modifications may be noisy
- **Mitigation:** Warnings only (squash still succeeds); callers decide whether to act

## Files to Modify

- client/src/main/java/io/github/cowwoc/cat/hooks/util/GitSquash.java - Rewrite with commit-tree approach and
  concurrent modification detection
- client/src/test/java/io/github/cowwoc/cat/hooks/test/GitSquashTest.java - TestNG tests for all squash scenarios
- client/build-jlink.sh - Add git-squash launcher to HANDLERS
- plugin/skills/work-merge-first-use/SKILL.md - Update script path to Java launcher
- plugin/skills/work-with-issue-first-use/SKILL.md - Update script path to Java launcher
- plugin/skills/git-squash-first-use/SKILL.md - Update script path to Java launcher

## Files to Delete

- plugin/scripts/git-squash-quick.sh - Replaced by Java
- tests/hooks/git-squash-quick.bats - Replaced by TestNG

## Acceptance Criteria

- [x] GitSquash.java warns (in JSON output) when files are modified on both branches
- [x] GitSquash.java succeeds normally when only the issue branch modifies files
- [x] TestNG tests for concurrent modification detection, commit message validation, rebase conflicts, and squash
  verification
- [x] Skill SKILL.md files updated to reference Java launcher
- [x] Bash script and bats tests deleted

## Execution Steps

1. **Rewrite GitSquash.java:** Replace prohibited soft-reset approach with commit-tree approach. Add concurrent
   modification detection after rebase, comparing files modified on base branch (merge-base..base) with files modified
   on issue branch (base..HEAD). Report overlapping files as warnings in JSON output.
   - Files: client/src/main/java/io/github/cowwoc/cat/hooks/util/GitSquash.java
2. **Rewrite GitSquashTest.java:** Convert bats tests to TestNG with isolated temp git repos.
   - Files: client/src/test/java/io/github/cowwoc/cat/hooks/test/GitSquashTest.java
3. **Add launcher:** Register git-squash in build-jlink.sh HANDLERS array.
   - Files: client/build-jlink.sh
4. **Update skill references:** Change script paths from bash to Java launcher.
   - Files: plugin/skills/work-merge-first-use/SKILL.md, plugin/skills/work-with-issue-first-use/SKILL.md,
     plugin/skills/git-squash-first-use/SKILL.md
5. **Delete bash script and bats tests.**
   - Files: plugin/scripts/git-squash-quick.sh, tests/hooks/git-squash-quick.bats
