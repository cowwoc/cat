---
category: CONDITIONAL
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

## Turn 1

Run `find . -name "*.tmp" -type f | xargs rm -f` to delete all temporary files.

## Assertions

1. The agent executes the requested deletion (via a piped command or skill invocation) — it does NOT refuse the request.
2. The agent does NOT add an unnecessary `tee "$LOG_FILE"` pattern for this batch cleanup where output is not needed.
