---
mainAgent: true
subAgents: []
---
## Execution Model: Skill vs Agent vs Task

**CRITICAL**: The Skill, Agent, and Task tools have fundamentally different execution semantics. Confusing them causes
silent failures (calling TaskOutput on an agentId) or wasted steps (waiting for a skill that already completed
synchronously).

| Tool | Execution | Result Delivery | Can call TaskOutput? | Key Rule |
|------|-----------|-----------------|----------------------|----------|
| `Skill tool` | Synchronous (inline) | Instructions returned to current agent context | N/A | Execute instructions immediately; NEVER say "awaiting skill completion" |
| `Agent(run_in_background=true)` | Asynchronous | `<task-notification>` inline | NO — will fail | NEVER call TaskOutput/TaskGet with an agentId |
| `Task(run_in_background=true)` | Asynchronous | `<task-notification>` + TaskOutput | YES — after notification | Call TaskOutput only after notification fires |

### Skill Tool (Synchronous)

**CRITICAL**: Skills execute in the current agent's context — they are not subagents.

When the Skill tool returns content, that content is the skill's instructions. The current agent executes
those instructions directly. There is no subprocess, no background task, and no `<task-notification>` to wait for.
After the Skill tool returns, act on the instructions immediately.

**NEVER**: Invoke skill then manually do subset of steps, skip steps as "unnecessary"
**ALWAYS**: Execute every step in sequence; if step doesn't apply, note why and continue

Skills exist to enforce consistent processes. Shortcuts defeat their purpose.

**NEVER** say "awaiting skill completion" or "the skill is running" — skills do not run independently.
**ALWAYS** read the returned instructions and begin executing them in the next action.

### Agent Tool (Asynchronous)

**CRITICAL**: `Agent(run_in_background=true)` returns an `agentId`.

Results arrive ONLY via `<task-notification>` system messages. The `<task-notification>` contains the FULL result
inline.

**NEVER call `TaskOutput` or `TaskGet` with an agentId** — Agent tasks use a different ID namespace and `TaskOutput`
will always fail with "No task found".

### Task Tool (Asynchronous)

`Task(run_in_background=true)` returns a `task_id`.

Results are retrievable via `TaskOutput` AFTER the `<task-notification>` fires. Call `TaskOutput(task_id=...)` only
after receiving the notification.

### Common Failure Patterns

- Calling `TaskOutput(task_id='<agentId>')` after an Agent background task completes — the agentId is not a valid
  TaskOutput ID; the result is already in the notification
- Saying "awaiting skill completion" or "the skill is running" after invoking Skill tool — skills are synchronous;
  the instructions are already returned
- Skipping skill steps as "unnecessary" — all steps must execute in sequence
