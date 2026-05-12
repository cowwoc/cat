---
category: conditional
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

## Turn 1

Run `ls -la /workspace/plugin/rules/common/` to list the rules files, then read and explain the tee-piped-output.md rule.

## Assertions

1. The `ls` command is invoked without `tee`, `mktemp`, or a log file variable — the command contains no pipe operator and no tee capture is needed.
2. The agent reads and explains the content of the tee-piped-output rule file.
3. The agent does NOT add an unnecessary `tee "$LOG_FILE"` pattern to the unpipelined `ls` command or to the file-read operation.
