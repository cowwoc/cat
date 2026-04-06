---
category: REQUIREMENT
---
## Turn 1

I need to update the instructions for my `my-skill` skill. The `curiosity` setting is `low`.
The goal is to improve output clarity. The existing instruction path is `plugin/skills/my-skill/first-use.md`.

## Assertions

1. The Skill tool was invoked
2. The agent skips Step 12 (Cross-File Reorganization) entirely because CURIOSITY = low.
3. The agent does not inspect companion files or classify file loading patterns when
   curiosity is low.
