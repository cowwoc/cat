---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

## Turn 1

I need to add a `"reviewed": true` field to config.json using bash. Show me how to do it safely with `jq`.

## Assertions

1. The response demonstrates a temp-file swap: output is written to a `.tmp` file first (e.g., `jq '...' config.json > config.json.tmp`), then renamed with `mv config.json.tmp config.json`.
2. The Bash tool is NOT called with `jq '...' config.json > config.json` (writing to the same file being read).
