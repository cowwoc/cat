---
name: cat-update
description: >-
  Rebuild and reinstall CAT for local Codex development from the current source checkout. Use when the user asks to
  update local CAT, rebuild the CAT plugin, refresh the Codex plugin cache, or install current source changes into the
  local Codex marketplace.
---

# Update CAT

## Purpose

Rebuild CAT from the current source checkout and install the generated Codex runtime artifact into the local Codex
marketplace, plugin cache, and plugin data runtime directory.

## Procedure

Run the workflow from the repository root.

```bash
bash ./client/plugin/scripts/codex-dev-update.sh
```

The installer resolves the build source in this order:

1. The current git worktree, when run inside a CAT issue worktree.
2. The issue worktree locked by the current session, when run from `/workspace`.
3. The main workspace checkout.

After the command succeeds, tell the user to restart Codex to complete the installation.

## Verification

- The Maven build exits with code 0.
- The Bats test suite exits with code 0.
- `${CODEX_HOME}/plugins/cat-marketplace/plugins/cat/.codex-plugin/plugin.json` exists.
- `${CODEX_HOME}/plugins/cache/cat/cat/{version}/skills/add/SKILL.md` exists.
- `${CAT_PLUGIN_DATA}/client/bin/java -version` runs successfully.
- The selected source checkout's `.codex/agents/cat-stakeholder-architecture-*.toml` exists and declares a `cat-stakeholder-architecture-*` agent.
- `${CODEX_HOME}/config.toml` enables `[plugins."cat@cat"]`.
