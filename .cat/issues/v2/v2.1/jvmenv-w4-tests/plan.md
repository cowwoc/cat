---
issue: 2.1-jvmenv-w4-tests
parent: 2.1-remove-jvmscope-claudeenv-duplicates
sequence: 4 of 5
---

# Plan: jvmenv-w4-tests

## Objective

Update all test-source call sites to use the new `scope.getClaudeEnv()` accessor, fix the
`ClaudeEnvTest` method names, and verify the full test suite passes.

## Dependencies

- `2.1-jvmenv-w3-main` must be merged first

## Substitutions

For each file listed below:
- `scope.getClaudeSessionId()` → `scope.getClaudeEnv().getSessionId()`
- `scope.getProjectPath()` → `scope.getClaudeEnv().getProjectPath()`
- `scope.getClaudePluginRoot()` → `scope.getClaudeEnv().getPluginRoot()`

## Files to Update

- `PostToolUseFailureHookTest.java`: `scope.getClaudeSessionId()` → `scope.getClaudeEnv().getSessionId()`
- `PostToolUseHookTest.java`: same
- `SetPendingAgentResultTest.java`: same
- `SessionEndHandlerTest.java`: same (3 occurrences)
- `JvmScopePathResolutionTest.java`: same (2 occurrences)
- `InjectMainAgentRulesTest.java`: getProjectPath (4 occurrences) and getClaudePluginRoot (3 occurrences)
- `WarnApprovalWithoutRenderDiffTest.java`: getProjectPath (3 occurrences)
- `SubagentStartHookTest.java`: getProjectPath (3 occurrences)
- `InjectSubAgentRulesTest.java`: getProjectPath (6 occurrences) and getClaudePluginRoot (3 occurrences)
- `GetAddOutputPlanningDataTest.java`: all getProjectPath occurrences
- `SessionEndHookTest.java`: getProjectPath
- `TestClaudeToolTest.java` line 82: getProjectPath
- `ClaudeEnvTest.java`: update method names in test method bodies and Javadoc:
  `getClaudeSessionId()` → `getSessionId()`, `getClaudePluginRoot()` → `getPluginRoot()`,
  `getClaudeEnvFile()` → `getEnvFile()`
- `EnforceJvmScopeEnvAccessTest.java` line 82: update comment text mentioning `getClaudeSessionId()`

## Post-conditions

- [ ] No call site in `client/src/test/` references `scope.getClaudeSessionId()`,
  `scope.getProjectPath()`, or `scope.getClaudePluginRoot()` as direct scope methods
- [ ] `ClaudeEnvTest.java` uses the new method names
- [ ] `mvn -f client/pom.xml test` exits 0 with no compilation errors or test failures
