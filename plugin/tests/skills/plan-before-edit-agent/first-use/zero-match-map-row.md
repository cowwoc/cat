---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing the plan-before-edit-agent workflow. You scanned for symbol `DeprecatedType` and found
zero grep matches. You are now building the change map table in Step 2. How should this symbol appear in
the change map?

## Assertions
1. response must include a table row with `(none)` in the File column for the symbol with no matches
2. response must indicate "No usages found" in the Change column for that row
