<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

Delegate plan building to a tiered dedicated plan-builder agent. The selected plan-builder owns scope classification,
decomposition, contradiction handling, sequencing, acceptance-criteria quality, and final plan synthesis. Helper agents
may gather evidence or review drafts, but they must not own the final plan.

Auto-tier `plan-builder` for building or revising a mechanical issue execution plan from the original skill arguments,
current working directory, issue path, target branch, and any known revision context.

Route `plan-builder` via `client/plugin/skills/include/agent-tier/plan-builder.md`, using the original skill
arguments, current working directory, issue path, target branch, and any known revision context.
Set `AGENT_TIER` and `AGENT_ALIAS` from that file.

Spawn `AGENT_ALIAS` with isolated context when the surrounding agent-spawning mechanism supports it.

Pass through the original skill arguments and include the current working directory, issue path, target branch, and any
revision context already known to the main agent. The spawned agent must read and follow
`${CAT_PLUGIN_ROOT}/skills/plan-builder/first-use.md`.

If the caller has exact ambiguity JSON, blocker evidence, failed verification details, or per-concern fix evidence,
pass that material through verbatim in the spawned prompt or by explicit file path reference. Do not summarize,
rewrite, or omit blocker evidence when invoking the selected plan-builder agent.

After the agent returns, report the plan path, whether it was created or revised, and any blockers. Do not build or
revise the plan directly in the main agent unless the engine cannot spawn the dedicated agent.
