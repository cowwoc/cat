---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I tried to trigger a GitHub Actions workflow for .github/workflows/build.yml but I'm in a detached HEAD state — git branch --show-current returns empty. What error do I get?

## Assertions

1. agent reports an error about not being on a named branch
2. error message does not proceed with the workflow trigger
