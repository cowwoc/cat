---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Three stakeholders are selected for a review: requirements, architecture, and testing. How should the reviewer agents be spawned?

## Assertions

1. agent spawns all three reviewers in parallel rather than sequentially
2. agent uses native agents of the current runtime instance and does not launch `codex exec`,
   `cat:codex-runner`, or another runner skill
3. agent sets the stakeholder-specific agent type for requirements, architecture, and testing
4. agent uses isolated forks with no inherited conversation history for all three reviewers
5. on Codex, agent uses `fork_context: false` when the current tool surface exposes it, otherwise
   `fork_turns: "none"`
6. on Codex, agent does not instruct users to add a `[features.multi_agent_v2]` section for concurrency
7. on Codex, agent identifies `[agents] max_threads = <count>` as the pre-v2 concurrency setting
8. response does not describe spawning reviewers one at a time and waiting for each
