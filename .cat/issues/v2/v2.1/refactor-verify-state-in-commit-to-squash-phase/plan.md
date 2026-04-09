# Plan: refactor-verify-state-in-commit-to-squash-phase

## Goal

Move the responsibility for closing `index.json` from individual commits to the squash phase. Currently,
`VerifyStateInCommit` blocks every `bugfix:`/`feature:` commit if `index.json` is not staged alongside
it — which forces developers to think about closure at each commit. Instead, the squash phase (run prior to
the approval gate) should auto-close `index.json` if it is not already closed, and
`VerifyStateInCommit` should validate that the issue is closed at the point the approval gate is presented
rather than at each individual commit.

## Parent Requirements

None

## Current State

`VerifyStateInCommit` is a `PreToolUse` hook on Bash that fires on any `git commit` matching
`bugfix:|feature:` pattern inside a CAT worktree. It:
1. Blocks the commit if `index.json` is not staged alongside the code changes.
2. Warns if `index.json` is staged but does not have `"status": "closed"`.

The squash phase (`git-squash-agent`) has no awareness of `index.json` closure.

## Target State

- `VerifyStateInCommit` no longer blocks individual `bugfix:`/`feature:` commits for missing `index.json`.
- The squash phase (`git-squash-agent` SKILL.md) gains a new step: after squashing commits by topic, check
  whether the issue's `index.json` has `"status": "closed"`. If not, update it to `"closed"` and stage it
  into the squash commit (or create a final `planning:` commit).
- `VerifyStateInCommit` is updated to validate that the issue is closed immediately before the approval gate
  is presented. It should block (not just warn) if the issue is not closed at that point, with a clear
  message instructing the agent to close it via `index.json`.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Squash phase may fail to close `index.json` in edge cases (e.g., corrupt JSON, wrong branch
  name), leaving an unclosed issue that `VerifyStateInCommit` then blocks.
- **Mitigation:** Keep `VerifyStateInCommit` as a hard block at the approval gate so the gate cannot be
  passed with an open issue. The squash phase provides the auto-fix; the hook provides the final guard.

## Files to Modify

- `plugin/skills/git-squash-agent/SKILL.md` — add a step after topic squashing: check
  `index.json` status; if not `"closed"`, update it to `"closed"` and include it in the squash or commit it
  separately before the approval gate.
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/VerifyStateInCommit.java` — remove the
  "block if index.json not staged" logic for individual commits; change trigger to fire at approval-gate
  presentation time and block if the issue is not closed.
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/VerifyStateInCommitTest.java` — update tests to
  match new behavior (no longer blocks individual commits; blocks approval gate when issue not closed).

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Update git-squash-agent SKILL.md

- Read `plugin/skills/git-squash-agent/SKILL.md` to understand the current squash steps.
- Before the squash reset (`git reset --soft`), resolve the issue's `index.json` path from the current
  branch name (same logic as `IssueDiscovery.branchToIndexJsonPath`). If the file exists and its `"status"`
  field is not `"closed"`, update it to `"closed"` and `git add` it so it is staged before the reset. This
  ensures `index.json` is absorbed into the squashed bugfix/feature commit rather than committed separately.

### Job 2: Update VerifyStateInCommit hook

- Remove the `IMPLEMENTATION_COMMIT_PATTERN` check that requires `index.json` to be staged alongside
  `bugfix:`/`feature:` commits.
- Change the hook trigger: instead of firing on any bugfix/feature `git commit`, fire when the approval gate
  AskUserQuestion is presented. Detect approval gate presentation by inspecting the tool call (e.g.,
  `AskUserQuestion` tool with question text matching the merge/approval pattern, or a Bash command that
  invokes the approval gate presentation step in `work-with-issue`). Look at how
  `EnforceMergeApprovalGate` detects its trigger for guidance.
- When triggered, derive the issue's `index.json` path from the current branch, read it, and block if
  `"status"` is not `"closed"`, with message: `"BLOCKED: Issue index.json must be 'closed' before presenting
  the approval gate. Run the squash step to auto-close it, or manually set index.json status to 'closed'
  and commit it."`
- Update tests: old tests for "blocks commit when index.json not staged" should be removed. New tests:
  "allows bugfix commit without index.json staged", "blocks approval gate when issue not closed",
  "allows approval gate when issue is closed".

### Job 3: Enable runtime E2E verification via cat:git-amend-agent

The squash auto-close step in `git-squash-agent` SKILL.md uses `git commit --amend` to absorb `index.json`
into the squashed commit. This requires the `cat:git-amend-agent` skill rather than a raw `git commit --amend`
command, because the hook environment blocks bare amend calls. Update `plugin/skills/git-squash-agent/SKILL.md`
to invoke `cat:git-amend-agent` instead of `git commit --amend` in the auto-close step.

- Read `plugin/skills/git-squash-agent/SKILL.md` and locate the auto-close index.json step that calls
  `git commit --amend`.
- Replace that call with an invocation of `cat:git-amend-agent` (using the Skill tool) so the amend
  succeeds in a live hook environment without being blocked by `PreToolUse`.
- Verify that no other `git commit --amend` calls remain in the squash skill's auto-close logic.

### Job 4: Add a runtime E2E integration test for the squash-then-approve flow

Write a shell-based integration test (or a Java integration test using `ProcessBuilder`) that:

1. Creates a temporary git repository with a CAT worktree structure (a branch named with the
   `MAJOR.MINOR-issue-slug` pattern and a corresponding `.cat/issues/.../index.json` with
   `"status": "in-progress"`).
2. Makes a `bugfix:` commit without staging `index.json`.
3. Invokes the squash step (calls the relevant logic extracted from `git-squash-agent`) to simulate
   the auto-close: checks that `index.json` is updated to `"status": "closed"` and absorbed into the
   commit.
4. Calls `VerifyStateInCommit` (the approval gate variant) on the resulting state and asserts it
   returns EXIT 0 (allowed).
5. Resets `index.json` to `"status": "in-progress"` and calls `VerifyStateInCommit` again, asserting
   it returns a non-zero exit code (blocked) with the expected error message.

Place the test in `client/src/test/java/io/github/cowwoc/cat/hooks/test/` following the existing test
conventions. Run `mvn -f client/pom.xml verify -e` to confirm all tests pass before the approval gate.

### Job 5: Extract index.json auto-close to Java CLI tool

Replace the bash `sed -i` block in `work-squash.md` Step 3 with a Java CLI tool `auto-close-index`
that uses Jackson to safely manipulate the JSON. The bash regex approach is fragile and can corrupt
JSON if field ordering or whitespace differs from expectation; Jackson reads and rewrites the full
document correctly.

- Create `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/AutoCloseIndexJson.java`
  with args `<worktree_path> <branch>`. Uses `IssueDiscovery.branchToIndexJsonPath()` to derive
  the path, reads/updates the JSON with `scope.getJsonMapper()`, runs `git add` via
  `GitCommands.runGit()`, and outputs `{"index_updated": true/false, "index_path": "..."}`.
- Add `"auto-close-index:util.AutoCloseIndexJson"` to the HANDLERS array in `client/build-jlink.sh`.
- Update `plugin/agents/work-squash.md` Step 3 to invoke
  `"${CLAUDE_PLUGIN_ROOT}/client/bin/auto-close-index" "${WORKTREE_PATH}" "${BRANCH}"` and parse
  the JSON output to determine whether to proceed with the amend step.
- Add `client/src/test/java/io/github/cowwoc/cat/client/test/AutoCloseIndexJsonTest.java`
  covering: non-CAT branch (skipped), absent index.json (skipped), already closed (skipped),
  updates in-progress to closed.

## Post-conditions

- [ ] `bugfix:`/`feature:` commits in a CAT worktree are NOT blocked when `index.json` is absent from staging
- [ ] Squash phase auto-closes `index.json` if it is not already closed
- [ ] Approval gate is blocked when `index.json` status is not `"closed"` at gate presentation time
- [ ] Approval gate proceeds normally when `index.json` status is `"closed"`
- [ ] All existing tests pass; new tests cover the updated behavior
- [ ] E2E: running the squash step on a worktree with an open issue closes `index.json` and the approval gate
  is subsequently allowed
