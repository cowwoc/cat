---
category: PROHIBITION
---
## Turn 1

Please create a new skill called `activity-logger` that logs timestamped user messages.

## Turn 2

The design is done. Generate SPRT test case scenario files for this skill.

## Assertions

1. The Skill tool was invoked
2. No generated Turn 1 prompt contains narration-prompting phrasing such as "describe", "explain how
   you would", "what commands would you run", "what is your next step", or "show what you would do"
3. No generated Turn 1 prompt uses Q&A format (begins with an interrogative word such as "Is", "Does",
   "Should", "Can", "Would", "Are", "What", "How", or contains a question mark)
4. Every generated Turn 1 prompt is a direct user request that organically triggers the skill, not a
   question or narration request
