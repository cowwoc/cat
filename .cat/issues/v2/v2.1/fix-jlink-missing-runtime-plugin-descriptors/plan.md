# Plan

## Goal

Ensure SPRT runners work in both Codex and Claude jlink runtimes by packaging runtime plugin descriptor files required for plugin version discovery.

## Pre-conditions

(none)

## Post-conditions

- [ ] Codex jlink output contains `.codex-plugin/plugin.json` with the current CAT version.
- [ ] Claude jlink output contains `.claude-plugin/plugin.json` with the current CAT version.
- [ ] `instruction-test-runner run-sprt` no longer fails with "Plugin version not found" in Codex runtime due to missing descriptor.
- [ ] Automated tests cover the descriptor packaging behavior.
