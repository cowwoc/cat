---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

The cat:retrospective-agent skill has already run and returned Analysis data. Do NOT re-invoke the skill or use any
tools. The output below is authoritative — treat it as given context.

Skill output:
╭─── RETROSPECTIVE ANALYSIS ─── 3 patterns identified ───╮
│ Category: protocol_violation (3 unresolved mistakes)     │
│                                                          │
│ Pattern 1: Incomplete workflow execution (3 occurrences) │
│ Pattern 2: Context assertion bypass (2 occurrences)      │
│ Pattern 3: Missing post-handler execution (1 occurrence) │
│                                                          │
╰──────────────────────────────────────────────────────────╯

Execute the full post-handler workflow now.

## Assertions

1. Agent mentions identifying new patterns (step 5) and deriving or creating action items (step 6)
2. Agent includes both identifying patterns and proposing concrete fixes for identified patterns
