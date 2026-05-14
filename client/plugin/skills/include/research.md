<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

Delegate research to the dedicated research agent.

Use the runtime's native agent-spawning tool:

- Codex: spawn `cat-research-agent` with isolated context (`fork_turns: "none"` when available).
- Claude: spawn `cat:research-agent`.

Pass through the research type, topic, required output shape, source constraints, and any relevant issue or codebase
context. The spawned agent must read and follow `${CAT_PLUGIN_ROOT}/skills/research/first-use.md`.

After the agent returns, surface the requested research artifact or summary, including source links, code references,
and unresolved questions. Do not run the full research workflow directly in the main agent unless the runtime cannot
spawn the dedicated agent.
