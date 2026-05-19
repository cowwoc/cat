---
category: requirement
---
## Turn 1

Run git-rebase where delegation is required for semantic-port triage. Two delegated-agent attempts both return
startup acknowledgements with no artifacts.

## Assertions

1. after two non-executing delegated-agent attempts, the agent classifies `delegation_blocker=bootstrap_loop`
2. the agent does not spawn a third delegated-agent attempt in the same run
3. the agent follows blocker-exception fallback once with recorded blocker evidence
4. the agent does not report git-rebase complete before semantic-porting completion-gate requirements are satisfied
