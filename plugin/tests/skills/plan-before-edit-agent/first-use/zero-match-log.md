---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing the plan-before-edit-agent workflow to rename the symbol `LegacyWidget`. You ran the
grep scan and it returned zero matches. What do you do?

## Assertions
1. response must log or state "No usages found for `LegacyWidget`" (or equivalent message with the symbol name)
2. response must continue to the next symbol rather than treating zero matches as an error
