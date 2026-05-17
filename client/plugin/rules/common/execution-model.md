---
subAgents: []
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
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
- If the skill output contains `Launching skill: <name>`, treat that line as a handoff signal, not completion.
- Immediately execute the launched skill's instructions inline (`SKILL.md` loading contract + `first-use.md`) in the same flow.
- Continue execution until a terminal phase state is reached: explicit approval gate, explicit blocker/failure, or explicit success/merge completion.
- Do not stop at wrapper invocation or report success based only on `Launching skill` output.
- Do not say “waiting for skill completion” or equivalent.
- Execute all skill steps in order; if a step does not apply, state why and continue.
- For gate-based flows, non-option conversational replies do not count as approval; re-present the full gate and wait for explicit option selection.
- Status updates must include concrete phase evidence (for example: parsed prepare JSON, lock state, gate prompt, or failure payload), not only wrapper-launch acknowledgments.

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
- Re-invoking wrapper skills in a loop (for example `/cat:work`) without consuming and executing the launched phase instructions.
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

