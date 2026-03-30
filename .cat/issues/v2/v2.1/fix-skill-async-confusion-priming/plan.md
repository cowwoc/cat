# Plan

## Goal

Consolidate background-task-retrieval.md and skill-workflow-compliance.md into a single unified rule titled
"Execution Model: Skill vs Agent vs Task" that presents all three tools side-by-side in one table, reducing
confusion between synchronous Skill execution and async Agent/Task execution.

## Pre-conditions

(none)

## Post-conditions

- [ ] Two fragmented rules consolidated into one unified rule file `plugin/rules/execution-model.md`
- [ ] Unified rule contains side-by-side comparison table for Skill, Agent, and Task tools
- [ ] Old rule files (`background-task-retrieval.md`, `skill-workflow-compliance.md`) deleted
- [ ] `plugin/concepts/execution-model.md` renamed to `plugin/concepts/execution-hierarchy.md`
- [ ] All cross-references to `execution-model.md` in plugin/ updated to `execution-hierarchy.md`
- [ ] No new issues introduced

## Approach

1. Rename `plugin/concepts/execution-model.md` → `plugin/concepts/execution-hierarchy.md` (git mv) to free up
   the name `execution-model` for the new rule.
2. Update all 7 cross-references to `execution-model.md` in plugin/ to point to `execution-hierarchy.md`.
3. Create `plugin/rules/execution-model.md` by merging `plugin/rules/background-task-retrieval.md` and
   `plugin/rules/skill-workflow-compliance.md` into a single rule with a side-by-side comparison table.
4. Delete the two old rule files.

No other files reference the old rule filenames, so deletion is safe.

## Files to Update (cross-references to execution-hierarchy.md)

These files contain `execution-model.md` references that must be updated to `execution-hierarchy.md`:

- `plugin/skills/decompose-issue-agent/first-use.md` (2 occurrences)
- `plugin/concepts/token-warning.md` (1 occurrence)
- `plugin/skills/plan-builder-agent/first-use.md` (1 occurrence)
- `plugin/concepts/hierarchy.md` (1 occurrence)
- `plugin/concepts/parallel-execution.md` (1 occurrence)
- `plugin/skills/work-implement-agent/first-use.md` (1 occurrence)

## Jobs

### Job 1
- Rename `plugin/concepts/execution-model.md` to `plugin/concepts/execution-hierarchy.md` using `git mv`
- Update all cross-references listed in "Files to Update" from `execution-model.md` to `execution-hierarchy.md`
  (both bare filename and full path forms, e.g. `concepts/execution-model.md` and `execution-model.md`)
- Create `plugin/rules/execution-model.md` with YAML frontmatter `mainAgent: true` and `subAgents: []`
- The new file must contain:
  - A `## Execution Model: Skill vs Agent vs Task` heading
  - A **CRITICAL** callout: "The Skill, Agent, and Task tools have fundamentally different execution semantics.
    Confusing them causes silent failures (calling TaskOutput on an agentId) or wasted steps (waiting for a skill
    that already completed synchronously)."
  - A side-by-side comparison table with columns: Tool | Execution | Result Delivery | Can call TaskOutput? | Key Rule
    - Row 1: `Skill tool` | Synchronous (inline) | Instructions returned to current agent context | N/A |
      Execute instructions immediately; NEVER say "awaiting skill completion"
    - Row 2: `Agent(run_in_background=true)` | Asynchronous | `<task-notification>` inline | NO — will fail |
      NEVER call TaskOutput/TaskGet with an agentId
    - Row 3: `Task(run_in_background=true)` | Asynchronous | `<task-notification>` + TaskOutput | YES — after notification |
      Call TaskOutput only after notification fires
  - A `### Skill Tool (Synchronous)` subsection containing:
    - **CRITICAL** marker
    - "Skills execute in the current agent's context — they are not subagents."
    - "When the Skill tool returns content, that content is the skill's instructions. The current agent executes
      those instructions directly. There is no subprocess, no background task, and no `<task-notification>` to wait for."
    - "After the Skill tool returns, act on the instructions immediately."
    - "**NEVER**: Invoke skill then manually do subset of steps, skip steps as 'unnecessary'"
    - "**ALWAYS**: Execute every step in sequence; if step doesn't apply, note why and continue"
    - "Skills exist to enforce consistent processes. Shortcuts defeat their purpose."
    - "**NEVER** say 'awaiting skill completion' or 'the skill is running' — skills do not run independently."
    - "**ALWAYS** read the returned instructions and begin executing them in the next action."
  - A `### Agent Tool (Asynchronous)` subsection containing:
    - **CRITICAL** marker
    - "`Agent(run_in_background=true)` returns an `agentId`"
    - "Results arrive ONLY via `<task-notification>` system messages"
    - "The `<task-notification>` contains the FULL result inline"
    - "**NEVER call `TaskOutput` or `TaskGet` with an agentId** — Agent tasks use a different ID namespace and
      `TaskOutput` will always fail with 'No task found'"
  - A `### Task Tool (Asynchronous)` subsection containing:
    - "`Task(run_in_background=true)` returns a `task_id`"
    - "Results are retrievable via `TaskOutput` AFTER the `<task-notification>` fires"
    - "Call `TaskOutput(task_id=...)` only after receiving the notification"
  - A `### Common Failure Patterns` subsection listing:
    - "Calling `TaskOutput(task_id='<agentId>')` after an Agent background task completes — the agentId is not a
      valid TaskOutput ID; the result is already in the notification"
    - "Saying 'awaiting skill completion' or 'the skill is running' after invoking Skill tool — skills are
      synchronous; the instructions are already returned"
    - "Skipping skill steps as 'unnecessary' — all steps must execute in sequence"
- The file must NOT have a license header (`plugin/rules/` files are exempt)
- Delete `plugin/rules/background-task-retrieval.md`
- Delete `plugin/rules/skill-workflow-compliance.md`
- Update `.cat/issues/v2/v2.1/fix-skill-async-confusion-priming/index.json` to set `"status": "closed"` and `"progress": 100`
- Commit type: `refactor:`
- Commit message: `refactor: consolidate async/sync execution rules into unified execution-model rule`
