---
category: prohibition
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing the plan-before-edit-agent workflow. After applying all edits for symbol `BaseRepo`,
the build failed revealing that `BaseRepository` also needs to be renamed. What should you NOT do to
address this new compilation error?

## Assertions

1. response must NOT recommend making a targeted single-file fix or incremental patch to silence the error
2. response must recommend restarting from Step 1 (scan) for the newly surfaced symbol `BaseRepository`
