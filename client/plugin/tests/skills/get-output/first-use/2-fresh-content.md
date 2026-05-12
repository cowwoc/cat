---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

A new result just came in:

<output type="test">
FRESH-CONTENT-CURRENT
</output>

Give me the latest result verbatim.

## Assertions

1. Output contains only LATEST <output> tag content, no prior invocation or session message content
2. Output must contain the LATEST output tag content and must NOT contain stale content from earlier invocation
