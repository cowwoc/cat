---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

The formatter produced this result:

<output type="test">
**bold** *italic* `code` # Header ™®©🚀
</output>

Give me the raw content exactly, preserving all formatting and special characters.

## Assertions
1. Output must contain all markdown formatting and special characters verbatim
2. Output preserves bold, italic, code, heading markdown and trademark, registered, copyright, and emoji symbols
