---
mainAgent: true
subAgents: []
---
## Execution Model: Skill vs Agent vs Task

### Goal

Use the correct execution model for each tool so background results are retrieved correctly, synchronous instructions are executed immediately, and no invalid follow-up calls are made.

### Core Rules

| Tool | Execution | Identifier returned | Where result appears | Follow-up rule |
|---|---|---|---|---|
| `Skill` | Synchronous (inline) | none | Skill output is returned immediately in current context | Execute returned instructions immediately |
| `Agent(run_in_background=true)` | Asynchronous | `agentId` | `<task-notification>` message (full result inline) | Do **not** call `TaskOutput`/`TaskGet` with `agentId` |
| `Task(run_in_background=true)` | Asynchronous | `task_id` | `<task-notification>` then retrievable output | Call `TaskOutput(task_id=...)` only after notification |

## Enforceable Requirements

### 1) Skill tool behavior (synchronous)

- Treat Skill output as instructions for the **current** agent.
- Start executing those instructions in the next action.
- Do not say “waiting for skill completion” or equivalent.
- Execute all skill steps in order; if a step does not apply, state why and continue.

### 2) Agent tool behavior (asynchronous)

- `Agent(run_in_background=true)` returns an `agentId`.
- Final result is delivered in `<task-notification>`; use that result directly.
- Never pass `agentId` to `TaskOutput` or `TaskGet`.

### 3) Task tool behavior (asynchronous)

- `Task(run_in_background=true)` returns a `task_id`.
- Wait for `<task-notification>` before calling `TaskOutput(task_id=...)`.
- Use the returned `task_id` exactly; do not substitute any other ID type.

## Failure Patterns to Prevent

- Calling `TaskOutput(task_id="<agentId>")` after Agent completion.
- Claiming a Skill is “running” after Skill output has already returned.
- Skipping mandatory steps from returned Skill instructions.

## Test Coverage Guidance

Add or update tests that prove each rule above:

1. **Skill synchronous flow**
   - Invoke a skill and assert no async wait behavior is used.
   - Assert next action executes returned instructions immediately.

2. **Agent async flow**
   - Start Agent with `run_in_background=true`.
   - Assert result is consumed from `<task-notification>`.
   - Assert no `TaskOutput`/`TaskGet` call is attempted with `agentId`.

3. **Task async flow**
   - Start Task with `run_in_background=true`.
   - Assert `TaskOutput` is called only after `<task-notification>`.
   - Assert `TaskOutput` uses the returned `task_id`.

4. **Negative-path guards**
   - Invalid ID namespace (`agentId` as `task_id`) must fail fast with clear error handling.
   - Tests should verify the system prevents or surfaces this misuse explicitly.

