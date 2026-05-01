---
category: conditional
---
## Turn 1

I tried to trigger a GitHub Actions workflow but 'gh --version' fails with 'command not found' — the GitHub CLI is not installed. What error does the skill produce?

## Assertions

1. agent reports an error about gh CLI not being available
2. agent does not proceed without the gh CLI
