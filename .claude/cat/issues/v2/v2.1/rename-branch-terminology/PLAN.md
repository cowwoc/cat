# Plan: rename-branch-terminology

## Goal

Rename all references to `base_branch`/`BASE_BRANCH`/`baseBranch`/`base-branch` and `worktree_branch`/`worktree branch`
to use consistent `source_branch`/`target_branch` terminology across plugin skills, concepts, agents, and Java client
code. The mapping depends on context:

- **`base_branch` → `target_branch`**: The branch being merged INTO (e.g., `main`, `v2.1`)
- **`worktree_branch` / issue branch → `source_branch`**: The branch being merged FROM (the issue/feature branch)

## Satisfies

None — infrastructure terminology cleanup.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** High file count (~40+ files), must preserve semantic correctness in each context, variable renames in
  Bash/Java must be consistent across all references
- **Mitigation:** Systematic file-by-file review, run all tests after changes, split into sub-tasks by file type

## Files to Modify

### Plugin Agents (Markdown)

- `plugin/agents/work-merge.md` — description and step references
- `plugin/agents/work-squash.md` — `BASE_BRANCH` variable, validation, rebase/squash commands
- `plugin/agents/work-verify.md` — metadata variable list

### Plugin Skills (Markdown)

- `plugin/skills/git-squash/first-use.md` — base branch references, worktree branch references
- `plugin/skills/git-merge-linear/first-use.md` — `BASE_BRANCH` variable, merge description
- `plugin/skills/git-rebase/first-use.md` — `BASE_BRANCH` variable, rebase description
- `plugin/skills/cleanup/first-use.md` — `BASE_BRANCH` variable, merge check
- `plugin/skills/stakeholder-review/first-use.md` — `BASE_BRANCH` variable, diff commands, worktree branch references
- `plugin/skills/skill-builder/first-use.md` — `BASE_BRANCH` reference
- `plugin/skills/work-merge/first-use.md` — `BASE_BRANCH` variable throughout, SKILL.md description
- `plugin/skills/work-merge/SKILL.md` — description text
- `plugin/skills/work-with-issue/first-use.md` — `BASE_BRANCH` variable (many references), merge prompt text
- `plugin/skills/work-prepare/first-use.md` — `BASE_BRANCH` variable, branch detection
- `plugin/skills/config/first-use.md` — merge strategy description
- `plugin/skills/work/first-use.md` — base branch references
- `plugin/skills/remove/first-use.md` — worktree/branch references
- `plugin/skills/statusline/first-use.md` — worktree/branch display

### Plugin Concepts (Markdown)

- `plugin/concepts/git-operations.md` — base branch merge instructions
- `plugin/concepts/error-handling.md` — `BASE_BRANCH` variable example
- `plugin/concepts/commit-types.md` — base branch references
- `plugin/concepts/work.md` — base branch isolation description
- `plugin/concepts/merge-and-cleanup.md` — extensive base branch references
- `plugin/concepts/agent-architecture.md` — worktree/branch cleanup reference
- `plugin/concepts/issue-resolution.md` — base branch reference

### Java Client Source

- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforcePluginFileIsolation.java` — error message text
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java` — class name may stay (describes
  purpose), but update Javadoc/comments
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetIssueCompleteOutput.java` — argument description
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` — method comments, variable names
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java` — method comments, variable names
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — comments, step descriptions
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitMergeLinear.java` — Javadoc
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/ExistingWorkChecker.java` — CLI help text
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java` — class Javadoc, method comments
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java` — comment
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GitSquash.java` — class Javadoc, method comments
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMainRebase.java` — comment

### Java Client Tests

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ExistingWorkCheckerTest.java` — variable names, branch names
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java` — comments, variable names
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetIssueCompleteOutputTest.java` — comments
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitRebaseSafeTest.java` — comments
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GitSquashTest.java` — comments
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java` — comments
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/MergeAndCleanupTest.java` — comments, variable names
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/VerifyStateInCommitTest.java` — branch name string
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` — comments
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforcePluginFileIsolationTest.java` — comments

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Rename variables in plugin agent files** — Update `BASE_BRANCH` → `TARGET_BRANCH` in work-merge.md,
   work-squash.md, work-verify.md
2. **Rename variables in plugin skill files** — Update `BASE_BRANCH` → `TARGET_BRANCH` in all skill first-use.md and
   SKILL.md files. Update natural language "base branch" → "target branch" and "worktree branch" → "source branch"
   where contextually appropriate
3. **Rename variables in plugin concept files** — Update all concept documentation
4. **Rename variables/comments in Java source files** — Update variable names (`baseBranch` → `targetBranch`), method
   parameters, Javadoc, and error messages in client source
5. **Update Java test files** — Rename variables, branch name strings, and comments in test classes
6. **Run all tests** — `mvn -f client/pom.xml test` to verify no regressions

## Post-conditions

- [ ] No references to `base_branch`, `BASE_BRANCH`, `baseBranch`, or `base-branch` remain in plugin/ or client/
      (except where "base" is used in a non-branch-terminology context like "database" or as a git merge-base concept)
- [ ] No references to `worktree_branch` or `worktree branch` (as a named concept for the source) remain
- [ ] All `TARGET_BRANCH`/`targetBranch` references refer to the branch being merged INTO
- [ ] All `SOURCE_BRANCH`/`sourceBranch` references refer to the branch being merged FROM
- [ ] All tests pass (`mvn -f client/pom.xml test` exits 0)
