# Plan: Block Worktree Isolation Violations via Bash

## Goal
Prevent agents from bypassing worktree isolation by using Bash file-write commands (shell redirects, tee, cp, mv) to
write outside the active worktree when Edit/Write hooks block the operation.

## Satisfies
None (infrastructure hardening from M373)

## Problem
`EnforceWorktreePathIsolation` only intercepts Edit/Write tool calls. When the hook fires and blocks an Edit/Write,
agents can bypass it by using Bash shell redirects (`>`, `>>`) or commands like `tee`, `cp`, `mv` that write files
outside the worktree.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Bash command parsing is inherently imprecise (complex quoting, piped commands, variables). False
  positives could block legitimate operations.
- **Mitigation:** Focus on common write patterns. Allow bypass with explicit comment. Test thoroughly with edge cases.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockWorktreeIsolationViolation.java` - new BashHandler
- `client/src/main/java/io/github/cowwoc/cat/hooks/GetBashOutput.java` - add handler to list
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockWorktreeIsolationViolationTest.java` - TestNG tests

## Acceptance Criteria
- [ ] Bash commands with `> /workspace/path` outside worktree are blocked when a session lock exists
- [ ] Bash commands with `>> /workspace/path` outside worktree are blocked
- [ ] Bash commands with `tee /workspace/path` outside worktree are blocked
- [ ] Bash commands with `cp src /workspace/path` outside worktree are blocked
- [ ] Bash commands targeting paths inside the worktree are allowed
- [ ] Bash commands when no session lock exists are allowed (no false positives)
- [ ] All test cases pass with `mvn -f client/pom.xml test`

## Execution Steps
1. **Step 1:** Create `BlockWorktreeIsolationViolation` BashHandler that checks for file-writing bash patterns targeting
   paths outside the active worktree
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockWorktreeIsolationViolation.java`
2. **Step 2:** Register handler in GetBashOutput handler list
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/GetBashOutput.java`
3. **Step 3:** Write TestNG tests covering all write patterns and edge cases
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockWorktreeIsolationViolationTest.java`
4. **Step 4:** Run tests and verify no regressions

## Success Criteria
- [ ] Bash file-write commands outside worktree are blocked when session lock exists
- [ ] No false positives for legitimate Bash operations
- [ ] All tests pass
