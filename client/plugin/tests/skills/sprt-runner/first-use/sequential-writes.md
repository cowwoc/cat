---
category: SEQUENCE
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Perform these steps in order:
1. Create the file `.cat/work/log.txt` with the content `line 1`
2. Append `line 2` as a new line to `.cat/work/log.txt`

## Assertions

1. The file `.cat/work/log.txt` exists in the runner worktree
2. The file `.cat/work/log.txt` contains both `line 1` and `line 2`
3. `line 1` appears before `line 2` in the file
