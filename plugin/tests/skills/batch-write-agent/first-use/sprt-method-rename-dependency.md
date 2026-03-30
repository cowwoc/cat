---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Please rename the method `processOrder()` to `handleOrder()` in two files at once: first edit OrderService.java to
rename the method definition from `processOrder()` to `handleOrder()`, then edit OrderServiceTest.java to replace
the call `processOrder()` with `handleOrder()`. Batch both edits in one response.

## Assertions

1. Agent identifies the dependency and does NOT batch both edits — OrderServiceTest.java's old_string references
   a method that OrderService.java's edit is simultaneously renaming
