# Plan: split-runtime-rules-directories

## Goal

Split runtime-loaded agent files into product-specific and portable directories:
- `.claude/` for Claude Code-specific files
- `.codex/` for Codex-specific files
- `.agents/` for portable non-rule files shared by both runtimes
- `.cat/rules/{common,claude,codex}/`,
  `client/plugin/rules/{common,claude,codex}/`, `client/plugin/hooks/{common,claude,codex}/`,
  `client/plugin/skills/{common,claude,codex}/`, and `client/plugin/agents/{common,claude,codex}/` for CAT-managed
  runtime-loaded files

Rules must use CAT-owned paths rather than product-owned portable rule directories:
- Project-local portable rules: `.cat/rules/common/*`
- Project-local Claude-specific rules: `.claude/rules/*` and `.cat/rules/claude/*`
- Project-local Codex-specific rules: `.cat/rules/codex/*`
- Plugin portable rules: `client/plugin/rules/common/*`
- Plugin Claude-specific rules: `client/plugin/rules/claude/*`
- Plugin Codex-specific rules: `client/plugin/rules/codex/*`

Hooks must use CAT-owned plugin paths rather than product-owned hook directories:
- Plugin portable hooks: `client/plugin/hooks/common/*`
- Plugin Claude-specific hooks: `client/plugin/hooks/claude/*`
- Plugin Codex-specific hooks: `client/plugin/hooks/codex/*`

Skills must use CAT-owned plugin paths with runtime subdirectories:
- Plugin portable skills: `client/plugin/skills/common/*`
- Plugin Claude-specific skills: `client/plugin/skills/claude/*`
- Plugin Codex-specific skills: `client/plugin/skills/codex/*`

Agent definitions must use CAT-owned plugin paths with runtime subdirectories:
- Plugin portable agent bodies: `client/plugin/agents/common/*`
- Plugin Claude-specific agent wrappers: `client/plugin/agents/claude/*`
- Plugin Codex-specific agent definitions: `client/plugin/agents/codex/*`

Rule placement and conflicts:

| Audience | Canonical writable project path | Compatibility path | Load order |
|----------|---------------------------------|--------------------|------------|
| Portable CAT rules | `.cat/rules/common/*` | none | after plugin common rules |
| Claude-specific CAT rules | `.cat/rules/claude/*` | `.claude/rules/*` for Claude-native project conventions and existing user rules | after project common rules |
| Codex-specific CAT rules | `.cat/rules/codex/*` | none | after project common rules |

Claude loads `client/plugin/rules/common/*`, `client/plugin/rules/claude/*`, `.cat/rules/common/*`,
`.cat/rules/claude/*`, then `.claude/rules/*`. Codex loads `client/plugin/rules/common/*`,
`client/plugin/rules/codex/*`, `.cat/rules/common/*`, then `.cat/rules/codex/*`. CAT appends rules from each
directory and does not deduplicate same-named files; same-named rules are additive, not overrides.

Update all plugin/client code that currently reads from a single runtime-specific location so it resolves:
- portable non-rule files from `.agents/**`
- portable rules from `.cat/rules/common/*` and `client/plugin/rules/common/*`
- runtime-specific rule files from the active runtime's rule subdirectories
- portable hooks from `client/plugin/hooks/common/*` and runtime-specific hooks from `client/plugin/hooks/claude/*` or
  `client/plugin/hooks/codex/*`
- portable skills from `client/plugin/skills/common/*` and runtime-specific skills from
  `client/plugin/skills/claude/*` or `client/plugin/skills/codex/*`
- portable agent bodies from `client/plugin/agents/common/*` and runtime-specific agent wrappers/definitions from
  `client/plugin/agents/claude/*` or `client/plugin/agents/codex/*`
- runtime-specific non-rule files from `.claude/**` or `.codex/**`, depending on the active runtime

Claude Code and Codex must be supported simultaneously. Adding Codex support must not remove or break Claude Code
support.

Apply the same separation inside `client/**`: Claude-specific Java code, Codex-specific Java code, and portable shared
Java code must be separated by package/module structure instead of mixing runtime-specific behavior in shared classes.
The separation includes Java CLI entry points and utility scopes: shared CAT command-line utilities must not construct
Claude-specific scopes such as `MainClaudeTool` when running under Codex, and Codex workflows must not need to export
synthetic `CLAUDE_*` variables only to satisfy shared CAT utilities.

## Parent Requirements

None

## Decomposed Into

- `2.1-runtime-neutral-cli-context`

This parent remains `in-progress` while the child issue closes the remaining Java CLI scope and shared utility
runtime-neutrality work. Close the parent only after the child issue is implemented and the parent post-conditions are
verified.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Runtime detection or path resolution regressions; duplicated or conflicting rules between portable
  root rules and runtime-specific subdirectories; Claude-only assumptions leaking into Codex; shared Java CLIs
  continuing to require `CLAUDE_SESSION_ID`, `CLAUDE_PROJECT_DIR`, `CLAUDE_PLUGIN_ROOT`, or `CLAUDE_PLUGIN_DATA`
  when invoked from Codex
- **Mitigation:** Add path-resolution tests for both runtimes, define deterministic merge/precedence behavior, and run
  full verification for rule-loading flows and representative shared CLI invocations under both Claude and Codex

## Files to Modify

- `.claude/**` — Claude Code-specific plugin/project files
- `.codex/**` — Codex-specific plugin/project files
- `.agents/**` — shared portable non-rule files
- `.cat/rules/common/**`, `.cat/rules/claude/**`, `.cat/rules/codex/**` — project-local portable and
  runtime-specific CAT rules
- `client/plugin/rules/common/**`, `client/plugin/rules/claude/**`, `client/plugin/rules/codex/**` — plugin-shipped portable and
  runtime-specific CAT rules
- `client/plugin/hooks/common/**`, `client/plugin/hooks/claude/**`, `client/plugin/hooks/codex/**` — plugin-shipped portable and
  runtime-specific hooks
- `client/plugin/skills/common/**`, `client/plugin/skills/claude/**`, `client/plugin/skills/codex/**` —
  plugin-shipped portable and runtime-specific skills
- `client/plugin/agents/common/**`, `client/plugin/agents/claude/**`, `client/plugin/agents/codex/**` — plugin-shipped portable and
  runtime-specific agent definitions
- `client/pom.xml`, `client/cli/pom.xml`, `client/plugin/pom.xml`, `client/distribution/pom.xml` — Maven parent and
  submodule build definitions
- `client/cli/**` — Java CLI source, tests, jlink scripts, and module-specific build assets
- `client/cli/src/main/java/io/github/cowwoc/cat/claude/tool/**` and any replacement portable scope package — split
  Claude-only environment handling from runtime-neutral CLI scope behavior
- `client/cli/src/main/java/io/github/cowwoc/cat/claude/hook/util/**` and other shared launcher entry points — remove
  `MainClaudeTool` coupling from utilities that are bundled into both Claude and Codex runtime artifacts
- `docs/development/plugin-distribution.md` — developer-facing distribution design
- `client/plugin/**` files that reference `.claude`, `.codex`, `.agents`, `.cat/rules`, `client/plugin/rules`,
  `client/plugin/hooks`, `client/plugin/skills`, `client/plugin/agents`, or legacy shared locations
- `client/**` runtime-specific and shared Java code, including path-resolution code that hardcodes a Claude-only or
  single shared location
- Tests covering runtime detection, shared-file loading, runtime-specific loading, and path references

## Pre-conditions

- [x] All dependent issues are closed

## Jobs

### Job 1

- Inventory every runtime-loaded file under `.claude/`, existing shared locations, and plugin/runtime bootstrap paths
- Inventory `client/**` packages/classes and classify runtime-specific Java code versus portable shared code
- Classify each file as:
  - Claude-specific (`.claude/**`)
  - Codex-specific (`.codex/**`)
  - Shared portable non-rule (`.agents/**`)
  - Project-local portable rule (`.cat/rules/common/*`)
  - Project-local runtime-specific rule (`.cat/rules/claude/*`, `.cat/rules/codex/*`, or `.claude/rules/*`)
  - Plugin portable rule (`client/plugin/rules/common/*`)
  - Plugin runtime-specific rule (`client/plugin/rules/claude/*` or `client/plugin/rules/codex/*`)
  - Plugin portable hook (`client/plugin/hooks/common/*`)
  - Plugin runtime-specific hook (`client/plugin/hooks/claude/*` or `client/plugin/hooks/codex/*`)
  - Plugin portable skill (`client/plugin/skills/common/*`)
  - Plugin runtime-specific skill (`client/plugin/skills/claude/*` or `client/plugin/skills/codex/*`)
  - Plugin portable agent body (`client/plugin/agents/common/*`)
  - Plugin runtime-specific agent wrapper/definition (`client/plugin/agents/claude/*` or `client/plugin/agents/codex/*`)
- Classify client code as:
  - Claude-specific client code
  - Codex-specific client code
  - Shared portable client code
- Inventory every Java CLI launcher bundled into the Codex runtime artifact and identify which launchers still create
  `MainClaudeTool` or otherwise require `CLAUDE_*` variables despite being runtime-neutral CAT utilities
- Define deterministic resolution behavior when runtime-specific and shared files both exist
- Define conflict behavior for same-named shared and runtime-specific rules

### Job 2

- Create the new `.codex/` and `.agents/` directory structures
- Keep Claude Code-specific files under `.claude/`
- Keep project-local portable rules under `.cat/rules/common/*`
- Keep project-local Claude-specific rules under `.claude/rules/*` and/or `.cat/rules/claude/*`
- Keep project-local Codex-specific rules under `.cat/rules/codex/*`
- Move plugin-shipped portable rules to `client/plugin/rules/common/*`
- Move plugin-shipped Claude-specific rules to `client/plugin/rules/claude/*`
- Move plugin-shipped Codex-specific rules to `client/plugin/rules/codex/*`
- Move plugin-shipped portable hooks to `client/plugin/hooks/common/*`
- Move plugin-shipped Claude-specific hooks to `client/plugin/hooks/claude/*`
- Move plugin-shipped Codex-specific hooks to `client/plugin/hooks/codex/*`
- Move plugin-shipped portable skills to `client/plugin/skills/common/*`
- Move plugin-shipped Claude-specific skills to `client/plugin/skills/claude/*`
- Move plugin-shipped Codex-specific skills to `client/plugin/skills/codex/*`
- Move plugin-shipped portable agent bodies to `client/plugin/agents/common/*`
- Move plugin-shipped Claude-specific agent wrappers to `client/plugin/agents/claude/*`
- Move plugin-shipped Codex-specific agent definitions to `client/plugin/agents/codex/*`
- Physically move the plugin source tree from repo-root `plugin/` to Maven submodule `client/plugin/`
- Define a release artifact model that publishes flattened Claude and Codex plugin roots at isolated Git commits or
  immutable tags
- Define local-build installation so development builds use the same flattening pipeline as release builds
- Add Codex equivalents under `.codex/` only for non-rule runtime files where runtime-specific behavior is required
- Introduce or update client package/module boundaries so shared client logic is runtime-neutral, with Claude and Codex
  adapters isolated in runtime-specific packages
- Introduce a portable CLI scope or equivalent runtime-neutral command context for shared CAT utilities. It must read
  `CAT_PROJECT_DIR`, `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`, `CAT_SESSION_ID`, and `CAT_RUNTIME` first, with
  Claude-specific `CLAUDE_*` handling isolated to the Claude adapter or compatibility layer.
- Keep `MainClaudeTool` and Claude hook/tool types available only for genuinely Claude-specific hooks, statusline, and
  runner behavior. Shared utilities such as session-marker, merge, squash, rebase, issue-lock, status/config output,
  and other launchers present in both flattened artifacts must use the portable scope or runtime-specific adapters.
- Remove legacy single-location reads once all active references are migrated

### Job 3

- Update plugin/client code paths that load rules or runtime files so they load shared files plus runtime-specific files
- Ensure call sites pass or derive runtime context (`claude` or `codex`)
- Update shared Java CLI entry points so Codex launchers do not require `CLAUDE_SESSION_ID`, `CLAUDE_PROJECT_DIR`,
  `CLAUDE_PLUGIN_ROOT`, `CLAUDE_PLUGIN_DATA`, or `CLAUDE_CONFIG_DIR` unless the command is explicitly Claude-only.
- Update `write-session-marker`, `read-session-marker`, `merge-and-cleanup`, `git-squash`, `git-rebase`,
  `git-merge-linear`, `git-amend`, `issue-lock`, `work-prepare`, and other shared work-flow utilities to use the
  portable scope or runtime-specific adapters before they are included in Codex runtime artifacts.
- Update skill and agent examples so Codex `cat:work` and approval-gate flows use `CAT_*` variables only, without
  temporary `CLAUDE_*` exports that emulate a Claude session.
- Ensure Claude rule resolution includes `client/plugin/rules/common/*`, `client/plugin/rules/claude/*`,
  `.cat/rules/common/*`, `.cat/rules/claude/*`, and `.claude/rules/*`
- Ensure Codex rule resolution includes `client/plugin/rules/common/*`, `client/plugin/rules/codex/*`, `.cat/rules/common/*`, and
  `.cat/rules/codex/*`
- Ensure Claude hook resolution uses `client/plugin/hooks/common/*` and `client/plugin/hooks/claude/*`
- Ensure Codex hook resolution uses `client/plugin/hooks/common/*` and `client/plugin/hooks/codex/*`
- Ensure Claude skill resolution includes `client/plugin/skills/common/*` and `client/plugin/skills/claude/*`
- Ensure Codex skill resolution includes `client/plugin/skills/common/*` and `client/plugin/skills/codex/*`
- Ensure Claude agent resolution includes `client/plugin/agents/common/*` and `client/plugin/agents/claude/*`
- Ensure Codex agent resolution includes `client/plugin/agents/common/*` and `client/plugin/agents/codex/*`
- Ensure Claude non-rule resolution includes `.agents/**` and `.claude/**`
- Ensure Codex non-rule resolution includes `.agents/**` and `.codex/**`
- Move developer-facing distribution notes to `docs/development/`
- Refactor client runtime-specific conditionals into explicit runtime adapters where practical, keeping shared
  orchestration and file-resolution logic independent of Claude/Codex details
- Update docs, templates, and generated config references that point at old locations
- Remove retrospective-as-archive wording

### Job 4

- Add/adjust tests for runtime detection, runtime-specific file loading, and shared-file inclusion
- Add regression tests proving Claude and Codex can coexist in one checkout without overwriting each other's files
- Add regression tests proving representative shared CLI utilities run in a Codex environment with only `CAT_*`
  variables set and fail only when genuinely required portable variables are missing.
- Add regression tests proving Claude-specific CLIs still accept `CLAUDE_*` through the Claude adapter and shared CLIs
  prefer `CAT_*` values when both `CAT_*` and `CLAUDE_*` are present.
- Add client tests proving shared client code is reused and runtime adapters select the correct
  `client/plugin/rules/{common,claude,codex}`, `.cat/rules/{common,claude,codex}`,
  `client/plugin/hooks/{common,claude,codex}`, `client/plugin/skills/{common,claude,codex}`,
  `client/plugin/agents/{common,claude,codex}`, and `.claude`/`.codex` non-rule inputs
- Add client tests proving same-named rules across common, runtime-specific, and compatibility directories are
  appended in documented load order for both Claude and Codex, without filename-based deduplication
- Run full test suite and verify no remaining active references to deprecated single-location rule paths

### Job 5

- Convert `client/pom.xml` into the Maven parent project with `pom` packaging and `cli` and `plugin` modules
- Move the old Java client Maven artifact into `client/cli/` and update module coordinates, wrapper usage, checkstyle
  paths, PMD paths, surefire environment variables, and jlink script paths
- Add `client/plugin/pom.xml` as the plugin source-assets module
- Add `client/distribution/pom.xml` as the flattened output module that aggregates the `cli` and `plugin` modules into
  runtime-specific plugin roots
- Add a no-`rsync` flattening pipeline that builds Claude and Codex plugin artifacts from
  `client/plugin/{rules,hooks,skills,agents}/{common,<runtime>}/`
- Ensure flattened Claude artifacts expose only Claude-relevant content plus common content, with runtime manifests,
  hooks, skills, agents, rules, concepts, templates, migrations, scripts, config, and the jlink client layout needed by
  runtime commands
- Ensure flattened Codex artifacts expose only Codex-relevant content plus common content, including Codex hook
  registration and custom agent TOML files
- Define release outputs so flattened plugin artifacts and jlink binaries can be committed together to isolated
  runtime-specific release commits or immutable tags
- Define local build/install behavior for Claude and Codex:
  - Primary: install flattened artifacts from `client/distribution/target/runtime/<runtime>/` after
    `mvn -f client/pom.xml package`
  - Fallback/debug: install directly from isolated local release commits for parity with public releases
- Existing source-tree installs are migrated by reinstalling from the flattened artifact for the active runtime.
  Source-tree installs remain a development fallback only when flattened artifacts are unavailable, and must not be
  used for release verification.
- Do not use raw source-tree paths or symlinked development installs as the normal install/update path
- Update `cat-install`, `cat-uninstall`, migration/session-start behavior, and agent installation to use the flattened
  artifact layout instead of source-tree paths
- Add runtime-specific `cat-install` skills for Claude and Codex that build `client/distribution/target/runtime/<runtime>/`
  and reinstall/update the active runtime from that flattened artifact
- Bundle the jlink client image inside the flattened runtime artifacts as `client/` with a matching `VERSION` file
- Add license headers to source files in the source tree, including agent-facing instruction sources where required by
  source policy, and make the flattened release processor strip those headers from agent-facing files so installed
  runtime context does not waste tokens on license boilerplate
- VETOED legal review concern: agent-facing files in flattened artifacts must not keep per-file license headers.
  Redistributed flattened artifacts satisfy license notice requirements by shipping a single root `LICENSE.md` file.
- Remove SessionStart's GitHub jlink download path; SessionStart may only verify or acquire the runtime from the
  bundled flattened plugin artifact
- Update docs and agent-facing instructions that still refer to repo-root `plugin/` source paths
- Verify that `mvn -f client/pom.xml verify -e` builds both modules, produces jlink output, and produces flattened
  runtime plugin artifacts
- Update the merge approval gate so Claude uses `AskUserQuestion`, Codex uses `request_user_input` when available,
  and Codex Default mode asks for exact verbal option selection when `request_user_input` is unavailable. Claude
  remains structured-only and fails closed if `AskUserQuestion` is unavailable.
- Squash implementation commits back to the approval-gate invariant before presenting the issue for review

## Post-conditions

- [x] Runtime-specific directories exist: `.claude/` and `.codex/`
- [x] Portable shared non-rule directory exists only when portable non-rule files are needed: `.agents/`
- [x] Project-local portable rules live under `.cat/rules/common/*`
- [x] Project-local Claude-specific rules are loaded from `.claude/rules/*` and `.cat/rules/claude/*`
- [x] Project-local Codex-specific rules are loaded from `.cat/rules/codex/*`
- [x] Plugin portable rules live under `client/plugin/rules/common/*`
- [x] Plugin Claude-specific rules are loaded from `client/plugin/rules/claude/*`
- [x] Plugin Codex-specific rules are loaded from `client/plugin/rules/codex/*`
- [x] Plugin portable hooks live under `client/plugin/hooks/common/*`
- [x] Plugin Claude-specific hooks live under `client/plugin/hooks/claude/*`
- [x] Plugin Codex-specific hooks live under `client/plugin/hooks/codex/*`
- [x] Plugin portable skills live under `client/plugin/skills/common/*`
- [x] Plugin Claude-specific skills live under `client/plugin/skills/claude/*`
- [x] Plugin Codex-specific skills live under `client/plugin/skills/codex/*`
- [x] Plugin skill sources stay split under `client/plugin/skills/{common,claude,codex}/*`
- [x] Developer-facing distribution design lives under `docs/development/`
- [x] Plugin portable agent bodies live under `client/plugin/agents/common/*`
- [x] Plugin Claude-specific agent wrappers live under `client/plugin/agents/claude/*`
- [x] Plugin Codex-specific agent definitions live under `client/plugin/agents/codex/*`
- [x] `client/` is a Maven parent project with `cli`, `plugin`, and `distribution` submodules
- [x] The old Java client artifact builds from `client/cli/`
- [x] The old plugin source tree lives in the `client/plugin/` source-assets module
- [x] Flattened output builds from the `client/distribution/` module
- [x] The Maven build produces flattened Claude and Codex plugin artifacts
- [x] The Maven build produces jlink binaries that can be bundled with flattened plugin artifacts
- [x] Local install/update flows use flattened artifacts rather than raw source paths
- [x] Source files carry required license headers, while flattened agent-facing release files omit those headers
- [x] Flattened release artifacts preserve licensing through the root `LICENSE.md` file rather than per-agent-file
  headers
- [x] Public release flows can publish isolated runtime-specific flattened plugin commits with matching jlink binaries
- [ ] Client code separates shared logic from Claude-specific and Codex-specific runtime adapters, including Java CLI
  entry points and utility scopes
- [ ] Shared Java CLI utilities bundled into Codex artifacts do not construct `MainClaudeTool` or require synthetic
  `CLAUDE_*` variables in Codex sessions
- [ ] Codex `cat:work` merge and approval-gate flows no longer emulate Claude by exporting temporary `CLAUDE_*`
  variables for shared CAT utilities
- [x] Runtime loaders resolve shared plus runtime-specific files for the active runtime
- [x] Claude Code support remains functional
- [x] Codex support is added without requiring removal of Claude files
- [x] Retrospective-as-archive wording is removed
- [x] Automated tests cover runtime-specific and shared file resolution
- [x] Automated tests cover same-named rule append order and no-deduplication behavior for Claude and Codex
- [x] Merge approval gates use runtime-specific approval: Claude structured-only, Codex structured when available
  or exact verbal option selection in Default mode
- [x] Full verification passes
