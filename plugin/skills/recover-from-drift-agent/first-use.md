<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Recover from Drift (Agent Variant)

Agent-only skill for detecting and recovering from goal drift. Compares recent actions against plan.md to identify
when execution has diverged from the intended plan, then provides corrective guidance to realign with the original
goal.

This variant runs as a lightweight Haiku subagent and is invoked by other skills — it is not user-invocable.
Use `cat:recover-from-drift-agent` when actions keep failing or progress has stalled without explanation.

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" recover-from-drift-agent`
