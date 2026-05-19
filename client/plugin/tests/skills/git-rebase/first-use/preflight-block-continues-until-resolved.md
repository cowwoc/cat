---
category: requirement
---
## Turn 1

Run git-rebase and receive a preflight `decision: block` for path-consistency mismatches.

## Assertions

1. the agent treats preflight `decision: block` as in-progress and does not report completion
2. the agent investigates `src -> dst` mappings and applies path-alignment changes before rerunning git-rebase
3. the agent repeats the flow until preflight passes or a concrete ambiguity/risk escalation is required
