# Plan

## Goal

Remove the remaining Java CLI and shared utility coupling to Claude-only scopes so Codex runtime artifacts can run
shared CAT commands without synthetic `CLAUDE_*` environment variables.

Introduce a portable CLI scope or equivalent runtime-neutral command context for shared CAT utilities. Shared commands
must read `CAT_PROJECT_DIR`, `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`, `CAT_SESSION_ID`, and `CAT_RUNTIME` first. Claude
compatibility with `CLAUDE_PROJECT_DIR`, `CLAUDE_PLUGIN_ROOT`, `CLAUDE_PLUGIN_DATA`, `CLAUDE_SESSION_ID`, and
`CLAUDE_CONFIG_DIR` must be isolated to Claude-specific adapters or compatibility layers.

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
- **Concerns:** Scope construction is shared by many command-line utilities; incorrect precedence between `CAT_*` and
  `CLAUDE_*` variables could break Claude compatibility or keep Codex dependent on Claude-only state.
- **Mitigation:** Use test-driven changes around representative shared launchers, verify both runtime adapters, and
  run the full Maven verification suite before review.

## Files to Modify

- `client/cli/src/main/java/io/github/cowwoc/cat/claude/tool/**` and replacement portable scope packages
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
  - Shared utilities prefer `CAT_PROJECT_DIR`, `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`, `CAT_SESSION_ID`, and
    `CAT_RUNTIME`.
  - Claude adapters may fall back to `CLAUDE_PROJECT_DIR`, `CLAUDE_PLUGIN_ROOT`, `CLAUDE_PLUGIN_DATA`,
    `CLAUDE_SESSION_ID`, and `CLAUDE_CONFIG_DIR`.
  - Shared utilities prefer `CAT_*` when both `CAT_*` and `CLAUDE_*` are present.

### Job 2

- Add the portable CLI scope or runtime-neutral command context and runtime-specific adapter wiring.
- Refactor shared Java CLI entry points so Codex launchers do not instantiate `MainClaudeTool` or require
  `CLAUDE_SESSION_ID`, `CLAUDE_PROJECT_DIR`, `CLAUDE_PLUGIN_ROOT`, `CLAUDE_PLUGIN_DATA`, or `CLAUDE_CONFIG_DIR`.
- Keep `MainClaudeTool` and Claude hook/tool types available for genuinely Claude-specific hooks, statusline, and
  runner behavior.
- Update shared workflow launchers and runtime command examples so Codex paths use `CAT_*` variables directly rather
  than exporting temporary `CLAUDE_*` compatibility variables.

### Job 3

- Add regression tests proving representative shared CLI utilities run in a Codex environment with only `CAT_*`
  variables set.
- Add regression tests proving shared CLIs prefer `CAT_*` values when both `CAT_*` and `CLAUDE_*` are present.
- Add regression tests proving Claude-specific CLIs still accept `CLAUDE_*` through the Claude adapter.
- Add checks that Codex flattened artifacts do not include shared launchers that require `MainClaudeTool` construction.
- Run `mvn -f client/pom.xml verify -e`.

## Post-conditions

- [ ] Shared Java CLI utilities bundled into Codex artifacts do not construct `MainClaudeTool`.
- [ ] Shared Java CLI utilities run under Codex with `CAT_PROJECT_DIR`, `CAT_PLUGIN_ROOT`, `CAT_PLUGIN_DATA`,
  `CAT_SESSION_ID`, and `CAT_RUNTIME`, without synthetic `CLAUDE_*` exports.
- [ ] Shared Java CLI utilities prefer `CAT_*` values when both `CAT_*` and `CLAUDE_*` values exist.
- [ ] Claude-specific CLIs still accept Claude-specific environment through the Claude adapter or compatibility layer.
- [ ] `MainClaudeTool` remains limited to genuinely Claude-specific hooks, statusline, runner behavior, or adapters.
- [ ] Codex `cat:work` merge and approval-gate flows no longer emulate Claude by exporting temporary `CLAUDE_*`
  variables for shared CAT utilities.
- [ ] `mvn -f client/pom.xml verify -e` passes.
