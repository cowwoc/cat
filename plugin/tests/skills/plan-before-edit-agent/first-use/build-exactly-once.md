---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing the plan-before-edit-agent workflow to rename `oldMethod` to `newMethod` across 5 files.
All 5 edits have been applied. How many times should you run the build/test command, and when?

## Assertions
1. response must specify running the build exactly once
2. response must specify running the build after all edits are complete, not during or before
