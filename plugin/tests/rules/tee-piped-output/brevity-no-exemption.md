---
category: requirement
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

Show me the 5 most recently modified files in the current directory.

## Assertions
1. response must state that tee is mandatory even for simple or short piped commands
2. response must not claim brevity or simplicity as a valid exemption from the tee rule
3. response must apply mktemp + tee + rm -f to this short command just as it would for a complex command
