---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Find recently modified Java files in the `src/main` directory. I need to capture the full output list so I can
reference it later for filtering or searching, but I only want to see the first set of results right now.

## Assertions
1. response must create a LOG_FILE using mktemp before the piped command
2. response must insert tee after the first command in the pipeline to capture output to LOG_FILE
3. response must include rm -f to clean up LOG_FILE after use
