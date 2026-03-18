# Plan: remove-cat-branch-point-references

## Goal

Remove all remaining references to `cat-branch-point` from source code, skills, tests, and session instructions. The
concept was removed but residual references were left behind.

## Satisfies

None — cleanup of removed concept

## Context: Current State

The `cat-branch-point` file was a worktree marker written by `work-prepare` into a worktree's git directory. The concept
has been removed, but 26 non-issue files still reference it:

**Java source (10 files):**
- `client/src/main/java/io/github/cowwoc/cat/hooks/CatMetadata.java` — `BRANCH_POINT_FILE` constant
- `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnUnsquashedApproval.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockWrongBranchCommit.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/VerifyStateInCommit.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/WarnMainWorkspaceCommit.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebase.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforcePluginFileIsolation.java`

**Java tests (6 files):**
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockWrongBranchCommitTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforcePluginFileIsolationTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/RecordLearningTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/VerifyStateInCommitTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnMainWorkspaceCommitTest.java`

**Plugin skills/docs (9 files):**
- `plugin/concepts/error-handling.md`
- `plugin/skills/cleanup/first-use.md`
- `plugin/skills/git-rebase-agent/first-use.md`
- `plugin/skills/git-squash-agent/first-use.md`
- `plugin/skills/learn/phase-prevent.md`
- `plugin/skills/recover-from-drift/first-use.md`
- `plugin/skills/stakeholder-review-agent/first-use.md`
- `plugin/skills/work-prepare-agent/first-use.md`
- `plugin/skills/work-with-issue-agent/first-use.md`

**Bats tests (1 file):**
- `tests/worktree-isolation.bats`

Closed issue PLANs also reference it but are historical records and must not be modified.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Several hooks use `cat-branch-point` as the mechanism to detect whether the current directory is inside
  a CAT worktree. Removing the references requires understanding what replaced the concept and updating detection logic
  accordingly.
- **Mitigation:** Read each file to understand how `cat-branch-point` is used before modifying. Ensure the replacement
  worktree detection mechanism is consistent across all hooks. Run full test suite after changes.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/CatMetadata.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnUnsquashedApproval.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockWrongBranchCommit.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/VerifyStateInCommit.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/WarnMainWorkspaceCommit.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSessionInstructions.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitRebase.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforcePluginFileIsolation.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockWrongBranchCommitTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforcePluginFileIsolationTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/RecordLearningTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/VerifyStateInCommitTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnMainWorkspaceCommitTest.java`
- `plugin/concepts/error-handling.md`
- `plugin/skills/cleanup/first-use.md`
- `plugin/skills/git-rebase-agent/first-use.md`
- `plugin/skills/git-squash-agent/first-use.md`
- `plugin/skills/learn/phase-prevent.md`
- `plugin/skills/recover-from-drift/first-use.md`
- `plugin/skills/stakeholder-review-agent/first-use.md`
- `plugin/skills/work-prepare-agent/first-use.md`
- `plugin/skills/work-with-issue-agent/first-use.md`
- `tests/worktree-isolation.bats`

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Investigate replacement mechanism

1. **Determine what replaced cat-branch-point for worktree detection**
   - Files: `WorkPrepare.java`, `CatMetadata.java`
   - Identify the current mechanism for detecting whether a directory is inside a CAT worktree
   - Document the replacement approach to use consistently across all files

### Wave 2: Update Java source and tests

1. **Remove BRANCH_POINT_FILE constant and update CatMetadata**
   - Files: `CatMetadata.java`

2. **Update worktree detection in hooks**
   - Files: `EnforcePluginFileIsolation.java`, `BlockWrongBranchCommit.java`, `VerifyStateInCommit.java`,
     `WarnMainWorkspaceCommit.java`, `WarnUnsquashedApproval.java`
   - Replace `cat-branch-point`-based detection with the current mechanism

3. **Update utility classes**
   - Files: `GitRebase.java`, `RecordLearning.java`, `WorkPrepare.java`

4. **Update all corresponding tests**
   - Files: All 6 test files
   - Update test setup/assertions to match the new detection mechanism

5. **Run full test suite**
   - `mvn -f client/pom.xml test`

### Wave 3: Update plugin skills and docs

1. **Update skill files**
   - Files: All 9 plugin skill/concept files
   - Replace references to `cat-branch-point` with the current worktree detection description

2. **Update session instructions**
   - Files: `InjectSessionInstructions.java`
   - Remove or update injected text that mentions `cat-branch-point`

### Wave 4: Update Bats tests

1. **Update or remove worktree-isolation.bats**
   - Files: `tests/worktree-isolation.bats`
   - Update tests to validate the current worktree detection mechanism instead of `cat-branch-point`

### Wave 5: Store target branch in STATE.md

The `cat-branch-point` file previously provided the fork-point commit hash. Skills like `cleanup/first-use.md` need
the **target branch name** to run `git branch --merged`. Git does not natively track which branch a branch was forked
from, so the target branch must be stored explicitly. STATE.md is version-controlled and pushed to remote, making it
collaborative.

1. **Update `WorkPrepare.java` to write `Target Branch:` to STATE.md**
   - Add `targetBranch` parameter to `updateStateMd()` and `createStateMd()`
   - Write `- **Target Branch:** <branch-name>` field to STATE.md
   - For `updateStateMd()`: append the field if not already present
   - For `createStateMd()`: include in the template

2. **Update `cleanup/first-use.md` to read `Target Branch:` from STATE.md**
   - Replace naming-convention derivation (`grep -oE '^[0-9]+\.[0-9]+'`) with STATE.md read
   - If `Target Branch:` is absent, skip `--merged` check with a warning
   - If present, pass it to `git branch --merged "$TARGET_BRANCH"`

3. **Run full test suite**
   - `mvn -f client/pom.xml test`

## Post-conditions

- [ ] Zero references to `cat-branch-point` in non-closed-issue files (`grep -r "cat-branch-point"` returns only
  closed issue PLANs)
- [ ] All hooks that need worktree detection use the current replacement mechanism consistently
- [ ] `WorkPrepare` writes `Target Branch:` to STATE.md at worktree creation
- [ ] `cleanup/first-use.md` reads `Target Branch:` from STATE.md for `git branch --merged`
- [ ] `mvn -f client/pom.xml test` passes
- [ ] Bats tests pass
