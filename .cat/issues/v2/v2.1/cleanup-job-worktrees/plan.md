# Plan

## Goal

Ensure `cat:work-implement` cleans up isolated implementation subagent job branches and worktrees after their
commits have been merged back into the parent issue branch, preventing stale `*-jobN` branches/worktrees like the
ones left behind by `2.1-review-skill-output-rendering`.

## Pre-conditions

- [ ] The issue worktree is based on current `v2.1`.
- [ ] The stale `2.1-review-skill-output-rendering-job1/job2/job3` artifacts have been inspected enough to confirm
  they are merged job worktrees rather than active work.

## Post-conditions

- [ ] The cause is documented in the implementation: `work-implement` merges isolated subagent branches but did not
  require post-merge job branch/worktree cleanup.
- [ ] `cat:work-implement` requires cleanup of each isolated subagent worktree and branch after a successful merge.
- [ ] Cleanup guidance covers both single-subagent and parallel job execution paths.
- [ ] Cleanup is sequenced only after the job branch has been successfully merged into the parent issue branch.
- [ ] A regression test proves the skill instructions require subagent worktree and branch cleanup after merge.
- [ ] `mvn -f client/pom.xml verify -e` passes.

## Jobs

### Job 1: Add regression coverage

- Locate the instruction regression tests that validate `work-implement/first-use.md` workflow requirements.
- Add or update a test that fails when `work-implement` does not explicitly require post-merge cleanup of isolated
  subagent worktrees and branches.
- Cover both single-subagent and parallel job paths if the existing test structure makes that practical; otherwise
  make the assertion broad enough to require cleanup language in both sections.
- Run the targeted test and confirm it fails before changing the skill instructions.

### Job 2: Update work-implement cleanup instructions

- Update `client/plugin/skills/common/work-implement/first-use.md`.
- After a successful single-subagent fast-forward merge, require removing the subagent worktree and deleting the
  subagent branch.
- After each successful parallel job fast-forward merge, require removing that job's subagent worktree and deleting
  that job's subagent branch before incrementing `NEXT_MERGE`.
- Require cleanup to be skipped only when merge fails, so failed jobs remain available for diagnosis.
- Preserve existing branch validation and merge-order constraints.

### Job 3: Verify and close

- Run the targeted regression test and confirm it passes.
- Run `mvn -f client/pom.xml verify -e`.
- Update `index.json` to closed/progress 100 in the same implementation commit.
