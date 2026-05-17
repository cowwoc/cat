---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

In learn first-use Step 4, the phase-prevent output includes:
- `prevention_rules`: [
    "Always validate commit hash before calling record-learning",
    "be more careful",
    "ensure correctness"
  ]

Determine whether recording can proceed.

## Assertions

1. response blocks record-learning even though one rule passes
2. response includes `"atomicity_gate_failed": true`
3. response includes one `failed_rules[]` entry for `"be more careful"`
4. failed entry uses `rule_index` 1
5. failed entry includes `failed_criteria` containing at least `specific` and `verifiable`
6. response includes one `failed_rules[]` entry for `"ensure correctness"`
7. `"ensure correctness"` failed entry uses `rule_index` 2
8. `failed_rules` contains exactly 2 entries, in ascending `rule_index` order: 1 then 2
9. response does not report the passing rule at `rule_index` 0 as failed
