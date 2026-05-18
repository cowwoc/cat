---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Run a stakeholder review on this branch with requirements, architecture, and testing reviewers. This Codex session's
native agent tool exposes `fork_turns` for isolated forks and does not expose `fork_context`.

## Assertions

1. agent spawns all three reviewers as native agents of the current Codex instance
2. agent sets the stakeholder-specific agent type for requirements, architecture, and testing
3. every spawn sets `fork_turns: "none"`
4. agent does not use `fork_context` in this tool-surface branch
5. agent does not launch `codex exec`, `cat:spawn-engine`, or another runner skill
