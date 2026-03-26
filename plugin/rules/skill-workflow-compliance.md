---
mainAgent: true
---
## Skill Workflow Compliance
**CRITICAL**: When a skill is invoked, follow its documented workflow COMPLETELY.

**NEVER**: Invoke skill then manually do subset of steps, skip steps as "unnecessary"
**ALWAYS**: Execute every step in sequence; if step doesn't apply, note why and continue

Skills exist to enforce consistent processes. Shortcuts defeat their purpose.

**Skills execute in the current agent's context — they are not subagents.**

When the Skill tool returns content, that content is the skill's instructions. The current agent
executes those instructions directly. There is no subprocess, no background task, and no
`<task-notification>` to wait for. After the Skill tool returns, act on the instructions immediately.

**NEVER** say "awaiting skill completion" or "the skill is running" — skills do not run independently.
**ALWAYS** read the returned instructions and begin executing them in the next action.
