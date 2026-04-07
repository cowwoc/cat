---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are running the semantic comparison algorithm (Section 2 of validation-protocol.md). Evaluate the
following two scenarios and determine whether the CONSEQUENCE severity upgrade rule applies in each case.

**Scenario A — 5-line proximity (upgrade SHOULD apply):**

The original source document contains:

```
Line 10: NEVER delete the lock file manually.
Line 11:
Line 12: Running scripts from outside the repo root is unsupported.
Line 13:
Line 14: Deleting the lock file causes the session manager to lose track of active workers.
```

The PROHIBITION unit is at line 10. The CONSEQUENCE unit ("Deleting the lock file causes...") is at
line 14 — exactly 5 lines away. Both units share the same subject (the lock file). The CONSEQUENCE
describes what goes wrong if the PROHIBITION is violated. The compressed document omits the CONSEQUENCE
unit entirely.

**Scenario B — 6-line proximity (upgrade should NOT apply):**

The original source document contains:

```
Line 10: NEVER delete the lock file manually.
Line 11:
Line 12: Running scripts from outside the repo root is unsupported.
Line 13:
Line 14: Ensure the configuration schema is validated before deployment.
Line 15:
Line 16: Deleting the lock file causes the session manager to lose track of active workers.
```

The PROHIBITION unit is at line 10. The CONSEQUENCE unit ("Deleting the lock file causes...") is at
line 16 — exactly 6 lines away. The compressed document omits the CONSEQUENCE unit entirely.

For each scenario, state whether the CONSEQUENCE unit is classified as HIGH severity or MEDIUM severity,
and whether the gate decision is FAIL or WARN.

## Assertions

1. for Scenario A (5-line proximity), response must classify the CONSEQUENCE unit as HIGH severity
2. for Scenario A, response must set the gate decision to FAIL (not WARN)
3. for Scenario B (6-line proximity), response must classify the CONSEQUENCE unit as MEDIUM severity
   (upgrade does NOT apply at 6 lines)
4. for Scenario B, response must set the gate decision to WARN (not FAIL)
5. response must demonstrate that the boundary condition is exactly 5 lines (inclusive triggers upgrade,
   6 lines does not)
