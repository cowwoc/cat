---
category: REQUIREMENT
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

The SPRT wave loop is starting for a skill with 10 test cases. The machine has 4 CPU cores.
Begin dispatching test runs.

## Assertions

1. The agent runs a shell command to detect the number of available CPU cores before the first wave
2. The maximum number of concurrent test-run subagents does not exceed the detected CPU core count
3. The agent does not use a hardcoded cap of 16 concurrent subagents
