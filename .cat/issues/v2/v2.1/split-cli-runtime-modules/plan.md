# Plan: split-cli-runtime-modules

## Goal

Split the current CLI Maven module into runtime-specific and shared modules:

- `client/common-cli` for runtime-neutral command, hook, config, release-artifact, and utility code
- `client/claude-cli` for Claude Code entrypoints, payload parsing, launchers, and Claude-specific adapters
- `client/codex-cli` for Codex entrypoints, payload parsing, launchers, and Codex-specific adapters

The Codex implementation must not invoke Claude implementation classes, and the Claude implementation must not invoke
Codex implementation classes. Both runtimes may depend on shared code from `common-cli`.

## Parent Requirements

- `split-runtime-rules-directories` establishes runtime-specific plugin source and artifact layout.
- `add-codex-missing-hooks` establishes native Codex hook registration and payload handling requirements.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Maven reactor and jlink launcher regressions; duplicate logic while separating runtime entrypoints;
  accidental cross-runtime dependencies; release artifacts missing runtime-specific launchers
- **Mitigation:** Move shared behavior first, keep runtime adapters thin, enforce boundaries through Maven/module
  dependencies, and verify both runtime artifacts by executing representative launchers with native payloads

## Files to Modify

- `client/pom.xml` — add the new Maven modules and adjust reactor ordering
- `client/cli/**` — migrate existing shared and runtime-specific code out of the current monolithic module
- `client/common-cli/**` — new shared CLI module
- `client/claude-cli/**` — new Claude-specific CLI module
- `client/codex-cli/**` — new Codex-specific CLI module
- `client/distribution/**` — package the runtime-specific CLI images and launchers into flattened artifacts
- `client/plugin/hooks/{claude,codex}/**` — update hook command paths only if launcher names or locations change
- `client/**/src/test/**` — move and update behavior tests for the new module boundaries
- Documentation that describes the client module layout or runtime artifact build

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Inventory and Module Design

- Classify existing `client/cli` Java packages, scripts, launchers, and tests as shared, Claude-specific, or
  Codex-specific
- Define Maven coordinates and Java module names for `common-cli`, `claude-cli`, and `codex-cli`
- Define the expected dependency graph:
  - `claude-cli` depends on `common-cli`
  - `codex-cli` depends on `common-cli`
  - `common-cli` depends on neither runtime-specific module
  - `claude-cli` and `codex-cli` do not depend on each other
- Decide whether the old `client/cli` module is removed or retained only as a parent/compatibility aggregate

### Job 2: Extract Shared CLI Code

- Move runtime-neutral command orchestration, file/path helpers, JSON helpers, diagnostics, configuration, release
  artifact utilities, and common hook plumbing into `client/common-cli`
- Rename shared packages/classes so they do not contain Claude- or Codex-specific terminology unless they model an
  explicit runtime enum/value
- Keep shared code free of runtime-specific payload schemas, hook names, launcher names, and product APIs
- Move shared tests with the shared code and keep them focused on meaningful inputs and outputs

### Job 3: Create Claude CLI Module

- Move Claude Code entrypoints, Claude hook payload parsing, Claude launcher definitions, and Claude adapter classes
  into `client/claude-cli`
- Ensure Claude code calls shared services through runtime-neutral interfaces or value objects
- Remove any dependency from Claude code to Codex packages/classes
- Preserve existing Claude launcher behavior and release artifact contents

### Job 4: Create Codex CLI Module

- Move Codex entrypoints, native Codex hook payload parsing, Codex launcher definitions, and Codex adapter classes into
  `client/codex-cli`
- Ensure Codex code calls shared services through runtime-neutral interfaces or value objects
- Remove any dependency from Codex code to Claude packages/classes
- Preserve Codex hook behavior with native Codex payloads and release artifact launcher paths

### Job 5: Update Build and Distribution

- Update the Maven reactor, module descriptors, checkstyle/PMD/surefire configuration, and jlink packaging for the
  three CLI modules
- Generate runtime-specific jlink images or launchers from the runtime-specific module that owns each runtime
- Ensure flattened Claude artifacts include the Claude CLI runtime and Claude launchers
- Ensure flattened Codex artifacts include the Codex CLI runtime and Codex launchers
- Keep release artifact paths stable where possible, especially `${CAT_PLUGIN_ROOT}/client/bin/<launcher>`

### Job 6: Runtime Verification

- Add or update automated tests that execute representative Claude and Codex commands/hooks with realistic native
  payloads and assert observable behavior
- Do not add tests whose only purpose is to enforce package-time structure, scan imports, or assert internal release
  artifact layout without exercising runtime behavior
- Verify module boundaries through the Maven/module dependency graph instead of source-scanning tests
- Run `mvn -f client/pom.xml verify -e`

## Post-conditions

- [ ] `client/common-cli`, `client/claude-cli`, and `client/codex-cli` exist as Maven modules
- [ ] Shared CLI behavior lives in `common-cli`
- [ ] Claude-specific entrypoints, payload parsing, and launchers live in `claude-cli`
- [ ] Codex-specific entrypoints, payload parsing, and launchers live in `codex-cli`
- [ ] Codex code does not invoke Claude implementation classes
- [ ] Claude code does not invoke Codex implementation classes
- [ ] Claude and Codex runtime artifacts expose stable `${CAT_PLUGIN_ROOT}/client/bin/<launcher>` paths
- [ ] Runtime behavior tests cover representative Claude and Codex hook/command payloads
- [ ] Tests passing: `mvn -f client/pom.xml verify -e` exits 0
