<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Codex Custom Agents

This directory contains native Codex custom agent definitions in TOML format. Codex does not currently let plugins
register custom agents through `.codex-plugin/plugin.json`, so CAT's 2.1 migration copies these files from the
flattened installed plugin into the project `.codex/agents/` directory when running under Codex.

Each TOML file owns Codex-specific metadata such as `model`, `model_reasoning_effort`, and `sandbox_mode`. The agent instructions assume Codex's documented installed plugin cache layout at `~/.codex/plugins/cache/$MARKETPLACE_NAME/$PLUGIN_NAME/$VERSION/`, then load the matching engine-neutral role body from `plugin/agents/common/`.

Model selection should stay cost-conscious. Use `gpt-5.5` only for adversarial hardening and instruction synthesis
roles where failures invalidate downstream work; use `gpt-5.4` for planning/decomposition roles that need stronger
general reasoning without frontier cost; use `gpt-5.3-codex` for code-writing, code-heavy review, and verification
roles; use `gpt-5.4-mini` for mechanical git/file operations and non-code stakeholder review. Prefer `low` effort
only for deterministic wrappers, `medium` for checklist or semantic review, and `high` only where the agent gates
correctness or must synthesize complex instructions.

CAT agent names are prefixed with `cat-` to avoid colliding with project-specific agents. The migration copies these
files into project `.codex/agents/` as `cat-*.toml` and replaces older CAT-generated files when the migration runs. The
Codex `SessionStart` hook re-runs the current-version migration once per installed plugin cache when the cache-local
marker is missing. Codex 0.134.0 also exposes `agent_type` to subagent-scoped hooks; CAT uses the Codex
`SubagentStart` hook for agent-targeted rule injection, but still relies on this migration because plugin installation
does not register custom agent TOML files. When copies change, CAT asks the user to restart, resume, or clear Codex
because Codex may snapshot custom-agent definitions before SessionStart hooks finish.

Plugin uninstall does not remove project agent copies automatically. Use `cat:uninstall` to remove generated
`cat-*.toml` files before invoking Codex's built-in plugin uninstaller.
