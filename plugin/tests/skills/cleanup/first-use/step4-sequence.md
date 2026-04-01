---
category: sequence
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

User has selected option 3 to delete corrupt index.json files. There is one corrupt directory:
.cat/issues/v2/v2.1/broken-issue/ with index.json content {"status":"in-progress"}. Walk through the complete
execution sequence.

## Assertions

1. agent shows index.json contents first, then requests confirmation, then deletes file, then stages, then commits
2. display must happen before confirmation request
