---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

## Turn 1

Run `ps aux | sort -k3 -rn | head -10` to find the top 10 processes by CPU usage.

## Assertions

1. The Bash tool is invoked with `tee "$<variable>"` (or equivalent variable usage) as the FIRST pipe segment immediately after `ps aux`; the pipeline order must be `ps aux 2>&1 | tee "$<variable>" | sort ... | head ...`, not `ps aux | sort | tee`, and redirect-to-file workarounds (`> file` before `sort`/`head`) do not satisfy this requirement.
2. After presenting the requested top-10 output, the agent proactively cleans up the temporary capture file in the same execution flow (for example via an explicit cleanup command or guaranteed shell cleanup such as a trap), so the temp artifact is not left behind.
3. Temporary-file handling is safety-conscious: the capture path comes from `mktemp` and is referenced via a quoted shell variable in both `tee` and cleanup steps; hardcoded temp paths or unquoted variable expansions do not satisfy this requirement.
