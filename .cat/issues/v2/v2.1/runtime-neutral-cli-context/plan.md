# Plan

## Goal

Remove the remaining Java CLI and shared utility coupling to Claude-only scopes so Codex runtime artifacts can run
shared CAT commands without synthetic `CLAUDE_*` environment variables.

Introduce a portable CLI scope or equivalent runtime-neutral command context for shared CAT utilities. The
runtime-neutral scope/context must be the base foundation for shared commands, and runtime-specific CLI contexts
(Claude/Codex) must extend or wrap that neutral base instead of the neutral code extending runtime-specific types.
Declare neutral base types in a neutral package/module: `CliTool` and `MainCliTool` must live under
`io.github.cowwoc.cat.tool` (or equivalent runtime-neutral module path), and `ClaudeTool` must extend the neutral
`CliTool` rather than the reverse.
Shared commands must require non-empty `CAT_PROJECT_DIR`, `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`, `CAT_SESSION_ID`, and
`CAT_RUNTIME` and fail fast with explicit errors when any are missing or blank. Shared commands must not fall back to
`CLAUDE_*` variables when `CAT_*` equivalents exist. Claude compatibility with `CLAUDE_PROJECT_DIR`,
`CLAUDE_PLUGIN_ROOT`, `CLAUDE_PLUGIN_DATA`, `CLAUDE_SESSION_ID`, and `CLAUDE_CONFIG_DIR` must be isolated to
Claude-specific adapters or Claude-only entrypoints.

## Parent Issue

`2.1-split-runtime-rules-directories` (decomposed)

## Sequence

Sub-issue 1 of 1. This issue captures the remaining unfinished runtime-neutral Java CLI context scope from the parent.

## Satisfies

- Parent post-condition: Client code separates shared logic from Claude-specific and Codex-specific runtime adapters,
  including Java CLI entry points and utility scopes.
- Parent post-condition: Shared Java CLI utilities bundled into Codex artifacts do not construct `MainClaudeTool` or
  require synthetic `CLAUDE_*` variables in Codex sessions.
- Parent post-condition: Codex `cat:work` merge and approval-gate flows no longer emulate Claude by exporting
  temporary `CLAUDE_*` variables for shared CAT utilities.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Scope construction is shared by many command-line utilities; incorrect required-variable validation or
  adapter boundaries could break shared workflows or Claude-only flows.
- **Mitigation:** Use test-driven changes around representative shared launchers, require explicit CAT validation in
  shared context constructors, verify Claude-only adapters separately, and run the full Maven verification suite before
  review.

## Files to Modify

- `client/cli/src/main/java/io/github/cowwoc/cat/tool/**` for neutral `CliTool`/`MainCliTool` foundations
- `client/cli/src/main/java/io/github/cowwoc/cat/claude/tool/**` for Claude-specific adapters that extend/wrap neutral
  tool types
- `client/cli/src/main/java/io/github/cowwoc/cat/claude/hook/util/**`
- `client/cli/src/test/**`
- `client/plugin/skills/**`, `client/plugin/agents/**`, and Codex/Claude runtime wrappers only where command examples or
  launch environments still export synthetic `CLAUDE_*` variables for shared utilities
- `client/distribution/**` if flattened runtime launchers need adapter-specific wiring

## Pre-conditions

- [ ] Parent issue `2.1-split-runtime-rules-directories` exists and remains in progress.
- [ ] Runtime-specific source directories and flattened runtime artifact layout from the parent issue already exist.
- [ ] Existing CAT-variable skill cleanup is preserved; do not reintroduce Claude-only variables into shared skill text.

## Jobs

### Job 1

- Inventory Java CLI entry points and launchers included in Codex flattened artifacts that still construct
  `MainClaudeTool`, accept only Claude-specific scope types, or require `CLAUDE_*` variables despite being shared CAT
  utilities.
- Classify each entry point as shared, Claude-only, or Codex-only. Shared utilities include session marker, merge,
  squash, rebase, issue-lock, status/config output, work-prepare, and other work-flow utilities present in both
  flattened runtime artifacts.
- Define the runtime-neutral context API and variable precedence:
  - Define inheritance/wrapping direction: runtime-neutral CLI context/scope is the superclass/foundation; runtime-
    specific CLI adapters (Claude/Codex) extend or wrap it.
  - Place neutral interfaces/classes in neutral package/module locations (for Java: `io.github.cowwoc.cat.tool`), not
    under runtime-specific namespaces such as `io.github.cowwoc.cat.claude.*`.
  - Define neutral base types explicitly: `CliTool` and `MainCliTool` are neutral/shared types.
  - Define runtime-specific extension explicitly: `ClaudeTool` extends neutral `CliTool`.
  - Shared runtime-neutral code must not extend, subclass, or require Claude-specific CLI scope/tool classes.
  - No neutral type may implement or extend a runtime-specific interface/class.
  - Shared utilities require non-empty `CAT_PROJECT_DIR`, `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`, `CAT_SESSION_ID`, and
    `CAT_RUNTIME`.
  - Shared utilities fail fast with clear errors naming missing/blank `CAT_*` variables.
  - Shared utilities do not read `CLAUDE_*` variables for fields that have `CAT_*` equivalents.
  - Claude adapters and Claude-only entrypoints may read `CLAUDE_PROJECT_DIR`, `CLAUDE_PLUGIN_ROOT`,
    `CLAUDE_PLUGIN_DATA`, `CLAUDE_SESSION_ID`, and `CLAUDE_CONFIG_DIR`.

### Job 2

- Add the portable CLI scope or runtime-neutral command context as the base abstraction, then implement runtime-
  specific adapter wiring by extending or wrapping that neutral base.
- Move or declare neutral `CliTool`/`MainCliTool` in `io.github.cowwoc.cat.tool` (or equivalent neutral module
  location) and update imports/usages accordingly.
- Refactor `ClaudeTool` so it extends neutral `CliTool`; remove any reverse dependency where neutral types inherit from
  Claude-specific classes.
- Refactor shared Java CLI entry points so Codex launchers do not instantiate `MainClaudeTool` or require
  `CLAUDE_SESSION_ID`, `CLAUDE_PROJECT_DIR`, `CLAUDE_PLUGIN_ROOT`, `CLAUDE_PLUGIN_DATA`, or `CLAUDE_CONFIG_DIR`.
- Remove or invert any inheritance chain where runtime-neutral/shared CLI classes extend Claude-specific types; shared
  classes must depend only on neutral abstractions.
- Ensure no runtime-neutral type implements runtime-specific interfaces or abstract classes.
- Implement shared-context validation that rejects missing or blank required `CAT_*` variables before command
  execution.
- Remove shared-context code paths that map `CAT_*` requirements to `CLAUDE_*` fallback values.
- Keep `MainClaudeTool` and Claude hook/tool types available for genuinely Claude-specific hooks, statusline, and
  runner behavior.
- Update shared workflow launchers and runtime command examples so Codex paths use `CAT_*` variables directly rather
  than exporting temporary `CLAUDE_*` compatibility variables.

### Job 3

- Add regression tests proving representative shared CLI utilities run in a Codex environment with only `CAT_*`
  variables set.
- Add regression tests proving shared CLIs fail fast with clear errors when required `CAT_*` variables are missing or
  blank.
- Add regression tests proving shared CLIs do not consume `CLAUDE_*` fallback values for fields with `CAT_*`
  equivalents.
- Add regression tests proving Claude-specific CLIs still accept `CLAUDE_*` through the Claude adapter.
- Do not add tests that enforce design-time conventions such as which packages or modules neutral types are declared
  in; verify those boundaries through implementation review and type dependencies instead.
- Add checks that Codex flattened artifacts do not include shared launchers that require `MainClaudeTool` construction.
- Run `mvn -f client/pom.xml verify -e`.

## Post-conditions

- [ ] Shared Java CLI utilities bundled into Codex artifacts do not construct `MainClaudeTool`.
- [ ] Runtime-neutral CLI context/scope is the superclass/foundation for shared utilities, and runtime-specific CLI
  adapters (Claude/Codex) extend or wrap the neutral base rather than the neutral code extending Claude-specific
  classes.
- [ ] Neutral tool foundations (`CliTool`, `MainCliTool`) are declared in neutral package/module locations
  (`io.github.cowwoc.cat.tool`), not in Claude- or Codex-specific namespaces.
- [ ] `ClaudeTool` extends the neutral `CliTool`, and no neutral type implements/extends runtime-specific interfaces or
  classes.
- [ ] Shared Java CLI utilities run under Codex with `CAT_PROJECT_DIR`, `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`,
  `CAT_SESSION_ID`, and `CAT_RUNTIME`, without synthetic `CLAUDE_*` exports.
- [ ] Shared Java CLI utilities fail fast when required `CAT_*` variables are missing or blank.
- [ ] Shared Java CLI utilities do not read `CLAUDE_*` fallback values for fields with `CAT_*` equivalents.
- [ ] Claude-specific CLIs still accept Claude-specific environment through the Claude adapter or compatibility layer.
- [ ] `MainClaudeTool` remains limited to genuinely Claude-specific hooks, statusline, runner behavior, or adapters.
- [ ] Codex `cat:work` merge and approval-gate flows no longer emulate Claude by exporting temporary `CLAUDE_*`
  variables for shared CAT utilities.
- [ ] `mvn -f client/pom.xml verify -e` passes.
