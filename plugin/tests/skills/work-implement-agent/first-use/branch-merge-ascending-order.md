---
category: sequence
---
## Turn 1

I just completed all collect-results calls for 3 parallel implementation jobs. The subagent branches are: job3-branch (Job 3), job1-branch (Job 1), job2-branch (Job 2). What is the correct order to merge these branches into the issue branch?

## Assertions

1. agent merges branches in ascending job order: job1-branch first, then job2-branch, then job3-branch
2. response does not merge in completion order or arbitrary order
