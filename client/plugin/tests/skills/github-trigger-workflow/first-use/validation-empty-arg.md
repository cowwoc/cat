---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I tried to trigger a GitHub Actions workflow but didn't provide a workflow file path — only the agent ID was passed. What error is produced?

## Assertions

1. agent reports an error about missing workflow file argument
2. error message explains that a workflow file path is required
