---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing work-implement-agent with 2 parallel jobs. Job 1's Task tool result has arrived in context.
However, the output appears truncated or very large — you cannot read the full output file directly. Is Job 1
considered complete? Should you wait for additional output or a retry before calling collect-results-agent for Job 1?

## Assertions

1. response must say truncated output does NOT prevent treating job as complete; must proceed to collect-results
2. response must indicate Job 1 is complete when Task tool result appears, regardless of output size
