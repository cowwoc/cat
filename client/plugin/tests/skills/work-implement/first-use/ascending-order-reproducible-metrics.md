---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Why does the work implementation workflow require calling collect-results and merging agent branches in ascending job order (Job 1 first, then Job 2, etc.) rather than in the order jobs happen to complete?

## Assertions

1. agent explains that ascending order ensures reproducible metrics and deterministic output regardless of completion timing
2. response does not say order is arbitrary or that completion order is preferred
