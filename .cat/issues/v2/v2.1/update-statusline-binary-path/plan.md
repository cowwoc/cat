# Plan

## Goal

Replace CAT session-start, statusline, hook registration, and plugin instruction runtime path handling so executable launchers use the runtime bundled under the plugin root directly, and remove obsolete jlink bundle workflows that only exist to support installing/copying runtime binaries into plugin data.

## Problem Statement

Current Claude session startup still performs runtime acquisition and install/copy behavior into `${CLAUDE_PLUGIN_DATA}/client` before invoking Java. Statusline configuration, Claude hook registration, and several plugin instructions also point to commands under `${CLAUDE_PLUGIN_DATA}/client/bin` or `${CAT_PLUGIN_DATA}/client/bin`. The runtime is now bundled with the plugin artifact under `${CLAUDE_PLUGIN_ROOT}/client`, so the issue must pivot from plugin-data runtime installation to direct plugin-root runtime usage for executable launchers.

## Research Findings

- `client/plugin/hooks/claude/session-start.sh` contains runtime acquisition/install logic (`try_acquire_runtime`, `install_bundled_runtime`, lock handling, and `${CLAUDE_PLUGIN_DATA}/client` validations) before Java dispatch.
- `client/plugin/rules/claude/cat-environment.md` currently describes `CAT_PLUGIN_DATA` as the location for generated runtime artifacts including the jlink client.
- `client/plugin/skills/claude/statusline/first-use.md` invokes `${CLAUDE_PLUGIN_DATA}/client/bin/statusline-install` and passes `${CLAUDE_PLUGIN_DATA}` as install input.
- `client/plugin/hooks/claude/hooks.json` still invokes Claude hook launchers from `${CLAUDE_PLUGIN_DATA}/client/bin`, which would break once session-start stops installing runtime binaries into plugin data.
- Several plugin concepts, skill instructions, agent instructions, and instruction-test scenarios still show executable launcher paths under `${CLAUDE_PLUGIN_DATA}/client/bin` or `${CAT_PLUGIN_DATA}/client/bin`.
- `client/cli/src/main/java/io/github/cowwoc/cat/claude/hook/util/StatuslineInstall.java` currently models statusline command installation around plugin-data runtime paths.
- CI still includes release-bundle workflows tied to first-use runtime download/install semantics:
  - `.github/workflows/build-jlink-bundle.yml`
  - `.github/workflows/build-jdk-bundle.yml`
- Runtime/plugin artifact assembly is generated in-repo via:
  - `client/cli/build-jlink.sh`
  - `client/distribution/scripts/build-runtime-artifacts.sh`
  - `client/distribution/pom.xml`
  - `client/cli/src/main/java/io/github/cowwoc/cat/agent/PluginArtifactBuilder.java`

## Jobs

### Job 1: Validate runtime acquisition requirements before removal

Files to inspect:
- `client/plugin/hooks/claude/session-start.sh`
- `client/cli/src/main/java/io/github/cowwoc/cat/codex/hook/SessionStartHook.java`
- `client/cli/src/main/java/io/github/cowwoc/cat/claude/hook/skills/InstructionTestRunner.java`
- `.github/workflows/build-jlink-bundle.yml`
- `.github/workflows/build-jdk-bundle.yml`
- `client/distribution/scripts/build-runtime-artifacts.sh`
- `client/cli/src/main/java/io/github/cowwoc/cat/agent/PluginArtifactBuilder.java`

Execution steps:
- Confirm whether Claude or Codex startup paths still require download/copy/install of runtime binaries into plugin data at runtime.
- Confirm whether any release/runtime acquisition logic still consumes externally published jlink/JDK bundles for startup.
- Document whether both bundle workflows are obsolete, or whether one must remain for a non-startup consumer.
- Gate runtime-removal edits on this validation so no active startup path is broken.

### Job 2: Switch session-start to plugin-root runtime

Files to modify:
- `client/plugin/hooks/claude/session-start.sh`
- `client/plugin/hooks/claude/hooks.json`
- `client/plugin/hooks/common/README.md`
- `client/plugin/rules/claude/cat-environment.md`
- Plugin instruction/reference files that invoke `${CLAUDE_PLUGIN_DATA}/client/bin/<launcher>` or `${CAT_PLUGIN_DATA}/client/bin/<launcher>`

Execution steps:
- Remove or bypass runtime acquisition/install/copy behavior that targets `${CLAUDE_PLUGIN_DATA}/client`.
- Make Java dispatch resolve from `${CLAUDE_PLUGIN_ROOT}/client/bin/java`.
- Make Claude hook registration and plugin-facing command examples resolve launchers from `${CLAUDE_PLUGIN_ROOT}/client/bin/<launcher>` or `${CAT_PLUGIN_ROOT}/client/bin/<launcher>`.
- Keep failure handling/logging for missing runtime binaries, but report plugin-root runtime errors instead of plugin-data install failures.
- Update environment-rule guidance so plugin-root is the runtime source for bundled client binaries.

### Job 3: Switch statusline install + skill flow to plugin-root runtime

Files to modify:
- `client/plugin/skills/claude/statusline/first-use.md`
- `client/cli/src/main/java/io/github/cowwoc/cat/claude/hook/util/StatuslineInstall.java`

Files to update for behavior coverage:
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/StatuslineInstallTest.java`
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/StatuslineInstallMainTest.java`
- `tests/hooks/session-start.bats`

Execution steps:
- Change statusline skill invocation path from `${CLAUDE_PLUGIN_DATA}/client/bin/statusline-install` to `${CLAUDE_PLUGIN_ROOT}/client/bin/statusline-install`.
- Update install arguments and command-path composition so installed statusline command points to `${CLAUDE_PLUGIN_ROOT}/client/bin/statusline-command`.
- Rename method/parameter semantics in `StatuslineInstall` from plugin-data-root meaning to plugin-root meaning, including usage/help strings and JSON output fields where needed.
- Update tests to assert plugin-root command paths and reject regressions back to plugin-data paths.

### Job 4: Remove obsolete CI/build bundle workflow paths (if validated obsolete)

Files to modify:
- `.github/workflows/build-jlink-bundle.yml`
- `.github/workflows/build-jdk-bundle.yml`

Files to inspect/modify if workflow references must be aligned:
- `client/cli/build-jlink.sh`
- `client/distribution/scripts/build-runtime-artifacts.sh`
- `client/distribution/pom.xml`
- `client/cli/src/main/java/io/github/cowwoc/cat/agent/PluginArtifactBuilder.java`
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/common/PluginArtifactBuilderTest.java`

Execution steps:
- Remove workflow steps/jobs that produce separate downloadable jlink/JDK bundles intended for runtime installation into plugin data, if Job 1 confirms no remaining consumer.
- Ensure distribution/plugin artifact build still produces and bundles runtime under plugin root without depending on removed workflows.
- Align affected tests and build messaging with plugin-root runtime distribution model.

### Job 5: Regression validation

Files to run/update as needed:
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/SessionStartHookTest.java`
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/SessionStartHookMainTest.java`
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/CodexSessionStartHookTest.java`
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/StatuslineInstallTest.java`
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/StatuslineInstallMainTest.java`
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/common/PluginArtifactBuilderTest.java`

Execution steps:
- Update/add tests for Claude session startup Java path resolution to plugin-root runtime.
- Remove obsolete shell tests for deleted runtime download/install/lock helpers and replace them with plugin-root runtime launch coverage.
- Verify Codex behavior is unchanged where it already uses plugin-root runtime launchers.
- Run full client verification suite required by project policy.

## Post-conditions

- [x] Session startup invokes Java from `${CLAUDE_PLUGIN_ROOT}/client/bin/java`.
- [x] Statusline install writes `${CLAUDE_PLUGIN_ROOT}/client/bin/statusline-command` into `.claude/settings.json`.
- [x] Claude hook registration and plugin-facing binary command examples invoke `${CLAUDE_PLUGIN_ROOT}/client/bin/<launcher>` or `${CAT_PLUGIN_ROOT}/client/bin/<launcher>` instead of plugin-data launcher paths.
- [x] No Claude/Codex startup code path installs or copies jlink runtime into `${CLAUDE_PLUGIN_DATA}/client`.
- [x] Hook tests no longer assert deleted runtime download/install/lock helper behavior.
- [x] Obsolete CI jlink/JDK bundle creation for plugin-data installation is removed (or explicitly retained with documented non-obsolete consumer identified in validation).
- [x] `client/plugin/hooks/claude/session-start.sh`, `client/plugin/rules/claude/cat-environment.md`, `client/plugin/skills/claude/statusline/first-use.md`, and `client/cli/src/main/java/io/github/cowwoc/cat/claude/hook/util/StatuslineInstall.java` all reflect plugin-root runtime usage.
- [x] Relevant tests pass, including runtime-path and statusline install coverage under `client/cli/src/test/java/io/github/cowwoc/cat/client/test/`.
- [x] `mvn -f client/pom.xml verify -e` passes.
