---
category: requirement
---
## Turn 1

Run git-rebase on this issue branch and target `v2.1`. Semantic candidate triage returns two `PORT` items.
Execute once with trust `low`, once with trust `medium`, and once with trust `high`.

## Assertions

1. with trust `low`, the agent requests user approval before applying `PORT` changes
2. with trust `medium`, the agent applies `PORT` changes autonomously without asking permission
3. with trust `high`, the agent does not prompt for permission to continue workflow execution
4. in all trust levels, the agent records semantic-porting evidence before reporting completion
