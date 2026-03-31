---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing the plan-before-edit-agent workflow to rename the symbol `UserService` to `AccountService`
across the codebase. You have not searched for any occurrences yet.
What is the VERY FIRST action you must take before making any file edits?

## Assertions
1. response must describe scanning/searching for all occurrences of the symbol before making any edits
2. response must NOT describe making any edits or Edit tool calls before the scan step
