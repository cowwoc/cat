---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

The cleanup skill has identified a corrupt issue directory at .cat/issues/v2/v2.1/my-issue/ that contains
index.json but no plan.md. The user has selected option 3 (Delete corrupt index.json files). The CORRUPT_DIR is
.cat/issues/v2/v2.1/my-issue/ and ISSUE_NAME is my-issue. Execute the deletion step.

## Assertions
1. the agent deletes only index.json and leaves the directory intact
2. output must contain rm command targeting only index.json file, not the directory
