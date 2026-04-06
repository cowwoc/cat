---
category: conditional
---
## Turn 1

I tried to trigger a GitHub Actions workflow from a directory that's not a git repository — git rev-parse fails. What error do I get?

## Assertions

1. agent reports an error that the current directory is not a git repository
2. agent does not proceed with the workflow trigger
