# Plan: improve-unsquashed-commit-detection

## Problem

WarnUnsquashedApproval uses a hard-coded `commitCount > 2` threshold to detect unsquashed commits.
This breaks when `/cat:git-squash` legitimately produces 3+ commits via squash-by-topic. The tests
are also environment-dependent because they run the hook against real git state.

## Satisfies

None (infrastructure improvement)

## Root Cause

The commit-count heuristic cannot distinguish "forgot to squash" from "intentionally multi-commit
after squash-by-topic." The check needs a signal that squashing *happened*, not a count of resulting
commits.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Marker file must be created atomically; tests must not depend on real git state
- **Mitigation:** Use `.git/cat-squashed` marker written by git-squash-quick.sh and interactive
  rebase workflow; mock git state in tests

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnUnsquashedApproval.java` — Replace
  commit-count check with marker-based check
- `plugin/scripts/git-squash-quick.sh` — Write `.git/cat-squashed` marker after successful squash
- `plugin/skills/git-squash/first-use.md` — Add marker creation to interactive rebase workflow
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java` — Fix
  environment-dependent tests to mock git state

## Acceptance Criteria

- [ ] WarnUnsquashedApproval checks for `.git/cat-squashed` marker instead of counting commits
- [ ] `/cat:git-squash` (both quick and interactive workflows) creates the marker after success
- [ ] Approval gate allows 3+ commits when marker is present (squash-by-topic)
- [ ] Approval gate warns when marker is absent and issue branch has commits
- [ ] Tests pass regardless of the worktree's actual commit count
- [ ] Uses "issue" terminology consistently (not "task")

## Execution Steps

1. **Add marker creation to git-squash-quick.sh:** After successful squash verification, write
   marker file at `$(git rev-parse --git-dir)/cat-squashed`
   - Files: `plugin/scripts/git-squash-quick.sh`

2. **Add marker creation to interactive rebase workflow docs:** Document that after successful
   verification step, the marker file should be created
   - Files: `plugin/skills/git-squash/first-use.md`

3. **Replace commit-count heuristic in WarnUnsquashedApproval:** Check for `.git/cat-squashed`
   marker. If present, allow. If absent and issue branch has commits, warn.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnUnsquashedApproval.java`

4. **Fix environment-dependent tests:** Mock git state or use test-specific git repos instead
   of relying on real worktree state
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/HookEntryPointTest.java`

5. **Run tests and verify all pass**
