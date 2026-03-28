# Plan

## Goal

Update statusline error prefix from class name to 'CAT' — change error format from '⚠ <class name>: <error message>' to '⚠ CAT: <error message>'

## Pre-conditions

(none)

## Post-conditions

- [ ] All statusline error strings in StatuslineCommand.java use the `⚠ CAT:` prefix instead of class name prefixes (StatuslineCommand, MalformedJson, etc.)
- [ ] Test assertions updated to verify the exact `⚠ CAT:` prefix, not just the warning symbol
- [ ] Javadoc in StatuslineCommand.getActiveIssue() updated to reflect `⚠ CAT: <message>` format
- [ ] All tests passing with no regressions
- [ ] E2E verification: statusline errors display `⚠ CAT: <error message>` instead of `⚠ <class name>: <error message>`

## Research Findings

### Current Error Prefix Locations in StatuslineCommand.java

Five error prefix locations need updating:

| Line | Current Prefix | Context |
|------|---------------|---------|
| ~184 | `⚠ MalformedJson: lock file is empty: ` | getActiveIssue() — empty lock file |
| ~188 | `⚠ MalformedJson: lock file parsed to null node: ` | getActiveIssue() — null JSON parse |
| ~205 | `⚠ StatuslineCommand: ` | getActiveIssue() — IOException/JacksonException catch |
| ~347 | `⚠ StatuslineCommand: ` | main() — IllegalArgumentException/IOException handler |
| ~376 | `⚠ StatuslineCommand: ` | main() — RuntimeException/AssertionError handler (printError method) |

### Javadoc to Update

The `getActiveIssue()` method Javadoc at ~line 162 references `"⚠ StatuslineCommand: <message>"` — must change to
`"⚠ CAT: <message>"`.

### Test Assertions to Update

In `StatuslineCommandTest.java`:
- Line ~1029: `requireThat(result, "result").startsWith("⚠")` — strengthen to `startsWith("⚠ CAT:")`
- Line ~1094: `requireThat(result, "result").startsWith("⚠")` — strengthen to `startsWith("⚠ CAT:")`

In `StatuslineCommandMainTest.java`: No error prefix assertions found (tests check exception types, not output format).

### Files NOT in Scope

`GetStatusOutput.java` and `GetCleanupOutput.java` also use `⚠` but for display purposes (`⚠ Corrupt Issue
Directories:`, `⚠ CORRUPT`), not as error prefixes in the `⚠ <class>: <message>` pattern. These are out of scope.

## Jobs

### Job 1

- Read `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java`
- Replace all five error prefix strings:
  - Change `"⚠ MalformedJson: lock file is empty: "` to `"⚠ CAT: lock file is empty: "`
  - Change `"⚠ MalformedJson: lock file parsed to null node: "` to `"⚠ CAT: lock file parsed to null node: "`
  - Change all three occurrences of `"⚠ StatuslineCommand: "` to `"⚠ CAT: "`
- Update Javadoc on `getActiveIssue()`: change `{@code "⚠ StatuslineCommand: <message>"}` to
  `{@code "⚠ CAT: <message>"}`
- Read `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineCommandTest.java`
- Update test assertion at ~line 1029: change `.startsWith("⚠")` to `.startsWith("⚠ CAT:")`
- Update test assertion at ~line 1094: change `.startsWith("⚠")` to `.startsWith("⚠ CAT:")`
- Run `mvn -f client/pom.xml test` to verify all tests pass
- Commit type: `refactor:`
- Commit message: `refactor: standardize statusline error prefix to CAT instead of class names`
- Update `.cat/issues/v2/v2.1/refactor-statusline-error-prefix/index.json`: set `"status"` → `"closed"`, `"progress"` → `100`
