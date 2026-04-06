---
category: sequence
---
## Turn 1

Here is the retrospective analysis output:

╭─── RETROSPECTIVE ANALYSIS ─── 3 patterns identified ───╮
│ Category: protocol_violation (3 unresolved mistakes)     │
│                                                          │
│ Pattern 1: Incomplete workflow execution (3 occurrences) │
│ Pattern 2: Context assertion bypass (2 occurrences)      │
│ Pattern 3: Missing post-handler execution (1 occurrence) │
│                                                          │
╰──────────────────────────────────────────────────────────╯

What should happen next?

## Assertions

1. agent executes the post-handler workflow steps in response to the analysis
2. agent does not re-run the retrospective analysis or use additional tools to re-fetch the data
