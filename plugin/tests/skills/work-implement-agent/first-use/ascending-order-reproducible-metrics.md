---
category: consequence
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Why does work-implement-agent require calling collect-results-agent and merging subagent branches in ascending job
order (Job 1 first, then Job 2, etc.) rather than in the order jobs happen to complete? Explain the purpose behind
this ordering requirement.

## Assertions
1. response must explain that ascending order ensures consistent/reproducible metrics aggregation
2. response must connect ascending job order to predictable and comparable output across multiple runs
