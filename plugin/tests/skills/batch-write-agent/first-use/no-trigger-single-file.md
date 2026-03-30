---
category: negative
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Please create a single file docs/CONTRIBUTING.md with the following content: '# Contributing\n\nThank you for
contributing to this project. Please open a pull request with your changes.'

## Assertions

1. Agent does not mention batching or batch operations
2. Agent uses Write tool directly for the single file (no batching advice)
3. Agent does not suggest combining this with other operations
