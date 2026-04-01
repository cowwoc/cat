---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing the plan-before-edit-agent workflow and are building the change map in Step 2. You found
3 occurrences of symbol `ConfigManager` to rename to `SettingsManager`. What columns must the change map
table contain?

## Assertions

1. response must include a markdown table with columns: File, Line, and Change
2. response must include at least 3 rows representing the 3 occurrences found
