# Plan: improve-undefined-var-warning

## Problem
When a Bash redirect target contains a variable that cannot be statically resolved,
`BlockWorktreeIsolationViolation` emits "One or more variables in the path could not be resolved"
without naming the specific undefined variable(s), making the warning harder to act on.

## Parent Requirements
None

## Reproduction Code
```
# Script with unresolvable variable
cmd > "${SESSION_DIR}/squash-complete-${ISSUE_ID}"
```

## Expected vs Actual
- **Expected:** `WARNING: Cannot verify Bash redirect to variable-expanded path: "${SESSION_DIR}/squash-complete-${ISSUE_ID}"\n\nUndefined variables: SESSION_DIR, ISSUE_ID\n...`
- **Actual:** `WARNING: Cannot verify Bash redirect to variable-expanded path: "${SESSION_DIR}/squash-complete-${ISSUE_ID}"\n\nOne or more variables in the path could not be resolved.\n...`

## Root Cause
`ShellParser.expandEnvVars()` returns `null` on the first undefined variable but does not report which
variable failed. The caller in `BlockWorktreeIsolationViolation` has no way to name the undefined
variable(s) without re-scanning the target string.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Only changes the text of a warning message; no logic changes
- **Mitigation:** Add a unit test for the new `findUndefinedVars` method

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java` — add `findUndefinedVars(String, Function<String,String>)` method
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWorktreeIsolationViolation.java` — replace "One or more variables..." with a list of the undefined variable names
- `client/src/test/java/io/github/cowwoc/cat/client/test/ShellParserTest.java` (or equivalent) — add tests for `findUndefinedVars`

## Test Cases
- [ ] Path with one undefined variable → list shows that one variable name
- [ ] Path with two undefined variables → list shows both names in order
- [ ] Path with all variables defined → returns empty list (no warning triggered)

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Add findUndefinedVars to ShellParser
- Add `public static List<String> findUndefinedVars(String target, Function<String, String> envLookup)` to `ShellParser`
  - Scan `target` for `${VAR}` and `$VAR` references using `ENV_VAR_EXPAND_PATTERN`
  - For each match, call `envLookup`; if it returns `null`, add the variable name to the result list
  - Return the list (preserving order of appearance, no deduplication needed)
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java`

### Job 2: Update warning message in BlockWorktreeIsolationViolation
- After `expandEnvVars` returns `null`, call `ShellParser.findUndefinedVars(target, mergedLookup)` to get the undefined names
- Replace "One or more variables in the path could not be resolved." with "Undefined variable(s): ${names joined by ", "}"
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWorktreeIsolationViolation.java`

### Job 3: Add tests
- Add unit tests for `findUndefinedVars`:
  - One undefined variable
  - Two undefined variables
  - All defined (empty result)
  - Files: test source file for `ShellParser`

## Post-conditions
- [ ] Warning message names the specific undefined variable(s) (e.g., `Undefined variable(s): SESSION_DIR, ISSUE_ID`)
- [ ] `ShellParser.findUndefinedVars` unit tests pass
- [ ] `mvn -f client/pom.xml verify -e` exits 0 with no new failures
