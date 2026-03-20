# Plan: rename-claude-hook-types

## Type
Refactor

## Current State
`HookInput` and `HookOutput` are named without the "Claude" prefix, obscuring their purpose as Claude-specific
hook I/O types. Additionally, `JvmScope` contains three Claude infrastructure path methods (`getClaudeConfigDir()`,
`getClaudeSessionsPath()`, `getClaudeSessionPath()`) that belong in `ClaudeEnv` alongside the env var access they
are derived from.

## Target State
- `HookInput` renamed to `ClaudeHookInput` and `HookOutput` renamed to `ClaudeHookOutput` throughout the codebase
- `getClaudeConfigDir()`, `getClaudeSessionsPath()`, `getClaudeSessionPath()` moved from `JvmScope` to `ClaudeEnv`
- All callers updated accordingly
- `getCatWorkPath()` and `getCatSessionPath()` remain in `JvmScope` (CAT project paths, not Claude-specific)

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Internal API — `HookInput`/`HookOutput` class names change, `JvmScope` loses three methods.
  No external API impact (package-private usage only).
- **Mitigation:** Compile-time safety via `find`+`sed` bulk rename; all callers found by grep; tests validate
  completeness.

## Research Findings
### Rename scope
Approximately 90 Java files under `client/src/` reference `HookInput` and/or `HookOutput`. Files to rename:
- `client/src/main/java/io/github/cowwoc/cat/hooks/HookInput.java` → `ClaudeHookInput.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/HookOutput.java` → `ClaudeHookOutput.java`

### Methods to move from JvmScope → ClaudeEnv
- `getClaudeConfigDir()` — returns `$CLAUDE_CONFIG_DIR` path; used by `SkillDiscovery.java` (2×)
- `getClaudeSessionsPath()` — returns `{claudeConfigDir}/projects/{encodedProjectPath}/`; used by `SessionAnalyzer.java`,
  `GetSkill.java`, `InvestigationContextExtractor.java`, `GetSubagentStatusOutput.java` (2×), `EnforceStatusOutput.java`,
  `BlockUnauthorizedMergeCleanup.java`, `RequireSkillForCommand.java`
- `getClaudeSessionPath(String sessionId)` — returns `{claudeSessionsPath}/{sessionId}/`; used by
  `SessionEndHandler.java`, `WarnUnknownTerminal.java`

### Methods staying in JvmScope
- `getCatWorkPath()` — returns `{projectPath}/.cat/work/` — CAT project structure, not Claude-specific
- `getCatSessionPath(String sessionId)` — returns `{projectPath}/.cat/work/sessions/{sessionId}/` — same

### TestJvmScope
`TestJvmScope` implements `JvmScope`; it must also implement the remaining methods and be updated to no longer
implement the removed methods.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/HookInput.java` — rename to `ClaudeHookInput.java`, update class name
- `client/src/main/java/io/github/cowwoc/cat/hooks/HookOutput.java` — rename to `ClaudeHookOutput.java`, update class name
- All ~90 Java files referencing `HookInput`/`HookOutput` — update import and reference names
- `client/src/main/java/io/github/cowwoc/cat/hooks/JvmScope.java` — remove 3 Claude-path methods
- `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java` — remove implementations of the 3 removed methods
- `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeEnv.java` — add 3 Claude-path methods
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java` — remove implementations of removed methods
- All callers of `scope.getClaudeConfigDir()`, `scope.getClaudeSessionsPath()`, `scope.getClaudeSessionPath()` —
  update to use `ClaudeEnv` instance

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Rename `HookInput` to `ClaudeHookInput`:
  - Git-mv: `cd client && git mv src/main/java/io/github/cowwoc/cat/hooks/HookInput.java src/main/java/io/github/cowwoc/cat/hooks/ClaudeHookInput.java`
  - Update class declaration inside file: change `class HookInput` to `class ClaudeHookInput` (and constructor name if any)
  - Bulk-replace all references: `find client/src -name "*.java" | xargs sed -i 's/\bHookInput\b/ClaudeHookInput/g'`
  - Files to verify: check that no remaining `HookInput` (non-qualified) references exist
- Rename `HookOutput` to `ClaudeHookOutput`:
  - Git-mv: `cd client && git mv src/main/java/io/github/cowwoc/cat/hooks/HookOutput.java src/main/java/io/github/cowwoc/cat/hooks/ClaudeHookOutput.java`
  - Update class declaration inside file: change `class HookOutput` to `class ClaudeHookOutput` (and constructor name if any)
  - Bulk-replace all references: `find client/src -name "*.java" | xargs sed -i 's/\bHookOutput\b/ClaudeHookOutput/g'`
  - Files to verify: check that no remaining `HookOutput` (non-qualified) references exist

### Wave 2
- Move Claude-path methods from `JvmScope` to `ClaudeEnv`:
  - In `ClaudeEnv.java`: add three methods (`getClaudeConfigDir()`, `getClaudeSessionsPath()`,
    `getClaudeSessionPath(String sessionId)`) with implementations equivalent to those in `MainJvmScope.java`
  - In `JvmScope.java`: remove the three method declarations
  - In `MainJvmScope.java`: remove the three method implementations (they are now in `ClaudeEnv`)
  - In `TestJvmScope.java`: remove the three method implementations (they are no longer in the interface)
- Update all callers of `scope.getClaudeConfigDir()`, `scope.getClaudeSessionsPath()`, `scope.getClaudeSessionPath()`:
  - Files: `SkillDiscovery.java`, `SessionAnalyzer.java`, `GetSkill.java`, `InvestigationContextExtractor.java`,
    `GetSubagentStatusOutput.java`, `EnforceStatusOutput.java`, `BlockUnauthorizedMergeCleanup.java`,
    `RequireSkillForCommand.java`, `SessionEndHandler.java`, `WarnUnknownTerminal.java`
  - Pattern: replace `scope.getClaudeXxx(...)` with `new ClaudeEnv().getClaudeXxx(...)`
  - Note: each call site must construct a `ClaudeEnv` instance; check if a `ClaudeEnv` field already exists in the
    class — if so, reuse it rather than constructing a new one per call

### Wave 3
- Run `mvn -f client/pom.xml test` from the worktree root to verify all tests pass
- Fix any compilation errors or test failures found
- Update `.cat/issues/v2/v2.1/rename-claude-hook-types/index.json` to set `status` to `closed`

## Post-conditions
- [ ] `ClaudeHookInput.java` exists; `HookInput.java` does not exist anywhere in `client/src/`
- [ ] `ClaudeHookOutput.java` exists; `HookOutput.java` does not exist anywhere in `client/src/`
- [ ] No remaining `import.*HookInput` or `import.*HookOutput` references in any Java file
- [ ] `JvmScope` no longer declares `getClaudeConfigDir()`, `getClaudeSessionsPath()`, or `getClaudeSessionPath()`
- [ ] `ClaudeEnv` declares and implements all three moved methods
- [ ] All tests pass: `mvn -f client/pom.xml test` exits 0
