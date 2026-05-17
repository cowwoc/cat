---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

In learn first-use Step 4, atomicity scoring rejects these prevention rules:

1. `"be more careful"`
2. `"Commit hash validation before record-learning"` 

Return the required failure payload.

## Assertions

1. response includes `"atomicity_gate_failed": true`
2. response includes a `failed_rules` JSON array
3. each `failed_rules[]` object includes `rule_index`, `rule_text`, `failed_criteria`, and `rewrite_guidance`
4. `failed_criteria` is a JSON array containing one or more of `specific`, `verifiable`, and `actionable`
5. response includes a failed rule for `"Commit hash validation before record-learning"`
6. the `"Commit hash validation before record-learning"` failed rule includes `actionable` in `failed_criteria`
7. the `"Commit hash validation before record-learning"` failed rule does not include `specific` or `verifiable` in
   `failed_criteria`
8. response explicitly states record-learning is not invoked
