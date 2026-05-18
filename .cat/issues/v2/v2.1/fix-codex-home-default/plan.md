# Plan: fix-codex-home-default

## Problem

Codex shared CLI scope derivation fails when `CODEX_HOME` is unset in a Codex runtime context. The resolver currently
requires `CAT_PLUGIN_DATA` unless `CODEX_HOME` is explicitly present, which diverges from Codex behavior that defaults
to `~/.codex`.

## Parent Requirements

None

## Reproduction Code

```java
Map<String, String> environment = new HashMap<>();
environment.put("CODEX_THREAD_ID", "codex-session");
Map<String, String> properties = Map.of(
  "cat.launcher.dir", launcherDir.toString(),
  "user.home", userHome.toString());
new MainCliTool(environment::get, properties::get, projectPath);
```

## Expected vs Actual

- **Expected:** In Codex runtime, missing `CODEX_HOME` resolves to `user.home/.codex`; `pluginData` resolves to
  `<user.home>/.codex/plugins/data/cat-cat` without requiring `CAT_PLUGIN_DATA`.
- **Actual:** `pluginData` falls back to `requiredEnvironmentValue("CAT_PLUGIN_DATA")`, causing fast-fail when
  `CAT_PLUGIN_DATA` is absent.

## Research Findings

- `client/common-cli/src/main/java/io/github/cowwoc/cat/tool/AbstractCliTool.java`
  - `RuntimeValueResolver.configPath()` already defaults Codex config to `user.home/.codex` when `CODEX_HOME` and
    `CAT_CONFIG_DIR` are absent.
  - `RuntimeValueResolver.pluginData()` derives plugin data from `CODEX_HOME` only when `CODEX_HOME` is present, then
    falls back to required `CAT_PLUGIN_DATA`.
  - `RuntimeDetector.resolveRuntime()` can classify Codex by launcher context (`looksLikeCodexLauncher()`) even when
    `CODEX_HOME` is missing, which exposes the plugin-data derivation gap.
- `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/MainCliToolTest.java`
  - Existing tests cover Codex default config path (`codexRuntimeDefaultsConfigDirToUserHome`) and explicit
    `CODEX_HOME`, but do not assert Codex default plugin-data derivation when `CODEX_HOME` is absent.

## Approach Analysis

### Approach A: Derive plugin data from resolved Codex config path (selected)

- **Risk:** LOW
- **Scope:** 2 files (moderate)
- **Description:** In `RuntimeValueResolver.pluginData()`, when runtime is Codex, resolve plugin data from the same
  Codex home basis used by config-path derivation (explicit `CODEX_HOME` when present, otherwise `user.home/.codex`).
  Add regression tests for unset `CODEX_HOME` + present `CODEX_THREAD_ID`.
- **Pros:** Aligns plugin-data and config-path semantics; minimal behavior change for Claude and explicit CAT aliases.
- **Cons:** Requires careful test coverage to avoid breaking explicit `CAT_PLUGIN_DATA` fallback behavior outside Codex.

### Approach B: Require `CAT_PLUGIN_DATA` whenever `CODEX_HOME` is absent (rejected)

- **Risk:** MEDIUM
- **Scope:** 1 file (minimal)
- **Description:** Keep current fallback and document stricter env requirements.
- **Rejection rationale:** Contradicts Codex default behavior and issue goal; preserves existing bug.

### Approach C: Compute plugin data in runtime detector/harness phase (rejected)

- **Risk:** MEDIUM
- **Scope:** 3-4 files (comprehensive)
- **Description:** Materialize derived environment aliases before value-resolution.
- **Rejection rationale:** Adds architectural complexity and coupling without additional functional value for this fix.

## Root Cause

`RuntimeValueResolver.pluginData()` in `AbstractCliTool` treats Codex plugin-data derivation as conditional on explicit
`CODEX_HOME`, while `configPath()` already supports implicit default Codex home (`user.home/.codex`). The inconsistent
derivation rules create a false requirement for `CAT_PLUGIN_DATA` in valid Codex sessions.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Codex environments that intentionally rely on `CAT_PLUGIN_DATA` fallback semantics might change if
  runtime detection incorrectly identifies Codex.
- **Mitigation:** Keep runtime detection unchanged; constrain new behavior to `runtime == AgentRuntime.CODEX`; add
  regression tests for explicit `CODEX_HOME`, launcher-based Codex detection, and existing Claude behavior.

## Files to Modify

- `client/common-cli/src/main/java/io/github/cowwoc/cat/tool/AbstractCliTool.java`
  - Update `RuntimeValueResolver.pluginData()` to derive Codex plugin data from the effective Codex home path:
    `CODEX_HOME` when set, otherwise `userHome().resolve(".codex")`.
  - Preserve existing Claude path handling and CAT alias fallback for non-Codex runtimes.
- `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/MainCliToolTest.java`
  - Add regression test(s) for Codex runtime with `CODEX_THREAD_ID` present, `CODEX_HOME` absent, launcher context
    present, and `user.home` provided; assert `pluginData == userHome/.codex/plugins/data/cat-cat`.
  - Keep coverage for explicit `CODEX_HOME` and blank-value fast-fail behavior unchanged.

## Test Cases

- [ ] Original bug scenario: Codex runtime with `CODEX_THREAD_ID` set and `CODEX_HOME` unset resolves plugin data from
  `user.home/.codex/plugins/data/cat-cat` without requiring `CAT_PLUGIN_DATA`.
- [ ] Explicit `CODEX_HOME` still resolves plugin data to `CODEX_HOME/plugins/data/cat-cat`.
- [ ] Claude runtime behavior remains unchanged.
- [ ] Blank explicit `CODEX_HOME` (`" \t"`) still fails fast as invalid explicit value.

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- Add/adjust failing test first in
  `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/MainCliToolTest.java` for Codex runtime where:
  `CODEX_THREAD_ID` is set, `CODEX_HOME` is unset, launcher path indicates Codex, and `user.home` is provided.
- Run targeted test command to confirm failure before implementation:
  `mvn -f client/pom.xml -Dtest=MainCliToolTest test`
- Update `RuntimeValueResolver.pluginData()` in
  `client/common-cli/src/main/java/io/github/cowwoc/cat/tool/AbstractCliTool.java`:
  - Keep Claude branch unchanged.
  - For Codex branch, compute effective home:
    `String codexHome = values.environmentValue("CODEX_HOME");`
    `Path base = (codexHome != null) ? Path.of(codexHome) : userHome().resolve(".codex");`
    `return base.resolve("plugins/data/cat-cat");`
  - Leave non-Codex fallback to `CAT_PLUGIN_DATA` unchanged.
- Re-run targeted suite:
  `mvn -f client/pom.xml -Dtest=MainCliToolTest test`
- Run full verification suite:
  `mvn -f client/pom.xml verify -e`
- Update issue tracking status file
  `.cat/issues/v2/v2.1/fix-codex-home-default/index.json` in the implementation commit to mark completion state
  according to CAT workflow outcome.

## Post-conditions

- [ ] Codex shared CLI scope derivation treats unset `CODEX_HOME` as `user.home/.codex`.
- [ ] Codex plugin-data derivation resolves to `<effective-codex-home>/plugins/data/cat-cat` when runtime is Codex.
- [ ] Existing explicit `CODEX_HOME` behavior continues to work.
- [ ] Regression tests cover unset `CODEX_HOME` with `CODEX_THREAD_ID` present.
- [ ] `mvn -f client/pom.xml verify -e` passes.
