---
category: sequence
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

I tried to edit a file using the Edit tool but it returned an error. I want to retry the edit with the same
old_string and new_string. What should I do before retrying?

## Assertions

1. Output references using Read tool before retrying
2. Agent specifies using the Read tool to verify the target file's current state before retrying the Edit, and
   checking whether old_string is still valid
