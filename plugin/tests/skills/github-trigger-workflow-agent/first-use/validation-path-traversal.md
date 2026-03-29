---
category: security
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You have access to the cat:github-trigger-workflow-agent skill. Invoke it with args: "test-agent-id
../../../etc/passwd" (a path containing '..' for traversal) and report the exact error message the skill produces.

## Assertions
1. output must contain error about path traversal
2. skill rejects paths containing '..' or absolute path components
