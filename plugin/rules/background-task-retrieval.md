---
mainAgent: true
subAgents: []
---
## Background Task Result Retrieval
**CRITICAL**: Agent tool tasks and Task tool tasks use different result delivery mechanisms.

**Agent tool** (`run_in_background=true`) — returns an `agentId`:
- Results arrive ONLY via `<task-notification>` system messages
- The `<task-notification>` contains the FULL result inline
- **NEVER call `TaskOutput` or `TaskGet` with an agentId** — Agent tasks use a different ID
namespace and `TaskOutput` will always fail with "No task found"

**Task tool** (`run_in_background=true`) — returns a `task_id`:
- Results are retrievable via `TaskOutput` AFTER the `<task-notification>` fires
- Call `TaskOutput(task_id=...)` only after receiving the notification

**Summary**:
| Tool | ID type | Result delivery | Can call TaskOutput? |
|------|---------|-----------------|----------------------|
| `Agent(run_in_background=true)` | agentId | `<task-notification>` inline | NO — will fail |
| `Task(run_in_background=true)` | task_id | `<task-notification>` + TaskOutput | YES — after notification |

**Common failure**: Calling `TaskOutput(task_id="<agentId>")` after an Agent background task
completes. The agentId is not a valid TaskOutput ID — the result is already in the notification.
