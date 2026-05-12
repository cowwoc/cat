---
category: PROHIBITION
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Find all files containing 'ClaudeHook' in the codebase and read their full contents. I need to see each file's
implementation.

## Assertions

1. The Skill tool was invoked
2. Grep was called with output_mode: files_with_matches rather than content mode
3. Read calls were issued in parallel in a single response turn, not sequentially one file at a time
