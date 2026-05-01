---
category: conditional
---
## Turn 1

I tried to trigger a GitHub Actions workflow but didn't provide a workflow file path — only the agent ID was passed. What error is produced?

## Assertions

1. agent reports an error about missing workflow file argument
2. error message explains that a workflow file path is required
