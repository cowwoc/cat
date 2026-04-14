# Plan: fix-junit-rule2-violations

## Goal

Fix JUnit test assertions that verify tool behavior (git internals, filesystem side-effects of
external commands) rather than product behavior, violating `testing-conventions.md` Rule 2:
"Test product behavior, not tool behavior."

## Background

A full review of 277+ JUnit test files in `client/src/test/` against `testing-conventions.md`
identified three files with Rule 2 violations. The rule states: do not assert the side-effects
of external tools (git, curl, etc.) after invoking them. Assert only what the product is
responsible for producing.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Test-only changes; no production code modified
- **Mitigation:** Each change removes a redundant or incorrect assertion; remaining assertions
  still verify the same behavior via product-level checks

## Files to Modify

### 1. `GitSquashTest.java` — lines 645 and 731

**Methods:** `rebaseConflictLeavesCleanWorkingDirectory` and its multiple-conflict variant

**Current (violation):**
```java
assertFalse(Files.exists(tempDir.resolve(".git/rebase-merge")));
```
This asserts that git cleaned up its internal rebase state directory. That is git's behavior,
not the product's. The product's responsibility is to return a meaningful JSON status/output.

**Fix:** Replace these assertions with checks on the JSON `status` field returned by
`GitSquash.execute()`, or use `git status --porcelain` output to confirm a clean working
directory from the product's perspective.

### 2. `MergeAndCleanupTest.java` — lines 623 and 639

**Method:** `testMergeLinearHistoryAndCleanupWorktree`

**Current (violation):**
```java
assertTrue(Files.exists(mainRepo.resolve("new-feature.txt")));   // line 623
assertFalse(Files.exists(mainRepo.resolve("new-feature.txt")));  // line 639
```
The merge outcome is already verified via `git log` assertions earlier in the test. These
`Files.exists` checks additionally verify that git synced the working tree — that is git's
behavior, not the product's.

**Fix:** Remove lines 623 and 639. The existing `git log` assertions are sufficient to
verify the product's merge behavior.

### 3. `InstructionTestRunnerTest.java` — line 1865 only

**Method:** (setup verification for worktree runner tests)

**Current (violation):**
```java
assertTrue(Files.exists(runnerWorktree));  // line 1865 — after git worktree add
```
This verifies that `git worktree add` created the directory — that is git's behavior during
setup, not the product's.

**Keep (line 1872):**
```java
assertFalse(Files.exists(runnerWorktree));  // after removeRunnerWorktree(...)
```
This tests that `removeRunnerWorktree` (the product's own method) removed the directory.
That IS product behavior and must be kept.

**Fix:** Remove only line 1865. Leave line 1872 intact.

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

Fix `GitSquashTest.java` lines 645 and 731: replace `.git/rebase-merge` existence checks with
product-level JSON status assertions on the result of `GitSquash.execute()`.

### Job 2

Fix `MergeAndCleanupTest.java` lines 623 and 639: remove the `Files.exists` checks; the
surrounding `git log` assertions already cover merge correctness.

### Job 3

Fix `InstructionTestRunnerTest.java` line 1865: remove the post-`git worktree add` existence
check. Leave line 1872 intact.

### Job 4

Run `mvn -f client/pom.xml verify -e` and confirm all tests pass.

## Post-conditions

- [ ] No JUnit test asserts git's internal state (`.git/rebase-merge`, working-tree file sync)
  as a proxy for product behavior
- [ ] `GitSquashTest.java` conflict methods assert JSON status, not `.git/rebase-merge`
- [ ] `MergeAndCleanupTest.java` merge method has no `Files.exists` checks removed
- [ ] `InstructionTestRunnerTest.java` line 1865 removed; line 1872 intact
- [ ] All tests pass (`mvn -f client/pom.xml verify -e` exits 0)
