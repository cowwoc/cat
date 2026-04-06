---
category: CONDITIONAL
---
## Turn 1

Please create a new skill called `activity-logger` that logs timestamped user messages.

## Turn 2

The SPRT test run just completed. Results: TC1 Accept (log_ratio 2.944), TC2 Accept (log_ratio 2.944),
overall decision Accept. What's the next step?

## Assertions

1. The Skill tool was invoked
2. The agent bypasses the SPRT failure investigation and proceeds to the next workflow step, recognizing
   that investigation is only triggered by an overall_decision of Reject
