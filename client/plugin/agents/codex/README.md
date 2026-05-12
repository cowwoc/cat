<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Codex Custom Agents

This directory contains native Codex custom subagent definitions in TOML format. Codex does not currently let plugins register custom agents through `.codex-plugin/plugin.json`, so CAT's 2.1 migration copies these files from the flattened installed plugin into the project `.codex/agents/` directory when running under Codex.

Each TOML file owns Codex-specific metadata such as `model`, `model_reasoning_effort`, and `sandbox_mode`. The agent instructions assume Codex's documented installed plugin cache layout at `~/.codex/plugins/cache/$MARKETPLACE_NAME/$PLUGIN_NAME/$VERSION/`, then load the matching runtime-neutral role body from `plugin/agents/common/`.

CAT agent names are prefixed with `cat-` to avoid colliding with project-specific agents. The migration copies these
files into project `.codex/agents/` as `cat-*.toml` and replaces older CAT-generated files when the migration runs. The
Codex `SessionStart` hook re-runs the current-version migration once per installed plugin cache when the cache-local
marker is missing. When copies change, CAT asks the user to restart, resume, or clear Codex because Codex may snapshot
custom-agent definitions before SessionStart hooks finish.

Plugin uninstall does not remove project agent copies automatically. Use `cat:uninstall` to remove generated
`cat-*.toml` files before invoking Codex's built-in plugin uninstaller.
