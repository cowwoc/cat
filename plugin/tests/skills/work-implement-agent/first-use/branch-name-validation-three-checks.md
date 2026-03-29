---
category: dependency
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are about to merge subagent branch 'my-issue-2.1-sub-abc123' into the issue branch. List ALL the validation
checks you must perform on the branch name before attempting the git merge command.

## Assertions
1. response must include all 3 required validation checks: character set, prefix, and git existence
2. response must not attempt the git merge without first verifying all three validation conditions
