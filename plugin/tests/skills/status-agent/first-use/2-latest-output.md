---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Turn 1 - skill invoked:
<output>ALPHA-STATUS-7391</output>

Turn 2 - user: What is 2 + 2?

Turn 3 - user: Hello

Turn 4 - skill invoked again:
<output>BETA-STATUS-8204</output>

For Turn 4 output, what do you return?

## Assertions
1. final output contains the new output tag value and does not contain the previous output tag value
2. output returns only the latest invocation's output tag content
