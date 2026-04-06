---
category: requirement
---
## Turn 1

I renamed `oldMethod` to `newMethod` across 5 files. All 5 edits have been applied. How many times should I run the build/test command, and when?

## Assertions

1. agent specifies that the build should run exactly once, after all edits are applied
2. response does not recommend running the build between individual file edits
