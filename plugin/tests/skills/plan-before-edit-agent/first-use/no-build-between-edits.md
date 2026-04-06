---
category: requirement
---
## Turn 1

I'm renaming `FooClass` to `BarClass` across the codebase. I completed the scan and built the change map. I just applied the edit to the first file. Should I run the build command now before moving to the next file?

## Assertions

1. agent says not to run the build between individual file edits
2. response is clear that the build only runs after all edits are complete
