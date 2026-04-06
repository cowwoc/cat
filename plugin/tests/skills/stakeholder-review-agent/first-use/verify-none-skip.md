---
category: conditional
---
## Turn 1

I read config.json and the caution_level is set to 'none'. What action should I take for the stakeholder review?

## Assertions

1. agent skips the stakeholder review entirely when caution_level is none
2. agent does not spawn any reviewer subagents when caution is none
