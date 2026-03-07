# Plan: fix-issue-path-worktree-resolution

## Problem

When a worktree is active, work phase agents (implement, confirm, review, merge) construct the PLAN.md path as
`"${ISSUE_PATH}/PLAN.md"` where `ISSUE_PATH` is the main workspace path
`/workspace/.claude/cat/issues/v2/v2.1/issue-name`. The `EnforceWorktreePathIsolation` hook blocks `Read` tool calls
to paths outside the active worktree, so agents fail to read PLAN.md or STATE.md.

## Parent Requirements

None

## Reproduction Code

```
# 1. Run /cat:work → work-prepare returns issue_path=/workspace/.claude/cat/issues/v2/v2.1/issue-name
# 2. work-implement-agent sets: PLAN_MD="${ISSUE_PATH}/PLAN.md"
# 3. Agent invokes: Read("/workspace/.claude/cat/issues/v2/v2.1/issue-name/PLAN.md")
# 4. EnforceWorktreePathIsolation hook blocks: "ERROR: Worktree isolation violation"
```

## Expected vs Actual

- **Expected:** Agents read PLAN.md from `${WORKTREE_PATH}/.claude/cat/issues/v2/v2.1/issue-name/PLAN.md`
  (within worktree)
- **Actual:** Agents attempt to read from main workspace path; blocked by worktree isolation hook

## Root Cause

`WorkPrepare.java` returns `issue_path` as an absolute main-workspace path. Work phase skills use this directly
for PLAN.md/STATE.md reads instead of deriving the equivalent path within the active worktree.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Only changes the value of the existing `issue_path` field — no new fields, no skill changes.
  Java handlers (e.g., `VerifyAudit.java`) read via Java FileI/O (not the `Read` tool) and are unaffected.
- **Mitigation:** Add a regression test verifying `issue_path` points inside the worktree.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — change `issue_path` to emit the
  worktree-relative path instead of the main workspace path
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` — add regression test verifying
  `issue_path` points inside the worktree

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `WorkPrepare.java`, change `result.put("issue_path", issuePath.toString())` (line ~496) to:
  ```java
  Path relativeIssuePath = projectDir.relativize(issuePath);
  result.put("issue_path", worktreePath.resolve(relativeIssuePath).toString());
  ```
  This redefines `issue_path` to point inside the worktree. No new field needed — all downstream consumers
  (skill files) already use `ISSUE_PATH` and will automatically get the worktree path.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

- Add a regression test `executeReturnsIssuePathInsideWorktree()` to verify `issue_path` starts with
  `worktree_path` and ends with the correct relative issue directory.
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`

- Run tests: `mvn -f client/pom.xml test`
  - Files: _(build validation only)_

- Commit: `bugfix: emit issue_path pointing inside worktree from WorkPrepare`

## Post-conditions

- [ ] `WorkPrepare.java` emits `issue_path` pointing inside the worktree (not the main workspace)
- [ ] Regression test in `WorkPrepareTest.java` verifies `issue_path` is inside the worktree
- [ ] All existing tests pass: `mvn -f client/pom.xml test`
- [ ] E2E: Run `/cat:work` on any open issue; implementation subagent reads PLAN.md without isolation errors
