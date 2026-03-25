# Plan

## Goal

Ensure every command-line option in every non-hook CLI class has test coverage through `run()`, and refactor
existing tests that call internal methods directly to go through `run()` instead.

This follows the "Test Through run(), Not Internal Methods" convention added to `.claude/rules/java.md` in
issue `2.1-thin-main-methods-and-add-run-tests`.

## Background

Issue `2.1-thin-main-methods-and-add-run-tests` added `run()` methods and `*MainTest.java` files for all
non-hook CLI classes. However:

1. Most `*MainTest.java` files only have null-check tests and 1-2 basic error-path tests
2. Many command-line options (flags, subcommands, validation paths) lack test coverage through `run()`
3. Existing tests in some files call internal methods (e.g., `getOutput()`, `getConcernBox()`) directly
   instead of going through `run()`

## Scope

### Part 1: Add missing argument-parsing tests through run()

For each CLI class with `run()`, ensure every command-line option and error path has a test:

- **Valid flag combinations** — verify `run()` returns 0 and produces expected output
- **Missing required flags** — verify `run()` returns 1
- **Invalid flag values** — verify `run()` returns 1
- **Unknown flags** — verify `run()` returns 1 (where applicable)
- **Help/usage output** — verify `run()` returns 0 and output contains "Usage"

Priority files (have substantial arg parsing with gaps):
- `EmpiricalTestRunner` — `--config`, `--trials`, `--model`, `--cwd`, `--output`, `--baseline`
- `GetAddOutput` — `--type`, `--name`, `--version`, `--issue-type`, `--dependencies`, `--parent`, `--path`
- `GetCheckpointOutput` — `--type`, `--issue-name`, `--tokens`, `--percent`, `--branch`, `--iteration`, `--total`
- `GetCleanupOutput` — `--project-dir`, `--phase`
- `GetIssueCompleteOutput` — `--issue-name`, `--target-branch`, `--scope-complete`
- `GetNextIssueOutput` — `--completed-issue`, `--target-branch`, `--session-id`, `--project-dir`, `--exclude-pattern`
- `SessionAnalyzer` — subcommands: `analyze`, `search`, `errors`, `file-history`
- `MarkdownWrapper` — `--width`, positional file arg, stdin mode

### Part 2: Refactor existing tests to use run()

Find tests that call internal methods directly (e.g., `getOutput()`, `analyzeSession()`, `getConcernBox()`)
and refactor them to go through `run()` with appropriate command-line arguments instead.

## Post-conditions

- [ ] Every `*MainTest.java` has tests for each command-line flag/option accepted by its `run()` method
- [ ] No test calls an internal method when the same functionality can be exercised through `run()`
- [ ] All tests pass (`mvn -f client/pom.xml verify`)
- [ ] Error paths return non-zero exit codes (verified via `requireThat(result, "result").isEqualTo(1)`)
- [ ] Happy paths return zero exit codes (verified via `requireThat(result, "result").isEqualTo(0)`)
