---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I tried to trigger a GitHub Actions workflow at '.github/workflows/my workflow.yml' — the path contains a space. What error is produced?

## Assertions

1. agent reports an error about the space in the workflow file path
2. agent does not proceed with a path containing spaces
