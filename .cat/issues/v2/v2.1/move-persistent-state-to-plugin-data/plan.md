# Plan

## Goal

Move CAT-owned mutable state that must persist across sessions out of project/work paths and into the runtime-neutral
`${CAT_PLUGIN_DATA}` directory, while keeping bundled executables and read-only plugin files under `${CAT_PLUGIN_ROOT}`.

## Context

CAT now treats plugin-root runtime content as shipped, read-only plugin content. Persistent state should not be mixed
with bundled runtime files or project source files. `CAT_PLUGIN_DATA` is the runtime-neutral writable plugin data root:

- Claude derives it from Claude Code's native `CLAUDE_PLUGIN_DATA`.
- Codex does not expose a direct native equivalent. CAT defines `CAT_PLUGIN_DATA` for Codex and currently defaults it
  to `${CAT_PLUGIN_ROOT}/data`.
- The Codex default intentionally keeps CAT's writable plugin data adjacent to the installed CAT plugin root while
  preserving the executable/data split: shipped launchers remain under `${CAT_PLUGIN_ROOT}/client/bin`, and mutable
  state lives under `${CAT_PLUGIN_ROOT}/data`.

## Scope

Examples of state that should live under `${CAT_PLUGIN_DATA}` when it is not intentionally project-local:

- CAT config and cached effective configuration.
- Session markers, resume metadata, and session analysis artifacts.
- Issue/workflow locks and operation lock files.
- CAT caches and expensive generated outputs.
- Verification/review outputs and generated reports that must survive between sessions.
- Logs, diagnostics, migration markers, and other CAT-owned runtime state.

State that should not move:

- Bundled executables and runtime launchers, which belong under `${CAT_PLUGIN_ROOT}/client/bin`.
- Project-owned source, issue records, plans, changelogs, and `.cat/issues` content.
- Worktree-specific files that are intentionally part of the project repository or issue artifact history.

## Pre-conditions

- Plugin-root runtime path work is complete or the implementation accounts for it.

## Post-conditions

- [ ] Mutable CAT-owned persistent state has a clear storage policy: `${CAT_PLUGIN_DATA}` for plugin data,
      `${CAT_PLUGIN_ROOT}` for shipped read-only plugin content, and project paths only for project-owned artifacts.
- [ ] Claude and Codex environment guidance documents the same `CAT_PLUGIN_DATA` semantics, including Codex's
      `${CAT_PLUGIN_ROOT}/data` default.
- [ ] Codex setup/update paths create `${CAT_PLUGIN_ROOT}/data` when needed and stop defaulting `CAT_PLUGIN_DATA` to
      `${CODEX_HOME}/plugins/data/cat-cat`.
- [ ] Skills, hooks, agents, and Java utilities that currently write persistent CAT runtime state to ad hoc
      work/project paths are audited and migrated to `${CAT_PLUGIN_DATA}` where appropriate.
- [ ] Paths that must remain project-local or worktree-local are explicitly documented so they are not moved by
      accident.
- [ ] Regression tests cover at least one Claude and one Codex path that read or write persistent state through
      `${CAT_PLUGIN_DATA}`.
- [ ] `mvn -f client/pom.xml verify -e` passes.
