---
issue: 2.1-jvmenv-w4-tests
parent: 2.1-remove-jvmscope-claudeenv-duplicates
sequence: 4 of 5
commit-type: refactor
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

## Research Findings

After waves 1-3 were merged, all test call sites were audited. Every variable that calls
`getClaudeSessionsPath()` or `getClaudeSessionPath()` in test files is already typed as
`TestClaudeHook`, `TestClaudeTool`, or `ClaudeHook` — none use `JvmScope`-typed variables.
The `getClaudeConfigDir()` method no longer exists anywhere. As a result, the first post-condition
is already met: no test call site calls any of the three methods on a `JvmScope`-typed variable.

Confirmed file-by-file:
- `TestUtils.java`: `getClaudeConfigDir()` does not exist; `writeLockFile`/`createWorktreeDir` use
  `JvmScope` but only call `getCatWorkPath()` (unaffected)
- `RequireSkillForCommandTest.java`: `scope` typed as `ClaudeHook` in helper methods
- `EnforceApprovalBeforeMergeTest.java`: `scope` typed as `TestClaudeHook`
- `EmpiricalTestRunnerTest.java`: `scope` typed as `TestClaudeTool`
- `DetectValidationWithoutEvidenceTest.java`: `scope` typed as `TestClaudeHook`
- `DetectAssistantGivingUpTest.java`: `scope` typed as `TestClaudeHook`
- `WarnApprovalWithoutRenderDiffTest.java`: `scope` typed as `TestClaudeHook`
- `JvmScopePathResolutionTest.java`: `scope` typed as `TestClaudeTool`

## Files to Update

No call-site changes needed — all listed files already use correctly typed variables.

## Post-conditions

- [ ] No call site in `client/src/test/` calls `scope.getClaudeConfigDir()`,
  `scope.getClaudeSessionsPath()`, or `scope.getClaudeSessionPath()` on a `JvmScope`-typed variable
- [ ] `mvn -f client/pom.xml test` exits 0 with no compilation errors or test failures

## Sub-Agent Waves

### Wave 1

- Run the following to find all remaining call sites:
  ```bash
  grep -rn "getClaudeConfigDir\|getClaudeSessionsPath\|getClaudeSessionPath" client/src/test/
  ```
  For each hit, check the declared type of the calling variable. If any use a `JvmScope`-typed
  variable (declared as `JvmScope`, not `ClaudeTool`, `ClaudeHook`, `TestClaudeTool`, or
  `TestClaudeHook`):
  - Change the parameter type to `ClaudeHook` for hook-context classes (those instantiated with
    `TestClaudeHook` at call sites), or `ClaudeTool` for skill-tool-context classes (instantiated
    with `TestClaudeTool`)
  - Update import statements accordingly
- Run `mvn -f client/pom.xml test` to verify all tests pass (exit code 0, no failures)
- Update `index.json` at `client/src/test/` — actually update the issue's `index.json` at
  `.cat/issues/v2/v2.1/jvmenv-w4-tests/index.json`: set `"status": "closed"`,
  `"resolution": "implemented"`, preserve `"dependencies"` and `"target_branch"` fields
- Commit with message: `refactor: update test call sites for JvmScope method removal`
