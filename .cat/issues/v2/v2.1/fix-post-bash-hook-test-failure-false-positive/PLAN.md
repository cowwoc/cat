# fix-post-bash-hook-test-failure-false-positive

## Goal

Fix the PostToolUse:Bash hook's "test_failure" detection to avoid triggering on incidental
keyword matches in diff output and file content. The hook should only detect actual test
runner output (mvn test, bats, gradle test, etc.), not arbitrary Bash commands that happen to
contain the words "test", "failure", or "test_failure".

## Background

The PostToolUse:Bash hook implements test failure detection by pattern-matching Bash stdout
for strings like "test_failure", "FAILURE", or similar keywords. This was designed to catch
test runner output and immediately alert the agent.

However, the hook does not distinguish between:

1. **Actual test runner output**: Output from commands like `mvn test`, `bats`, `gradle test`
   with meaningful exit codes
2. **Incidental keyword matches**: File content in diffs, rendered diff output from
   `get-output get-diff`, or file reads that contain these words as part of natural text

This causes false positives when:

- Reading files containing "test" or "failure" in their text (e.g., PLAN.md with SPRT
  benchmarking discussion)
- Running `git diff` on files with diff content mentioning failure scenarios
- Running `get-output get-diff` for approval gates, which renders diffs with
  skill-convention.md or PLAN.md content containing "failure" or "test" terminology

These false positives waste context by injecting spurious "MISTAKE DETECTED: test_failure"
system reminders that must be recognized and dismissed.

## Design Decisions

### Command-aware detection (preferred)

Check if the Bash command itself is a known test runner. Only apply test_failure pattern
matching to:
- `mvn test`
- `bats`
- `gradle test`
- Similar test runner commands

This eliminates false positives on unrelated commands (`git diff`, `get-output`, `cat`,
`read`, etc.).

### Output format awareness (alternative)

Distinguish test runner output format from arbitrary file content:
- Test runners produce structured output (e.g., `BUILD SUCCESS`, `BUILD FAILURE`, line-by-line
  test counts)
- Diff output is marked by diff header lines (`---`, `+++`, `@@`)
- Apply keyword matching only to lines that match test runner output patterns

### No pattern matching on diff output

Explicitly exclude commands or output that are clearly diffs:
- Don't pattern-match on `git diff` output
- Don't pattern-match on rendered diff output from tools like `get-output get-diff`

## Scope

### In scope

- Locate the PostToolUse:Bash hook handler (likely in `plugin/hooks/` or compiled in
  `post-bash` binary)
- Modify test_failure detection to be command-aware or output-format-aware
- Add guards to prevent false positives on diff output
- Verify the hook still detects actual test failures from mvn, bats, gradle

### Out of scope

- Changes to other hook types (PreToolUse, PostToolUse for non-Bash, etc.)
- Modifications to test runners themselves
- Changes to skills that invoke test runners

## Acceptance Criteria

- [ ] PostToolUse:Bash hook does NOT trigger "test_failure" detection on `git diff` output
- [ ] PostToolUse:Bash hook does NOT trigger "test_failure" detection on `get-output get-diff`
      output
- [ ] PostToolUse:Bash hook does NOT trigger "test_failure" detection on `cat` or file read
      commands
- [ ] PostToolUse:Bash hook still correctly detects actual test failures from `mvn test`,
      `bats`, `gradle test`, etc.
- [ ] Pattern matching is scoped to actual test runner commands, not arbitrary Bash output
- [ ] No spurious "MISTAKE DETECTED: test_failure" system reminders in Bash command output
      that contains incidental "test" or "failure" keywords