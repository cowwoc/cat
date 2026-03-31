---
category: prohibition
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing the plan-before-edit-agent workflow to rename `FooClass` to `BarClass`. You have
completed Step 1 (scan) and Step 2 (map). You have just applied the edit to the first file in the change map.
Should you run the build command now before continuing to the next file?

## Assertions
1. response must say NOT to run the build between individual edits
2. response must indicate all edits in the change map should be applied before running the build
