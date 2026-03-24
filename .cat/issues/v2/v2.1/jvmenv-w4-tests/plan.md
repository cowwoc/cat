---
issue: 2.1-jvmenv-w4-tests
parent: 2.1-remove-jvmscope-claudeenv-duplicates
sequence: 4 of 5
---

# Plan: jvmenv-w4-tests

## Objective

Update all test-source call sites that call `getClaudeConfigDir()`, `getClaudeSessionsPath()`, or
`getClaudeSessionPath()` on a `JvmScope`-typed variable, and verify the full test suite passes.

## Dependencies

- `2.1-jvmenv-w3-main` must be merged first

## Strategy

Same as Wave 3: change parameter types from `JvmScope` to `ClaudeTool` or `ClaudeHook` as
appropriate for each test file's context.

## Files to Update

### Uses getClaudeConfigDir()

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestUtils.java` (line 225: `pathSource.getClaudeConfigDir()`)

### Uses getClaudeSessionsPath()

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/RequireSkillForCommandTest.java` (2 occurrences)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceApprovalBeforeMergeTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EmpiricalTestRunnerTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/DetectValidationWithoutEvidenceTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/DetectAssistantGivingUpTest.java`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WarnApprovalWithoutRenderDiffTest.java` (3 occurrences)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/JvmScopePathResolutionTest.java`

### Uses getClaudeSessionPath()

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/JvmScopePathResolutionTest.java` (2 occurrences)

## Post-conditions

- [ ] No call site in `client/src/test/` calls `scope.getClaudeConfigDir()`,
  `scope.getClaudeSessionsPath()`, or `scope.getClaudeSessionPath()` on a `JvmScope`-typed variable
- [ ] `mvn -f client/pom.xml test` exits 0 with no compilation errors or test failures
