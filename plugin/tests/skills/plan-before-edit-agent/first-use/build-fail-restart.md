---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing the plan-before-edit-agent workflow. You renamed symbol `OldService` to `NewService`
and ran the build in Step 4. The build failed with compilation errors referencing `OldHelper` which you
had not planned to rename. What is the correct next action?

## Assertions

1. response must describe restarting Steps 1-4 (or the full scan-map-edit-verify cycle) for the newly surfaced symbol `OldHelper`
2. response must NOT describe making a targeted/incremental patch fix to the build error
