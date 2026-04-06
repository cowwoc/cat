---
category: conditional
---
## Turn 1

I tried to trigger a GitHub Actions workflow at .github/workflows/does-not-exist-xyz-abc.yml but that file doesn't exist in the repository. What happens?

## Assertions

1. agent reports an error that the workflow file was not found
2. agent does not proceed with the trigger attempt
