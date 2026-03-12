# Plan: refactor-rules-injection-classes

## Current State

`InjectCatRules` (in `session/` package) handles main-agent rules injection and implements `SessionStartHandler`.
`SubagentStartHook.getCatRules()` (lines 128-144) contains inline subagent rules injection logic using
`RulesDiscovery.filterForSubagent()`. The naming is inconsistent and the subagent logic is not extracted
into a dedicated handler class like the main-agent logic is.

## Target State

- `InjectCatRules` renamed to `InjectRulesToMainAgent` (same package, same interface)
- New class `InjectRulesToSubAgent` in the same package, encapsulating the subagent rules discovery logic
  currently inline in `SubagentStartHook.getCatRules()`
- `SubagentStartHook` delegates to `InjectRulesToSubAgent` the same way `SessionStartHook` delegates to
  `InjectRulesToMainAgent`

## Satisfies

None — code quality refactor

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — internal class rename, no public API change
- **Mitigation:** All references are within the same module; tests cover both injection paths

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectCatRules.java` — rename to `InjectRulesToMainAgent.java`
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectRulesToSubAgent.java` — new file
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionStartHook.java` — update import and reference (line 17, 57)
- `client/src/main/java/io/github/cowwoc/cat/hooks/SubagentStartHook.java` — replace `getCatRules()` method
  (lines 128-144) with delegation to `InjectRulesToSubAgent`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/InjectCatRulesTest.java` — rename to
  `InjectRulesToMainAgentTest.java`, update class references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SubagentStartHookTest.java` — update if it references
  `InjectCatRules` directly
- New test: `client/src/test/java/io/github/cowwoc/cat/hooks/test/InjectRulesToSubAgentTest.java`

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Rename `InjectCatRules` to `InjectRulesToMainAgent`:
  - Files: `InjectCatRules.java`, `SessionStartHook.java`, `InjectCatRulesTest.java`
  - Rename class file: `InjectCatRules.java` -> `InjectRulesToMainAgent.java`
  - Update class name, Javadoc, and constructor name inside the file
  - Update import and instantiation in `SessionStartHook.java` (lines 17, 57)
  - Rename test file: `InjectCatRulesTest.java` -> `InjectRulesToMainAgentTest.java`
  - Update class name and references inside the test file
  - Delete old files after creating new ones

- Create `InjectRulesToSubAgent`:
  - Files: new `session/InjectRulesToSubAgent.java`
  - Extract `SubagentStartHook.getCatRules()` logic (lines 128-144) into a new class
  - The class takes `JvmScope` in constructor (same pattern as `InjectRulesToMainAgent`)
  - Public method: `String getRules(HookInput input)` — accepts `HookInput` to extract `subagent_type`
  - Encapsulates: reading `subagent_type`, resolving `.cat/rules` path, calling
    `RulesDiscovery.getCatRulesForAudience()` with `filterForSubagent`
  - Add license header

- Update `SubagentStartHook` to delegate:
  - Files: `SubagentStartHook.java`
  - Replace `getCatRules()` method body with delegation to `new InjectRulesToSubAgent(scope).getRules(input)`
  - Or store `InjectRulesToSubAgent` as a field, matching `SessionStartHook`'s pattern

- Create `InjectRulesToSubAgentTest`:
  - Files: new `test/InjectRulesToSubAgentTest.java`
  - Test: blank subagent_type returns rules matching all subagents
  - Test: specific subagent_type filters correctly
  - Test: empty rules directory returns empty string
  - Follow existing `InjectCatRulesTest` patterns for test structure

- Run `mvn -f client/pom.xml verify` to confirm all tests pass and no lint violations
- Update STATE.md: status closed, progress 100%
- Commit: `refactor: rename InjectCatRules to InjectRulesToMainAgent and extract InjectRulesToSubAgent`

## Post-conditions

- [ ] `InjectCatRules.java` no longer exists; replaced by `InjectRulesToMainAgent.java`
- [ ] `InjectRulesToSubAgent.java` exists with subagent rules injection logic
- [ ] `SubagentStartHook` delegates rules injection to `InjectRulesToSubAgent`
- [ ] `SessionStartHook` references `InjectRulesToMainAgent` (not `InjectCatRules`)
- [ ] All tests pass (`mvn -f client/pom.xml verify` exits 0)
- [ ] No regressions: main-agent and subagent rules injection produce identical output as before
- [ ] E2E: Run `mvn -f client/pom.xml verify` and confirm BUILD SUCCESS with no test failures or lint violations
