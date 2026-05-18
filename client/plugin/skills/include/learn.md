<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

Delegate learning work to the dedicated learn agent.

Use the engine's native agent-spawning tool:

- Codex: spawn `cat-learn-agent` with isolated context (`fork_turns: "none"` when available).
- Claude: spawn `cat:learn-agent`.

Pass through the mistake description, relevant transcript/context pointers, current branch, target branch, and any
requested background/foreground mode. The spawned agent must read and follow
`${CAT_PLUGIN_ROOT}/skills/learn/first-use.md`.

After the agent returns, verify and report the learning file path, prevention commit hash if present, and any reason
the learning could not be recorded. Do not run the full learn workflow directly in the main agent unless the engine
cannot spawn the dedicated agent.
