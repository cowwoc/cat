---
category: conditional
---
## Turn 1

I want to trigger the GitHub CI workflow at .github/workflows/build.yml. The workflow file already has a push trigger, but it's scoped to 'branches: [main, develop]' and doesn't include my current branch 'feature/my-branch'. What does the skill determine about the existing trigger and what value does it set for ADDED_TRIGGER?

## Assertions

1. agent recognizes the existing trigger does not cover the current branch and sets ADDED_TRIGGER=true
2. output explicitly states what value ADDED_TRIGGER is set to
