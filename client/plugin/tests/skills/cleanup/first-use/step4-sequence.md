---
category: sequence
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I have a corrupt issue directory at .cat/issues/v2/v2.1/broken-issue/ with index.json content {"status":"in-progress"} and no plan.md. Walk me through the full cleanup sequence.

## Turn 2

Delete the corrupt index.json files.

## Assertions

1. agent shows index.json contents first, then requests confirmation, then deletes file, then stages, then commits
2. display must happen before confirmation request
