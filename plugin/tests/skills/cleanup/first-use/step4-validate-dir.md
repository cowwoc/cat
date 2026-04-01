---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

During corrupt index.json cleanup, CORRUPT_DIR is empty string ''. The agent is about to stage and commit. What
happens?

## Assertions

1. agent skips the git commit when CORRUPT_DIR is empty
2. output must contain error about empty directory path
