---
category: requirement
---
## Turn 1

Run git-squash with delegation required. First delegated response is generic with no artifacts, second response is
out-of-scope for requested squash targets.

## Assertions

1. the agent rejects invalid delegated output and classifies `delegation_failure=invalid_analysis`
2. the agent performs required retry/replacement flow instead of continuing squash actions immediately
3. if repeated delegated output remains invalid, the agent records blocker evidence and uses blocker fallback
4. the agent does not continue with squash completion reporting until delegation acceptance checks pass or fallback is used
