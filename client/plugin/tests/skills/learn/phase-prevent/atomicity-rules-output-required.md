---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are running phase-prevent for learn and have finished the prevention implementation. Produce the final
output JSON exactly as required by the phase.

## Assertions

1. response includes a top-level `prevention_rules` field in the output JSON
2. response sets `prevention_rules` to a non-empty JSON array
3. every `prevention_rules` entry is a non-empty string
4. every `prevention_rules` entry starts with an imperative action such as `Always`, `Run`, `Validate`, `Check`,
   `Verify`, `Inspect`, `Compare`, `Reject`, `Block`, or `Require`
5. every `prevention_rules` entry names concrete context such as a file, command, condition, artifact, commit hash,
   CAT skill, or tool
6. no `prevention_rules` entry is a generic aspiration such as `be more careful` or `ensure correctness`
