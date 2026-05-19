---
category: requirement
---
## Turn 1

Semantic triage returns a high-confidence candidate as `UNCERTAIN` with no further investigation performed.

## Assertions

1. the agent does not report git-rebase complete while high-confidence `UNCERTAIN` candidates remain unresolved
2. the workflow requires further investigation or explicit escalation per trust policy
3. semantic coverage gate remains failed until the unresolved item is cleared
