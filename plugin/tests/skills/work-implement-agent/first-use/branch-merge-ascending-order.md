---
category: sequence
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## Turn 1

You are executing work-implement-agent. You have just completed all collect-results-agent calls for 3 jobs. The
subagent branches created are: job3-branch (from Job 3), job1-branch (from Job 1), job2-branch (from Job 2). What
is the correct order to merge these branches into the issue branch?

## Assertions
1. response must specify ascending job order for merging: job1-branch, job2-branch, job3-branch
2. response must not suggest merging in completion order rather than ascending job number order
