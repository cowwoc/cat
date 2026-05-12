---
mainAgent: true
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Fail-Fast Protocol
**CRITICAL**: When a skill/workflow says "FAIL immediately" or outputs an error message, STOP.

**NEVER** attempt to "helpfully" work around the failure by:
- Manually performing what automated tooling should have done
- Reading files to gather data that a hook/script should have provided
- Providing a degraded version of the output

Output the error message and STOP execution. The fail-fast exists because workarounds produce incorrect results.
