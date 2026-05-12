---
category: sequence
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
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
