# Bugfix: fix-autolearn-pattern2-false-positive

## Problem

`AutoLearnMistakes.java` Pattern 2 (test_failure) contains the sub-pattern `^(FAIL:|FAILED\s)` which matches skill
documentation error templates like `FAIL: progress-banner launcher failed for phase preparing` in
`work-with-issue-agent/first-use.md`. When agents read these skill files via Bash, the output triggers spurious
`test_failure` detections.

## Root Cause

Pattern 2's `^(FAIL:|FAILED\s)` sub-pattern matches any line starting with `FAIL:` without requiring test-execution
context. Skill documentation files use `FAIL:` as a prefix for error-message templates that agents should display to
users. These lines are indistinguishable from real test failure lines under the current pattern.

**Evidence:** M467 — Session `4b0a7cc1`, agent read `work-with-issue-agent/first-use.md` via `cat` Bash command.
Lines like `FAIL: progress-banner launcher failed for phase preparing` triggered false positive. Recurrence of M461 and
M466.

## Satisfies

None - infrastructure/reliability improvement

## Post-conditions

- [ ] `^(FAIL:|FAILED\s)` sub-pattern is removed from Pattern 2 `testFailPattern` in `AutoLearnMistakes.java`
- [ ] Pattern 2 does NOT trigger on `FAIL: progress-banner launcher failed for phase preparing`
- [ ] Pattern 2 does NOT trigger on `FAIL: some documentation error message`
- [ ] Pattern 2 DOES trigger on `Tests run: 5, Failures: 2`
- [ ] Pattern 2 DOES trigger on `3 tests failed`
- [ ] Pattern 2 DOES trigger on `MyTest.testMethod ... FAILED`
- [ ] Pattern 2 DOES trigger on `5 failures`
- [ ] Tests in `AutoLearnMistakesTest` verify all false-positive and true-positive cases
- [ ] All existing `AutoLearnMistakes` tests continue to pass
- [ ] `mvn -f client/pom.xml test` passes

## Implementation

Remove the `^(FAIL:|FAILED\s)` sub-pattern from Pattern 2. Real test failures are covered by the remaining
sub-patterns:

- `Tests run:.*Failures: [1-9]` — Maven Surefire output
- `\d+\s+tests?\s+failed` — pytest/other framework output
- `\d+\s+failures?\b` — various test frameworks
- `^\s*\S+\s+\.\.\.\s+FAILED` — test method result lines

Add tests to `AutoLearnMistakesTest` verifying:
1. `FAIL: progress-banner launcher failed` does NOT trigger `test_failure`
2. `FAIL: some phase error` does NOT trigger `test_failure`
3. `Tests run: 1, Failures: 1` DOES trigger `test_failure`
4. `2 tests failed` DOES trigger `test_failure`

## Pre-conditions

- [ ] All dependent issues are closed

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java` — remove `^(FAIL:|FAILED\s)`
  from Pattern 2 `testFailPattern`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/AutoLearnMistakesTest.java` — add false-positive and
  true-positive test cases for Pattern 2
