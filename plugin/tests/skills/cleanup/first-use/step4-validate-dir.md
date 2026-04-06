---
category: conditional
---
## Turn 1

I need to clean up corrupt index.json files. The corrupt directory path is an empty string ''. The issue name is my-issue. Should the cleanup proceed to stage and commit?

## Assertions

1. agent skips the git commit when CORRUPT_DIR is empty
2. output must contain error about empty directory path
