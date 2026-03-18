# Plan: remove-jvmscope-claudeenv-duplicates

## Current State

`JvmScope` declares four methods whose sole purpose is to expose environment-variable-backed values:
`getClaudeSessionId()`, `getProjectPath()`, `getClaudePluginRoot()`, and `getClaudeEnvFile()`. The
same values are already served by `ClaudeEnv` (for CLI commands) and `HookInput` (for hook handlers).
This violates the design principle that information should live in exactly one of {`ClaudeEnv`,
`HookInput`, `JvmScope`}.

Additionally, three `ClaudeEnv` methods carry redundant `Claude` prefixes:
`getClaudeSessionId()`, `getClaudePluginRoot()`, and `getClaudeEnvFile()`.

## Target State

- `JvmScope` / `AbstractJvmScope` / `MainJvmScope` / `TestJvmScope` no longer declare or implement
  `getClaudeSessionId()`, `getProjectPath()`, `getClaudePluginRoot()`, or `getClaudeEnvFile()`.
- The derived methods that depended on these (`getCatDir()`, `getCatWorkPath()`, `getCatSessionPath()`,
  `getClaudeSessionPath()`, `getClaudeSessionsPath()`, `getPluginPrefix()`) remain in `JvmScope`; their
  implementations in `AbstractJvmScope` delegate to `ClaudeEnv` internally.
- `ClaudeEnv.getClaudeSessionId()` → `ClaudeEnv.getSessionId()`
- `ClaudeEnv.getClaudePluginRoot()` → `ClaudeEnv.getPluginRoot()`
- `ClaudeEnv.getClaudeEnvFile()` → `ClaudeEnv.getEnvFile()`
- All call sites updated throughout `client/src/`.

## Parent Requirements

None — this is a tech-debt refactor.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Removes four methods from `JvmScope` interface; renames three methods in
  `ClaudeEnv`. Any call site that is missed will fail to compile.
- **Mitigation:** The build (`mvn -f client/pom.xml test`) provides exhaustive compile-time coverage.
  The grep results below enumerate every call site. All changes are rename/removal only — no logic changes.

## Files to Modify

### Interface and abstract class

- `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`
  — Remove `getClaudeSessionId()`, `getProjectPath()`, `getClaudePluginRoot()`, `getClaudeEnvFile()`
  declarations.
  — Add `ClaudeEnv getClaudeEnv()` declaration (see Wave 1 for exact Javadoc).

- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`
  — Add `ClaudeEnv claudeEnv` constructor parameter; add `getClaudeEnv()` implementation.
  — Update derived methods to call `claudeEnv` (see Wave 1 for exact substitutions).

### Concrete JvmScope implementations

- `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java`
  — Remove `ConcurrentLazyReference` fields `claudeProjectPath`, `claudePluginRoot`, `claudeSessionId`,
    `claudeEnvFile` and their `@Override` methods.
  — Call `super(new ClaudeEnv())` in the constructor.

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java`
  — Remove fields `claudeProjectPath`, `claudePluginRoot`, `claudeSessionId`, `claudeEnvFile` and
    their `@Override` methods.
  — In each constructor, build a `Map<String, String>` from the constructor parameters and pass
    `SharedSecrets.newClaudeEnv(map)` to `super()`. See Wave 2 for exact map keys.

### ClaudeEnv

- `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeEnv.java`
  — Rename `getClaudeSessionId()` → `getSessionId()`
  — Rename `getClaudePluginRoot()` → `getPluginRoot()`
  — Rename `getClaudeEnvFile()` → `getEnvFile()`
  — `getProjectPath()` is already named correctly; no rename needed.

### Call sites in main source

Every occurrence of `scope.getClaudeSessionId()` → use `scope.getClaudeEnv().getSessionId()`:

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` line 2040
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java` lines 177, 187
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java` line 696
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/InvestigationContextExtractor.java` line 92
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java` line 128
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetTokenReportOutput.java` line 85
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` line 521
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java` line 73

Every occurrence of `scope.getProjectPath()` → use `scope.getClaudeEnv().getProjectPath()`:

- `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java` lines 162, 164, 178
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionEndHook.java` line 79
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java` line 56
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java` line 158
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCollectAfterAgent.java` line 60
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCommitBeforeSubagentSpawn.java` line 71
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` lines 195, 416
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillDiscovery.java` line 90
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` line 146
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java` line 597
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RootCauseAnalyzer.java` line 236 (uses `env.getProjectPath()` — env is `ClaudeEnv`, may already use correct name)
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockWorktreeIsolationViolation.java` line 84
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMainRebase.java` line 49
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java` line 498
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java` line 149
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java` line 130
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatuslineOutput.java` line 41
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAddOutput.java` line 93
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCheckpointOutput.java` line 82
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` line 87
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetWorkOutput.java` line 74
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCleanupOutput.java` lines 248, 1000
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java` lines 62, 263

Every occurrence of `scope.getClaudePluginRoot()` → use `scope.getClaudeEnv().getPluginRoot()`:

- `client/src/main/java/io/github/cowwoc/cat/hooks/licensing/Entitlements.java` line 50
- `client/src/main/java/io/github/cowwoc/cat/hooks/licensing/LicenseValidator.java` line 84
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java` line 466
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java` line 199
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSubAgentRules.java` line 72
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckDataMigration.java` line 69
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckUpdateAvailable.java` line 66
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectMainAgentRules.java` line 62
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java` line 95
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/RequireSkillForCommand.java` line 81
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/DisplayUtils.java` line 105

Every occurrence of `scope.getClaudeEnvFile()` → use `scope.getClaudeEnv().getEnvFile()`:

- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java` line 81

### Call sites in test source

Tests that call `scope.getClaudeSessionId()`:
- `PostToolUseFailureHookTest.java` line 70
- `SetPendingAgentResultTest.java` line 460
- `PostToolUseHookTest.java` line 70
- `SessionEndHandlerTest.java` lines 68, 228, 347
- `JvmScopePathResolutionTest.java` lines 65, 193

Tests that call `scope.getProjectPath()`:
- `InjectMainAgentRulesTest.java` lines 43, 143, 220, 273, 352
- `WarnApprovalWithoutRenderDiffTest.java` lines 49, 91, 135
- `SubagentStartHookTest.java` lines 444, 484, 524
- `InjectSubAgentRulesTest.java` lines 103, 145, 184, 264, 343, 383
- `GetAddOutputPlanningDataTest.java` (many occurrences — all `scope.getProjectPath()` calls)
- `GetCleanupOutputTest.java` (Javadoc mentions `scope.getProjectPath()`)
- `SessionEndHookTest.java` lines 312
- `TestJvmScopeTest.java` line 82

Tests that call `scope.getClaudePluginRoot()`:
- `InjectMainAgentRulesTest.java` lines 87, 133, 262
- `InjectSubAgentRulesTest.java` lines 49, 93, 253

Tests that call `scope.getClaudeEnvFile()`: (none found — `TestJvmScope` sets up the field, but test code does not call it directly on scope)

Tests that call `ClaudeEnv` renamed methods:
- `ClaudeEnvTest.java`: `getClaudeSessionId()` → `getSessionId()`, `getClaudePluginRoot()` → `getPluginRoot()`,
  `getClaudeEnvFile()` → `getEnvFile()` — also update Javadoc strings inside the test.
- `EnforceJvmScopeEnvAccessTest.java` line 82: comment mentions `getClaudeSessionId()` — update text.

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Update ClaudeEnv, add ClaudeEnv field to AbstractJvmScope, expose via JvmScope

- Rename `ClaudeEnv.getClaudeSessionId()` → `getSessionId()`, `getClaudePluginRoot()` → `getPluginRoot()`,
  `getClaudeEnvFile()` → `getEnvFile()`. Update Javadoc for each renamed method.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeEnv.java`

- Add `ClaudeEnv getClaudeEnv()` to the `JvmScope` interface with Javadoc:
  ```java
  /**
   * Returns the Claude environment accessor for this scope.
   *
   * @return the ClaudeEnv instance
   * @throws IllegalStateException if this scope is closed
   */
  ClaudeEnv getClaudeEnv();
  ```
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`

- Add a `final ClaudeEnv claudeEnv` field to `AbstractJvmScope`, set via a protected constructor
  parameter `AbstractJvmScope(ClaudeEnv claudeEnv)`. Implement `getClaudeEnv()` to return this field.
  Update derived methods `getCatDir()`, `getClaudeSessionsPath()`, `getClaudeSessionPath()`,
  `getCatWorkPath()`, `getCatSessionPath()`, and `derivePluginPrefix()` to use `claudeEnv`:
  - `getCatDir()`: `claudeEnv.getProjectPath().resolve(Config.CAT_DIR_NAME)`
  - `getClaudeSessionsPath()`: `getClaudeConfigDir().resolve("projects").resolve(encodeProjectPath(claudeEnv.getProjectPath().toString()))`
  - `getClaudeSessionPath()`: `getClaudeSessionsPath().resolve(claudeEnv.getSessionId())`
  - `getCatWorkPath()`: `claudeEnv.getProjectPath().resolve(".cat").resolve("work")`
  - `getCatSessionPath()`: `getCatWorkPath().resolve("sessions").resolve(claudeEnv.getSessionId())`
  - `derivePluginPrefix()`: `claudeEnv.getPluginRoot().toAbsolutePath().normalize()` (replaces `getClaudePluginRoot()`)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`

### Wave 2: Remove methods from JvmScope interface and concrete implementations

- Remove `getClaudeSessionId()`, `getProjectPath()`, `getClaudePluginRoot()`, `getClaudeEnvFile()`
  from the `JvmScope` interface.
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`

- Remove the `@Override` implementations and backing `ConcurrentLazyReference` fields
  (`claudeProjectPath`, `claudePluginRoot`, `claudeSessionId`, `claudeEnvFile`) from `MainJvmScope`.
  Change the constructor to call `super(new ClaudeEnv())` (passing a production `ClaudeEnv` that reads
  `System.getenv()`).
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java`

- Remove the `@Override getProjectPath()`, `@Override getClaudePluginRoot()`,
  `@Override getClaudeSessionId()`, `@Override getClaudeEnvFile()` method bodies and their backing
  fields (`claudeProjectPath`, `claudePluginRoot`, `claudeSessionId`, `claudeEnvFile`) from
  `TestJvmScope`. Instead, build a test `ClaudeEnv` using `SharedSecrets.newClaudeEnv(Map)` and
  pass it to `super()`. Each existing constructor must populate the map as follows:
  - `CLAUDE_PROJECT_DIR` → the `claudeProjectPath` parameter's `toString()` value
  - `CLAUDE_PLUGIN_ROOT` → the `claudePluginRoot` parameter's `toString()` value
  - `CLAUDE_SESSION_ID` → the `claudeSessionId` parameter (default: `"test-session"`)
  - `CLAUDE_ENV_FILE` → the `claudeEnvFile` parameter's `toString()` value (default: a temp file)
  Keep all existing constructor signatures unchanged so downstream test code continues to compile.
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java`

### Wave 3: Update main source call sites

For each main-source file listed in "Files to Modify — Call sites in main source":
- Replace `scope.getClaudeSessionId()` with `scope.getClaudeEnv().getSessionId()`
- Replace `scope.getProjectPath()` with `scope.getClaudeEnv().getProjectPath()`
- Replace `scope.getClaudePluginRoot()` with `scope.getClaudeEnv().getPluginRoot()`
- Replace `scope.getClaudeEnvFile()` with `scope.getClaudeEnv().getEnvFile()`

`scope.getClaudeEnv()` is the new method added to `JvmScope` in Wave 1, backed by the `ClaudeEnv` field
in `AbstractJvmScope`.

Files to update in Wave 3 (all main source call sites listed above):
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionEndHook.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCollectAfterAgent.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCommitBeforeSubagentSpawn.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillDiscovery.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RootCauseAnalyzer.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/InvestigationContextExtractor.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/licensing/Entitlements.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/licensing/LicenseValidator.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockWorktreeIsolationViolation.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMainRebase.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/RequireSkillForCommand.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetNextIssueOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatuslineOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAddOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCheckpointOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetWorkOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCleanupOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetTokenReportOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/DisplayUtils.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSubAgentRules.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectMainAgentRules.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectEnv.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckDataMigration.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckUpdateAvailable.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/SessionEndHandler.java`

### Wave 4: Update test source call sites and run full test suite

- Update test call sites:
  - `PostToolUseFailureHookTest.java`: `scope.getClaudeSessionId()` → `scope.getClaudeEnv().getSessionId()`
  - `PostToolUseHookTest.java`: same
  - `SetPendingAgentResultTest.java`: same
  - `SessionEndHandlerTest.java`: same (3 occurrences)
  - `JvmScopePathResolutionTest.java`: same (2 occurrences)
  - `InjectMainAgentRulesTest.java`: `scope.getProjectPath()` → `scope.getClaudeEnv().getProjectPath()`;
    `scope.getClaudePluginRoot()` → `scope.getClaudeEnv().getPluginRoot()`
  - `WarnApprovalWithoutRenderDiffTest.java`: `scope.getProjectPath()` → `scope.getClaudeEnv().getProjectPath()`
  - `SubagentStartHookTest.java`: same
  - `InjectSubAgentRulesTest.java`: same; also `scope.getClaudePluginRoot()` → `scope.getClaudeEnv().getPluginRoot()`
  - `GetAddOutputPlanningDataTest.java`: all `scope.getProjectPath()` → `scope.getClaudeEnv().getProjectPath()`
  - `SessionEndHookTest.java`: same
  - `TestJvmScopeTest.java` line 82: `scope.getProjectPath()` — update
  - `ClaudeEnvTest.java`: update method names in test method bodies and Javadoc comments:
    `getClaudeSessionId()` → `getSessionId()`, `getClaudePluginRoot()` → `getPluginRoot()`,
    `getClaudeEnvFile()` → `getEnvFile()`
  - `EnforceJvmScopeEnvAccessTest.java` line 82: update comment text that mentions `getClaudeSessionId()`

- Run `mvn -f client/pom.xml test` and fix any remaining compilation or test failures.
- Update `STATE.md` to reflect completion.

## Post-conditions

- [ ] No method named `getClaudeSessionId`, `getProjectPath`, `getClaudePluginRoot`, or `getClaudeEnvFile`
  exists in `JvmScope`, `AbstractJvmScope`, `MainJvmScope`, or `TestJvmScope`
- [ ] `ClaudeEnv` exposes `getSessionId()`, `getProjectPath()`, `getPluginRoot()`, `getEnvFile()`
- [ ] No call site in `client/src/` references `scope.getClaudeSessionId()`, `scope.getProjectPath()`,
  `scope.getClaudePluginRoot()`, or `scope.getClaudeEnvFile()`
- [ ] `mvn -f client/pom.xml test` exits 0 with no compilation errors or test failures
- [ ] E2E: code compiles successfully with no duplicate-method conflicts
