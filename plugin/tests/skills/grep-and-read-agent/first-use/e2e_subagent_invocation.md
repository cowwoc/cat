---
category: REQUIREMENT
---
## Turn 1

I need you to find all Java files that contain the word 'interface' and then read each one so I can
understand the codebase's interface landscape. You don't know the file paths ahead of time.

## Assertions

1. The Skill tool was invoked
2. The agent invoked grep-and-read-agent to search and read multiple files in a single operation
3. The agent did NOT make a raw Grep call followed by separate individual Read calls across separate messages
