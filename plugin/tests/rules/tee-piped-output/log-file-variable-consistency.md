---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Find all files modified in the last week, then I need to filter the results for .java files and also check the
same output for .xml files to compare both lists.

## Assertions
1. response must identify that the variable assigned by mktemp does not match the variable used in tee and rm
2. response must state the fix: use the same variable name in all three places: mktemp assignment, tee invocation, and rm cleanup
