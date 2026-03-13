# Plan: Refactor JvmScope Path API

## Current State

`JvmScope` and `AbstractJvmScope` expose five path methods with inconsistent naming:
- `getEncodedProjectDir()` — helper used only internally by derived methods
- `getSessionBasePath()` — returns `{configDir}/projects/{encodedProject}/`
- `getSessionDirectory()` — returns `{configDir}/projects/{encodedProject}/{sessionId}/`
- `getProjectCatDir()` — returns `{configDir}/projects/{encodedProject}/cat/`
- `getSessionCatDir()` — returns `{configDir}/projects/{encodedProject}/{sessionId}/cat/`

There is also no `getWorkDir()` method exposing the working directory.

## Target State

The interface and abstract class are renamed to:
- `getWorkDir()` added — returns the working directory (`Path.of(System.getProperty("user.dir"))` in
  `MainJvmScope`, injectable in `TestJvmScope`)
- `getEncodedProjectDir()` inlined into `getClaudeProjectPath()` and removed from the interface
- `getSessionBasePath()` → `getClaudeProjectPath()` — same path, clearer name
- `getSessionDirectory()` → `getClaudeSessionPath()` — same path, clearer name
- `getProjectCatDir()` → `getCatProjectPath()` — same path, clearer name
- `getSessionCatDir()` → `getCatSessionPath()` — same path, clearer name

All ~65 production call sites and ~224 test call sites are updated to the new names.
`JvmScopePathResolutionTest` is updated to use new method names and to cover `getWorkDir()`.

## Parent Requirements

None (internal tech debt refactor)

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** All renames are within the internal Java module. No external callers.
- **Mitigation:** Purely mechanical rename across a closed module; tests enforce correctness. All tests
  must pass after the rename.

## Files to Modify

### Interface and implementation
- `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java` — rename methods, add
  `getWorkDir()`, remove `getEncodedProjectDir()`
- `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java` — rename overrides,
  inline `getEncodedProjectDir()` into `getClaudeProjectPath()`, remove separate override
- `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java` — add `getWorkDir()`
  implementation (reads `user.dir` system property)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java` — add `getWorkDir()`
  parameter/field, update method names

### Production call sites (65 occurrences across ~10 files)
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/InvestigationContextExtractor.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetFile.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueLock.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/RestoreCwdAfterCompaction.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/ClearAgentMarkers.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckUpdateAvailable.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckDataMigration.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/WarnUnknownTerminal.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/RestoreWorktreeOnResume.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetSubagentStatusOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnsafeRemoval.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/WarnMainWorkspaceCommit.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/RequireSkillForCommand.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockMainRebase.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockWorktreeIsolationViolation.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/ask/WarnApprovalWithoutRenderDiff.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseHook.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseFailureHook.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionEndHook.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCollectAfterAgent.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceCommitBeforeSubagentSpawn.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/PreCompactHook.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectValidationWithoutEvidence.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/SetPendingAgentResult.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectAssistantGivingUp.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java`

### Test call sites (~224 occurrences across ~30 test files)
All test files listed in `client/src/test/java/io/github/cowwoc/cat/hooks/test/` that call the
renamed methods (see grep output above for the full list).

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Rename methods in `JvmScope.java`:
  - Remove `getEncodedProjectDir()` from the interface
  - Rename `getSessionBasePath()` → `getClaudeProjectPath()`
  - Rename `getSessionDirectory()` → `getClaudeSessionPath()`
  - Rename `getProjectCatDir()` → `getCatProjectPath()`
  - Rename `getSessionCatDir()` → `getCatSessionPath()`
  - Add `getWorkDir()` with Javadoc: "Returns the current working directory."
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java`

- Update `AbstractJvmScope.java`:
  - Remove `getEncodedProjectDir()` override; inline its logic directly into `getClaudeProjectPath()`
  - Rename `getSessionBasePath()` override to `getClaudeProjectPath()`; body:
    `return getClaudeConfigDir().resolve("projects").resolve(encodeProjectPath(getClaudeProjectDir().toString()));`
  - Rename `getSessionDirectory()` override to `getClaudeSessionPath()`; body:
    `return getClaudeProjectPath().resolve(getClaudeSessionId());`
  - Rename `getProjectCatDir()` override to `getCatProjectPath()`; body:
    `return getClaudeProjectPath().resolve("cat");`
  - Rename `getSessionCatDir()` override to `getCatSessionPath()`; body:
    `return getClaudeSessionPath().resolve("cat");`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java`

- Update `MainJvmScope.java`:
  - Add `getWorkDir()` override:
    ```java
    @Override
    public Path getWorkDir()
    {
      ensureOpen();
      return Path.of(System.getProperty("user.dir"));
    }
    ```
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java`

- Update `TestJvmScope.java`:
  - Add a `workDir` field (injectable via constructor); default to `claudeProjectDir` in existing
    constructors that do not accept it
  - Add `getWorkDir()` override returning the injected `workDir`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java`

### Wave 2

- Update all production call sites — perform the following renames across all main source files listed
  in "Files to Modify":
  - `getEncodedProjectDir()` → remove; any site calling this directly should instead use
    `AbstractJvmScope.encodeProjectPath(scope.getClaudeProjectDir().toString())`
  - `getSessionBasePath()` → `getClaudeProjectPath()`
  - `getSessionDirectory()` → `getClaudeSessionPath()`
  - `getProjectCatDir()` → `getCatProjectPath()`
  - `getSessionCatDir()` → `getCatSessionPath()`
  - Files: all production files listed above under "Production call sites"

- Update all test call sites — perform the same renames across all test files listed in the grep
  output:
  - `getSessionBasePath()` → `getClaudeProjectPath()`
  - `getSessionDirectory()` → `getClaudeSessionPath()`
  - `getProjectCatDir()` → `getCatProjectPath()`
  - `getSessionCatDir()` → `getCatSessionPath()`
  - Files: all test files that contain the old method names

- Update `JvmScopePathResolutionTest.java`:
  - Rename all test methods and their bodies to reference new method names
  - Add a new test `getWorkDirReturnsInjectedPath()` that verifies `scope.getWorkDir()` returns the
    path passed to `TestJvmScope`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/JvmScopePathResolutionTest.java`

### Wave 3

- Run `mvn -f client/pom.xml test` and verify exit code 0
- Update `STATE.md`: set Status to `closed`, Progress to `100%`
- Files: `.cat/issues/v2/v2.1/refactor-jvmscope-path-api/STATE.md`

## Post-conditions

- [ ] `JvmScope` interface contains `getWorkDir()`, `getClaudeProjectPath()`, `getClaudeSessionPath()`,
  `getCatProjectPath()`, `getCatSessionPath()` and does NOT contain `getEncodedProjectDir()`,
  `getSessionBasePath()`, `getSessionDirectory()`, `getProjectCatDir()`, or `getSessionCatDir()`
- [ ] No occurrences of old method names remain in any `.java` file under `client/src/`
- [ ] `mvn -f client/pom.xml test` exits with code 0 (all tests pass)
