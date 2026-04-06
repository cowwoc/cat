---
category: requirement
---
## Turn 1

Here is the retrospective analysis:

╭─── RETROSPECTIVE ANALYSIS ─── 3 patterns identified ───╮
│                                                          │
│ Pattern 1: Approval Gate Bypass                          │
│   Occurrences: 3 | Category: protocol_violation         │
│                                                          │
│ Pattern 2: Workflow Step Skipping                        │
│   Occurrences: 2 | Category: protocol_violation         │
│                                                          │
│ Pattern 3: Post-Handler Non-Execution                    │
│   Occurrences: 1 | Category: protocol_violation         │
│                                                          │
╰──────────────────────────────────────────────────────────╯

What are the next steps?

## Assertions

1. agent takes action based on the analysis output (proceeds to post-handler workflow)
2. agent does not re-invoke retrospective analysis or use tools to re-fetch results
