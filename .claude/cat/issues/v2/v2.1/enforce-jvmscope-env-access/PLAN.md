# Plan: enforce-jvmscope-env-access

## Current State
The `inject-env-via-jvmscope` issue centralized `CLAUDE_PROJECT_DIR` and `CLAUDE_PLUGIN_ROOT` into `JvmScope`, but
many hook files still call `System.getenv()` directly. There is no enforcement mechanism to prevent future direct
env access.

## Target State
All hook files access environment variables exclusively through `JvmScope`. A compile-time or test-time enforcement
check prevents regressions. Only `MainJvmScope` is allowed to call `System.getenv()`.

## Satisfies
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Handler constructors may change to accept JvmScope
- **Mitigation:** Incremental migration, all tests must pass after each handler change

## Files to Modify
- `hooks/src/main/java/.../JvmScope.java` - Add accessors for remaining env vars (CLAUDE_SESSION_ID, CLAUDE_ENV_FILE)
- `hooks/src/main/java/.../MainJvmScope.java` - Implement new lazy env var accessors
- `hooks/src/test/java/.../test/TestJvmScope.java` - Add test implementations for new accessors
- `hooks/src/main/java/.../InjectEnv.java` - Migrate to JvmScope
- `hooks/src/main/java/.../SessionUnlock.java` - Migrate to JvmScope
- `hooks/src/main/java/.../DisplayUtils.java` - Migrate to JvmScope
- `hooks/src/main/java/.../MergeAndCleanup.java` - Migrate to JvmScope in main()
- `hooks/src/main/java/.../GetConfigOutput.java` - Migrate to JvmScope
- `hooks/src/main/java/.../GetNextTaskOutput.java` - Migrate to JvmScope
- `hooks/src/main/java/.../GetRenderDiffOutput.java` - Migrate to JvmScope
- `hooks/src/main/java/.../GetStatusOutput.java` - Migrate to JvmScope
- `hooks/src/main/java/.../GetTokenReportOutput.java` - Migrate to JvmScope
- `hooks/src/main/java/.../RunGetStatusOutput.java` - Migrate to JvmScope in main()
- `hooks/src/main/java/.../WarnApprovalWithoutRenderDiff.java` - Migrate to JvmScope
- `hooks/src/main/java/.../EnforceApprovalBeforeMerge.java` - Migrate to JvmScope
- `hooks/src/main/java/.../TerminalType.java` - Migrate terminal detection env vars to JvmScope

## Files to Create
- `hooks/src/test/java/.../test/EnforceJvmScopeEnvAccessTest.java` - Test that scans source for System.getenv outside MainJvmScope

## Execution Steps
1. Add `getClaudeSessionId()` and `getClaudeEnvFile()` to `JvmScope` interface
2. Add `getTerminalType()` to `JvmScope` interface (wraps TerminalType detection)
3. Implement new accessors in `MainJvmScope` with `ConcurrentLazyReference`
4. Implement new accessors in `TestJvmScope` with constructor-injected values
5. Migrate each handler file to accept `JvmScope` and use scope methods instead of `System.getenv()`
6. For `main()` entry points (MergeAndCleanup, RunGetStatusOutput), create `MainJvmScope` at entry and pass through
7. Create `EnforceJvmScopeEnvAccessTest` that scans all `.java` source files for `System.getenv` and fails if found outside `MainJvmScope.java`
8. Run `mvn -f hooks/pom.xml test` to verify all tests pass

## Success Criteria
- [ ] No Java file except `MainJvmScope.java` contains `System.getenv()`
- [ ] All migrated handlers call `JvmScope` methods (no hardcoded fallbacks or removed env access)
- [ ] `EnforceJvmScopeEnvAccessTest` scans all `.java` source files and fails listing any violations
- [ ] All existing tests pass without modification (`mvn -f hooks/pom.xml test`)