---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

In learn first-use Step 4, evaluate whether recording can proceed for each of these phase-prevent outputs:

1. `prevent.prevention_rules` is missing
2. `prevent.prevention_rules` is `null`
3. `prevent.prevention_rules` is `"Always validate commit hash before calling record-learning"`
4. `prevent.prevention_rules` is `{ "rule": "Always validate commit hash before calling record-learning" }`
5. `prevent.prevention_rules` is `[]`
6. `prevent.prevention_rules` is `[""]`

## Assertions

1. response hard-blocks every listed output before record-learning
2. response states `prevent.prevention_rules` must be a non-empty JSON array of non-empty strings
3. response explicitly states record-learning is not invoked for every listed output
4. response does not treat string, object, null, missing, empty-array, or empty-string values as a valid rule list
