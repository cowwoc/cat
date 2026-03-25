# Goal

The work-agent skill's SKILL.md description lists trigger words that do not include 'next issue' or 'next'. When users say 'Next issue' to move to the next available work item, the agent cannot match this intent to cat:work-agent and instead tries non-existent skill names like /cat:work.

# Post-conditions

- plugin/skills/work-agent/SKILL.md trigger words include 'next issue' and 'next'
- Agent correctly resolves 'Next issue' to cat:work-agent skill invocation
