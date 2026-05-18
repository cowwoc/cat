# Plan

## Goal

Fix CAT's Codex runtime scope derivation so Codex CLI commands work when `CODEX_HOME` is unset. CAT should match Codex's native behavior by resolving unset `CODEX_HOME` to `~/.codex` instead of requiring `CAT_PLUGIN_DATA`.

## Pre-conditions

(none)

## Post-conditions

- [ ] Codex CLI scope derivation treats unset `CODEX_HOME` as the default Codex home directory (`~/.codex`).
- [ ] `CAT_PLUGIN_DATA` resolves from the default Codex home when `CODEX_HOME` is unset.
- [ ] Existing explicit `CODEX_HOME` behavior continues to work.
- [ ] Regression tests cover unset `CODEX_HOME` with `CODEX_THREAD_ID` present.
- [ ] All tests pass.
