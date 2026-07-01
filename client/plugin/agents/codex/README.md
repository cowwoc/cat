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

Model selection should stay cost-conscious. Tiered CAT agents use `low`, `medium`, and `high` wrappers rather than
untiered compatibility aliases. Plan-builder tiers route from `cat-plan-builder-low` (`gpt-5.4-mini`, medium effort) to
`cat-plan-builder-medium` (`gpt-5.4`, high effort) to `cat-plan-builder-high` (`gpt-5.5`, xhigh effort). Reviewer
tiers route from `gpt-5.4-mini`/medium to `gpt-5.4`/medium to `gpt-5.5`/high. The `cat-work-execute` implementer is
intentionally cheap (`gpt-5.4-mini`, medium effort) and must return `BLOCKED_PLAN_NOT_MECHANICAL` instead of making
planning decisions. Tier selection is owned by the workflow routing layer; agents do not self-escalate on tier
mismatch. Use `gpt-5.5` only for high-tier planning/review, adversarial hardening, instruction synthesis, or
other gates where failures invalidate downstream work.

When CAT must choose the weakest test-runner configuration across multiple matching Codex agent owners, compare
complete owner model/effort pairs. Model rank dominates effort rank. The model rank from weakest to strongest is
`gpt-5.4-mini`, `gpt-5.4`, `gpt-5.5`; effort rank is `low`, `medium`, `high`, `xhigh`. Unknown models or efforts must
be ranked explicitly before they are used by CAT-owned agents.

CAT agent names are prefixed with `cat-` to avoid colliding with project-specific agents. The migration copies these
files into project `.codex/agents/` as `cat-*.toml` and replaces older CAT-generated files when the migration runs. The
Codex `SessionStart` hook re-runs the current-version migration once per installed plugin cache when the cache-local
marker is missing. Codex 0.134.0 also exposes `agent_type` to subagent-scoped hooks; CAT uses the Codex
`SubagentStart` hook for agent-targeted rule injection, but still relies on this migration because plugin installation
does not register custom agent TOML files. When copies change, CAT asks the user to restart, resume, or clear Codex
because Codex may snapshot custom-agent definitions before SessionStart hooks finish.

Plugin uninstall does not remove project agent copies automatically. Use `$cat:uninstall` to remove generated
`cat-*.toml` files before removing CAT with `codex plugin remove cat@cat` or the plugin browser.
