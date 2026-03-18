# Plan: refactor-claudeenv-api

## Current State

The `ClaudeEnv` class and test infrastructure have inconsistent and confusing naming conventions:
- `ClaudeEnv` has prefixed methods: `getClaudeSessionId()`, `getClaudePluginRoot()`, `getClaudeEnvFile()`
- `TestJvmScope` exposes `getClaudeSessionId()` as a public getter, enabling direct test access
- Production code creates new `ClaudeEnv()` instances directly to access environment variables (e.g., in `GetSkill.java` line 178, `WorkPrepare.java` line 2057), breaking test isolation
- Tests rely on `TestJvmScope.getClaudeSessionId()` instead of using environment variable lookups

This creates two problems: (1) inconsistent naming makes the API harder to understand, and (2) `new ClaudeEnv()` in production code bypasses test scopes entirely, causing test isolation failures in `GetSkillTest`.

## Target State

After refactoring:
- `ClaudeEnv` methods are renamed for consistency: `getSessionId()`, `getPluginRoot()`, `getEnvFile()` (dropping the "Claude" prefix since context is already clear)
- `TestJvmScope.getClaudeSessionId()` is removed entirely; tests must use the hardcoded `"test-session"` string or construct `ClaudeEnv` via `SharedSecrets.newClaudeEnv(map)`
- Production code that needs environment variables uses `scope.getEnvironmentVariable("CLAUDE_SESSION_ID")` instead of `new ClaudeEnv()`, restoring test isolation
- The 2 currently-failing `GetSkillTest` tests pass because `GetSkill` now uses scope-provided environment variables that respect test bindings

## Parent Requirements

None (technology debt / API consistency improvement)

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None for end users (ClaudeEnv is internal API only); public `JvmScope` interface methods remain unchanged
- **Mitigation:** Comprehensive find-and-replace across production and test code; run full test suite (`mvn verify`) to catch any missed references

## Files to Modify

**Production code (rename method calls):**
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeEnv.java` — rename methods in class definition
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java` — fix line 178 to use `scope.getEnvironmentVariable()` instead of `new ClaudeEnv()`
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — line 2057: replace `new ClaudeEnv().getClaudeSessionId()` with equivalent scope-based lookup
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java` — line 696: same replacement pattern
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/util/InvestigationContextExtractor.java` — line 93: same replacement pattern
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java` — line 466: rename `getClaudePluginRoot()` call to `getPluginRoot()`
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java` — line 192: rename method call
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectMainAgentRules.java` — line 62: rename method calls
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java` — lines 72, 118, 155: rename method calls
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSubAgentRules.java` — line 72: rename method calls
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckUpdateAvailable.java` — line 66: rename method call
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckDataMigration.java` — line 69: rename method call
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` — line 522: replace `new ClaudeEnv().getClaudeSessionId()` with scope-based lookup
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/skills/DisplayUtils.java` — line 105: rename method call
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetTokenReportOutput.java` — lines 70, 86: replace `new ClaudeEnv()` with scope-based lookup; update Javadoc
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java` — line 129: replace `new ClaudeEnv()` with scope-based lookup
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/bash/RequireSkillForCommand.java` — line 81: rename method call
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java` — lines 84, 98: rename method declarations
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/licensing/LicenseValidator.java` — line 84: rename method call
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/licensing/Entitlements.java` — line 50: rename method call

**Test code:**
- `/workspace/client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java` — remove the public `getClaudeSessionId()` method entirely (lines 267-271); keep the private field
- `/workspace/client/src/test/java/io/github/cowwoc/cat/hooks/test/ClaudeEnvTest.java` — rename all method calls in test cases to match new names
- All other test files using `scope.getClaudeSessionId()` or `scope.getClaudePluginRoot()` or `scope.getClaudeEnvFile()` — update to use hardcoded `"test-session"` or direct `ClaudeEnv` construction where necessary

**Interface definition:**
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java` — interface does NOT declare these methods; verify no interface method signatures need renaming

## Pre-conditions

- [ ] All dependent issues are closed
- [ ] Current test suite runs without the refactoring (baseline)

## Sub-Agent Waves

### Wave 1

**Rename ClaudeEnv methods and update all production code references:**
- Rename `ClaudeEnv.getClaudeSessionId()` → `ClaudeEnv.getSessionId()`
- Rename `ClaudeEnv.getClaudePluginRoot()` → `ClaudeEnv.getPluginRoot()`
- Rename `ClaudeEnv.getClaudeEnvFile()` → `ClaudeEnv.getEnvFile()`
- Update method signatures in `ClaudeEnv.java`
- Update all method calls in production code files listed above
- Compile and verify no compilation errors

**Files:**
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeEnv.java`
- All production code files referencing `getClaudeSessionId()`, `getClaudePluginRoot()`, `getClaudeEnvFile()`

### Wave 2

**Fix GetSkill.java to use scope-based environment variable lookup instead of new ClaudeEnv():**
- In `GetSkill.java` line 178, replace `catAgentId = new ClaudeEnv().getClaudeSessionId();` with `catAgentId = scope.getEnvironmentVariable("CLAUDE_SESSION_ID");`
- Update similar patterns in `WorkPrepare.java`, `RecordLearning.java`, `InvestigationContextExtractor.java`, `GetStatusOutput.java`, `GetTokenReportOutput.java`, `GetNextIssueOutput.java`
- When `scope.getEnvironmentVariable()` returns null or empty, use fallback logic (e.g., return "" or throw AssertionError as appropriate)
- Verify that the fix restores test isolation: `TestJvmScope` does not have `CLAUDE_SESSION_ID` in its `envVars` map, so the code should throw `AssertionError` with a clear message when called from tests that don't mock this value

**Files:**
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/util/InvestigationContextExtractor.java`
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetTokenReportOutput.java`
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java`

### Wave 3

**Remove TestJvmScope.getClaudeSessionId() public method and update test code:**
- Remove the public `getClaudeSessionId()` method from `TestJvmScope.java` (lines 267-271)
- Keep the private `claudeSessionId` field — it's used by `getClaudeSessionPath()` and `getCatSessionPath()` internally
- Update all test files that call `scope.getClaudeSessionId()` to use the hardcoded string `"test-session"` instead (e.g., `scope.getCatSessionPath("test-session")` instead of `scope.getCatSessionPath(scope.getClaudeSessionId())`)
- Update `TestUtils.java` line 45 Javadoc comment to remove the reference to `TestJvmScope.getClaudeSessionId()`
- Compile and verify no compilation errors

**Files:**
- `/workspace/client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java`
- All test files using `scope.getClaudeSessionId()` (see search results above)
- `/workspace/client/src/test/java/io/github/cowwoc/cat/hooks/test/TestUtils.java`

### Wave 4

**Rename all test code method references to match new ClaudeEnv API:**
- Update `ClaudeEnvTest.java` to call renamed methods: `getSessionId()`, `getPluginRoot()`, `getEnvFile()`
- Update test files calling `scope.getClaudePluginRoot()` to use renamed method name
- Update test files calling `scope.getClaudeEnvFile()` to use renamed method name
- Compile and run full test suite: `mvn verify`

**Files:**
- `/workspace/client/src/test/java/io/github/cowwoc/cat/hooks/test/ClaudeEnvTest.java`
- All test files referencing renamed methods

### Wave 5

**Run full verification and commit:**
- Execute `mvn -f /workspace/client/pom.xml verify` to ensure all tests pass
- Verify that `GetSkillTest` tests now pass (the 2 tests that were failing due to test isolation issues)
- No regressions in other tests
- Commit all changes with a message describing the refactoring

**Success criteria:**
- All compilation succeeds
- All 2 previously-failing `GetSkillTest` tests now pass
- No test regressions
- Code quality metrics maintained or improved
- `mvn verify` exits with code 0

## Post-conditions

- [ ] User-visible behavior unchanged (internal API refactoring only)
- [ ] All tests pass (`mvn verify` exits 0, including the 2 previously-failing GetSkillTest tests)
- [ ] Code quality improved (cleaner naming, better test isolation)
- [ ] E2E verification: `mvn -f /workspace/client/pom.xml verify` exits 0
