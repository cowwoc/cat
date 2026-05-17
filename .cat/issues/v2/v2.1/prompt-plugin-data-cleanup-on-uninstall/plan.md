# Plan

## Goal

Update `cat:uninstall` so it asks whether to delete the CAT plugin data directory as part of uninstalling CAT from Codex.

## Pre-conditions

(none)

## Post-conditions

- [ ] `cat:uninstall` identifies the resolved plugin data directory, including `CAT_PLUGIN_DATA` when set and the Codex default when it must be derived.
- [ ] `cat:uninstall` asks the user whether to delete the plugin data directory before attempting removal.
- [ ] The safe/default response preserves the plugin data directory and reports that it was kept.
- [ ] Explicit confirmation deletes the plugin data directory and reports the deleted path.
- [ ] Missing plugin data directories are handled gracefully without failing the uninstall.
- [ ] Existing uninstall behavior remains intact: project `.codex/agents/cat-*.toml` files are removed and Codex's plugin uninstaller is invoked when available.
- [ ] Tests or deterministic verification cover both keep-data and delete-data paths.
