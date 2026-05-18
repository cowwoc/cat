<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

Delegate plan building to the dedicated plan-builder agent.

Use the engine's native agent-spawning tool:

- Codex: spawn `cat-plan-builder-agent` with isolated context (`fork_turns: "none"` when available).
- Claude: spawn `cat:plan-builder-agent`.

Pass through the original skill arguments and include the current working directory, issue path, target branch, and any
revision context already known to the main agent. The spawned agent must read and follow
`${CAT_PLUGIN_ROOT}/skills/plan-builder/first-use.md`.

After the agent returns, report the plan path, whether it was created or revised, and any blockers. Do not build or
revise the plan directly in the main agent unless the engine cannot spawn the dedicated agent.
