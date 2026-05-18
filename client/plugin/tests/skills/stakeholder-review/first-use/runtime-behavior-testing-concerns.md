---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Run a stakeholder review after an implementation removes a test that only scanned source files for package structure.
The testing reviewer is selected.

## Assertions

1. agent instructs testing reviewers to identify missing engine behavior coverage with meaningful inputs and outputs
2. agent tells reviewers not to request source-scanning, package-structure, or release-artifact-layout tests unless engine behavior is exercised
3. agent treats removal of layout-only tests as acceptable unless an equivalent engine behavior is left untested
