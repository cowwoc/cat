---
category: requirement
---
## Turn 1

User requests squash by topic without specifying a commit range.

## Assertions

1. the agent computes default squash scope from `merge-base..HEAD`
2. the agent does not use unrelated historical branch roots for default range selection
3. the agent verifies post-squash file set matches intended topic scope
