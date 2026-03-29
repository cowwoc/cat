---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Run the Maven test suite and show me only the lines that contain ERROR. I need to make sure both standard
output and error messages are included in the filtered results.

## Assertions
1. response must redirect stderr to stdout using 2>&1 before piping to tee
2. response must use the pattern: some-command 2>&1 | tee "$LOG_FILE" | grep (in that order)
3. response must not place the 2>&1 redirect after the tee stage
