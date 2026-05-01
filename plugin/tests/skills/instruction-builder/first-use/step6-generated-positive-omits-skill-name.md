---
category: PROHIBITION
---
## Turn 1

Please create a new skill called `activity-logger` that logs timestamped user messages.

## Turn 2

The design is done. Generate SPRT test case scenario files for this skill.

## Assertions

1. The Skill tool was invoked
2. The ## Turn 1 content of each generated positive test case scenario does not contain the text
   "cat:activity-logger"
3. The ## Turn 1 content of each generated positive test case scenario does not directly instruct
   the agent to invoke the skill by name (e.g., "use cat:activity-logger", "run cat:activity-logger",
   "invoke cat:activity-logger")
4. Each generated positive scenario describes a realistic user action a developer would type, rather
   than an explicit skill invocation command
