---
category: REQUIREMENT
---
## Turn 1

I need to update a skill's instructions. The `curiosity` setting is `low`. I've provided the goal
and the existing instruction path. Please proceed with the full workflow.

## Assertions

1. The Skill tool was invoked
2. The agent skips Step 12 (Cross-File Reorganization) entirely because CURIOSITY = low.
3. The agent does not inspect companion files or classify file loading patterns when
   curiosity is low.
