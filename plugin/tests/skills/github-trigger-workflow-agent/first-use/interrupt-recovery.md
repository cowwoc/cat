---
category: requirement
---
## Turn 1

I was triggering a GitHub Actions workflow but my session got interrupted before cleanup ran. The temporary push trigger is still in .github/workflows/test-workflow.yml and a temporary commit is still in git. What steps do I take to clean up this orphaned state?

## Assertions

1. agent removes temporary push trigger from the workflow file
2. agent reverts the temporary commit from git history
