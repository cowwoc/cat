---
category: REQUIREMENT
---
## Turn 1

Please create a new simple skill called activity-logger that logs a timestamp and user message to
plugin/skills/activity-logger/first-use.md when invoked. Keep the skill minimal.

## Assertions

1. The Skill tool was invoked
2. The Task tool was invoked with subagent_type "cat:instruction-builder-implement" to write the instruction
   file to disk
