<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

Delegate issue decomposition to the dedicated decompose-issue agent.

Use the runtime's native agent-spawning tool:

- Codex: spawn `cat-decompose-issue-agent` with isolated context (`fork_turns: "none"` when available).
- Claude: spawn `cat:decompose-issue-agent`.

Pass through the original skill arguments, the issue path, target branch, and the reason decomposition is needed. The
spawned agent must read and follow `${CAT_PLUGIN_ROOT}/skills/decompose-issue/first-use.md`.

After the agent returns, report the parent issue, created sub-issues, dependency updates, and any blockers. Do not
decompose the issue directly in the main agent unless the runtime cannot spawn the dedicated agent.
