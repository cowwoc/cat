---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Start the full test suite in the background so I can continue working on other tasks while the tests run.

## Assertions
1. response must state that commands run with run_in_background=true are exempt from the tee rule
2. response must not require tee to be inserted for this background command
3. response must explain that background task output is already captured and retrievable
