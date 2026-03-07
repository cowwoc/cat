# Plan: fix-read-block-on-issue-planning-files

## Problem

`EnforceWorktreePathIsolation` blocks `Read` tool operations on `.claude/cat/issues/` paths (PLAN.md,
STATE.md) when a worktree is active for the current session. Agents cannot read planning files from
the paths returned by `work-prepare`, which always points to the main workspace path
`/workspace/.claude/cat/issues/...`.

## Parent Requirements

None

## Reproduction Code

```
# 1. Start /cat:work (work-prepare returns issue_path=/workspace/.claude/cat/issues/v2/v2.1/issue-name)
# 2. In the work-with-issue workflow, Read tool attempts:
#    Read("/workspace/.claude/cat/issues/v2/v2.1/issue-name/PLAN.md")
# 3. Result: "ERROR: Worktree isolation violation — use worktree path instead"
```

## Expected vs Actual

- Expected: Read of `.claude/cat/issues/` paths from main workspace succeeds (these are read-only planning
  files whose main workspace location is authoritative)
- Actual: Read is blocked with "Worktree isolation violation" requiring a worktree path redirect

## Root Cause

`EnforceWorktreePathIsolation.check(String toolName, ...)` calls `isolationCheckReason()` which calls
`checkAgainstContext()`. That method blocks ANY path inside the project directory but outside the worktree,
including `.claude/cat/issues/` planning files that agents legitimately read by their main workspace path.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Allowing reads to `.claude/cat/issues/` could let agents read stale planning content if the
  worktree's version differs, but planning files are not typically modified inside worktrees
- **Mitigation:** Scope the exclusion strictly to `.claude/cat/issues/` reads; all writes remain blocked

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java` — Add early
  allow for Read operations targeting paths under `.claude/cat/issues/`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java` — Add
  regression tests for allowed reads and still-blocked writes

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `EnforceWorktreePathIsolation.check(String toolName, ...)`, after extracting `absoluteFilePath`, add
  an early return `allow()` if the path starts with `projectDir.toAbsolutePath().normalize().resolve(".claude/cat/issues")`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java`
- Add regression tests in `EnforceWorktreePathIsolationTest`:
  - Test: reading `.claude/cat/issues/v2/v2.1/issue-name/PLAN.md` from main workspace while worktree is
    active → allowed
  - Test: writing `.claude/cat/issues/v2/v2.1/issue-name/PLAN.md` from main workspace while worktree is
    active → blocked (write isolation unchanged)
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java`
- Run tests: `mvn -f client/pom.xml test`
- Update STATE.md: status=closed, progress=100%
- Commit: `bugfix: allow reads of issue planning files from main workspace in worktree`

## Post-conditions

- [ ] Bug fixed: Reading `/workspace/.claude/cat/issues/*/PLAN.md` returns file contents (not a block)
  when working in an active worktree session
- [ ] Regression test added: reads to `.claude/cat/issues/` paths are allowed when worktree is active
- [ ] Write isolation unchanged: writing to `.claude/cat/issues/*/PLAN.md` from main workspace is still
  blocked when worktree is active
- [ ] All existing tests pass
- [ ] E2E: Run /cat:work, note the `issue_path` returned by work-prepare, read `${issue_path}/PLAN.md` —
  succeeds without the worktree path redirect error
