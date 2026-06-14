<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan Builder

## Purpose

Build or revise a comprehensive plan.md for a CAT issue. This skill centralizes all planning logic so that
plan.md generation is consistent regardless of which workflow invokes it.

## Arguments

Positional space-separated arguments:

```
<curiosity> <mode> <contextPath> [revision-context]
```

| Position | Name | Description |
|----------|------|-------------|
| 1 | curiosity | Planning depth: `low`, `medium`, or `high` |
| 2 | mode | `revise` |
| 3 | contextPath | Path to a file containing context (see below) |
| 4 | [revision-context] | Optional: Revision instructions for revise mode (e.g., 'add performance tests') |

### Mode: `revise`

Used by `/cat:work` in two contexts: (1) generating execution steps for a lightweight plan (created by `/cat:add`,
containing only goal and post-conditions), and (2) revising an existing plan when requirements change during
implementation. The `contextPath` points to the issue directory (which contains plan.md and index.json).
An additional revision description follows as remaining arguments:

```bash
read CURIOSITY MODE ISSUE_PATH REVISION_CONTEXT <<< "$ARGUMENTS"
# REVISION_CONTEXT receives all remaining words after ISSUE_PATH (may contain spaces)
```

The skill reads the existing plan.md, applies the revision, and writes the updated plan.md in place.

> **Design rationale:** Both contexts use `revise` mode because adding execution steps to a lightweight plan.md (which
> already contains goal and post-conditions) is a revision of that existing document, not creation from scratch. The
> plan already exists; the skill revises it to include implementation details.

## When to Use

- **Adding execution steps** (`/cat:work`): Generate full execution steps for a lightweight plan.md created by
  `/cat:add` (which contains only goal and post-conditions, not a full plan from scratch)
- **Mid-work revision** (`/cat:work`): Revise plan.md when requirements change during implementation

## Effort-Based Planning Depth

Apply the following depth to plan.md content based on `$CURIOSITY`:

- `low`: Generate a concise plan. Assume the obvious approach. Skip alternative analysis. List only essential steps
  and post-conditions.
- `medium`: Explore two or three alternative approaches before settling on one. Note key trade-offs in a brief
  section. Execution steps should cover non-obvious edge cases.
- `high`: Perform deep research on the problem space. Document the reasoning for the chosen approach and explicitly
  list rejected alternatives with rationale. Execution steps must cover all known edge cases and failure modes.

## plan.md Comprehensiveness

The plan.md must be comprehensive enough for a haiku-level model to implement mechanically without making architectural
decisions. Include:
- Exact file paths to create/modify
- Specific code patterns or formats to use
- Complete lists (all files, all references to update, all post-conditions)
- Research findings that inform implementation decisions

If the execution agent needs to make judgment calls about "how" to implement, the plan.md is not detailed enough.
The agent should only decide "how to write the code", not "what approach to take".

## Orchestrator Delegation Model

The plan-builder agent is the strong top-level orchestrator. For Codex, its default is `gpt-5.4` with `high`
reasoning effort. It owns complexity classification, decomposition, milestone structure, contradiction handling,
sequencing, acceptance-criteria quality, and final plan synthesis.

Do not use a weak-orchestrator/escalate-upward design. Delegate bounded supporting work downward and keep final
integration in the plan-builder agent.

### Helper Agent Roles

Use engine-native agent types. Codex names use `cat-*`; Claude names use `cat:*`.

| Role | Codex agent | Claude agent | Use for | Must not do |
|------|-------------|--------------|---------|-------------|
| Evidence explorer/checker | `cat-plan-evidence-agent` | `cat:plan-evidence-agent` | Bounded repo discovery, relevant files/tests/docs, existing patterns, symbol ownership, local contradiction checks | Choose the integrated approach or write plan.md |
| Local option planner | `cat-plan-local-planner-agent` | `cat:plan-local-planner-agent` | Draft local options for one subsystem when design choices are non-obvious | Own final plan synthesis or cross-subsystem trade-offs |
| Normal plan checker | `cat-plan-review-agent` | `cat:plan-review-agent` | Mechanical review of acceptance criteria, sequencing, and implementability for normal plans | Edit plan.md directly |
| Strong plan reviewer | `cat-plan-strong-review-agent` | `cat:plan-strong-review-agent` | Broad, architecture-sensitive, contradiction-prone, or multi-subsystem plan review | Edit plan.md directly |

Delegate independent evidence and narrow checks in parallel. Keep planning local when the task is bounded and final
synthesis is straightforward.

### Mandatory Strong Review Triggers

Classify the plan as broad and run strong review when any of these are true:

- More than 2-3 subsystems are involved
- The plan spans milestones, migrations, rollout, CI/build/plugin lifecycle, or workflow semantics
- Docs, tests, config, or implementation patterns conflict
- Acceptance criteria are judgment-heavy rather than mechanical
- Work is evidence-gated
- Sequencing dependencies are non-trivial
- Performance, concurrency, persistence, compatibility, or public API behavior is involved

### Default Orchestration Workflows

Normal issues:

1. Strong orchestrator classifies scope.
2. Cheap evidence agents gather relevant files, tests, docs, and existing patterns in parallel when those searches are
   independent.
3. Orchestrator writes the draft plan.
4. One medium plan checker verifies acceptance criteria, sequencing, and mechanical implementability.
5. Orchestrator resolves findings and finalizes.

Broad optimization or architecture-sensitive issues:

1. Strong orchestrator maps subsystems and unknowns.
2. Cheap evidence agents gather subsystem evidence in parallel.
3. Medium local planners draft subsystem-local options only where design choices are non-obvious.
4. Evidence agents run bounded contradiction checks over code/docs/tests/config.
5. Orchestrator builds the single integrated draft plan.
6. Strong reviewer audits contradictions, sequencing, architecture sensitivity, and mechanical quality.
7. Orchestrator resolves findings and emits the final plan.

## plan.md Templates

Use the appropriate template based on issue type. Read the issue-plan.md reference for Feature, Bugfix, or Refactor
templates:

```bash
cat "${CAT_PLUGIN_ROOT}/concepts/issue-plan.md"
```

**CRITICAL:** Follow template guidance to separate Execution Jobs/Steps (actions only) from Success Criteria
(measurable outcomes). Do NOT include expected values like "score = 1.0" in Execution sections as this primes
agents to fabricate results.

## Jobs for Parallel Execution

> See `${CAT_PLUGIN_ROOT}/concepts/work-decomposition.md` for the full execution model, hierarchy, and
> parallelism rules.

Use `## Jobs` with `### Job N` sections to organize work into parallel jobs. This is the **default
structure** when an issue has independent work items that don't share files. All jobs spawn simultaneously;
add sequential ordering only when a job genuinely depends on output or side-effects from a prior job.

Rules for jobs:
- Create `## Jobs` section (replaces `## Execution Steps`)
- Each `### Job N` subsection contains bullet items for parallel execution
- **All jobs spawn simultaneously by default** — add a dependency marker only when Job N+1 requires output from Job N
- All items within a job run in parallel
- Jobs must not modify the same files (to avoid merge conflicts)
- The last job is responsible for updating index.json

**Dependency indicators** (sequential ordering required when any of these apply):
- Job N+1 reads a file first written by Job N
- Job N+1 invokes code compiled or generated by Job N
- Job N+1 runs integration tests against artifacts produced by Job N

**Independence indicators** (items are parallelizable when all of these are true):
- Items modify **different files** (no overlapping file modifications between jobs)
- Neither item produces output that the other consumes
- Items can be merged without conflict (no shared Git history dependencies)
- The order of merging items from different jobs does not affect correctness

### Job Sizing Guidance

When writing jobs, size each job's work to stay within 40% of an agent's context budget.

**Estimation heuristic:**
- Count the number of files the job's work must modify or create
- Assess change complexity: trivial (rename, formatting), medium (logic changes, new methods), high
  (new module, significant refactor)
- A job whose work spans > 5 medium-complexity files or > 10 trivial files is likely to exceed 40% context

**Splitting jobs with too much work:**
If a job's work would exceed the 40% budget, split it into two jobs of roughly equal scope before
writing plan.md. Move the second half of the job's items into a new job immediately after it.
Aim for jobs with equal work scope so each agent uses approximately the same context fraction.

**Example — oversized job split:**

```markdown
## Jobs

### Job 1   ← original job (12 files, medium complexity — too large)
- Update 12 service classes to use new interface
```

Split into:

```markdown
## Jobs

### Job 1
- Update 6 service classes (A–F) to use new interface

### Job 2
- Update 6 service classes (G–L) to use new interface
```

**Main Agent Jobs (optional):** If the issue requires skills that spawn their own agents (e.g.,
`/cat:instruction-builder`, `/cat:stakeholder-review`), add a `## Main Agent Jobs` section
**above** `## Jobs`. The main agent executes these skills directly before spawning implementation
agents. Each bullet is a skill invocation:

```markdown
## Main Agent Jobs

- /cat:instruction-builder goal="create or update skill"
```

Omit `## Main Agent Jobs` entirely when the issue has no pre-delegation skills.

Example valid job structure (independent modules):

```markdown
## Jobs

### Job 1
- Implement parser module
- Add parser tests

### Job 2
- Implement formatter module
- Add formatter tests
- Run full test suite
```

Do NOT use multiple jobs if items share files or if the sequential dependency is unclear. In such cases, use a single
`## Jobs` / `### Job 1` section or revert to `## Execution Steps` for sequential execution.

## Research Findings

If research was performed (via `/cat:research` or inline), add a Research Findings section to plan.md after the
Goal/Problem section:

```markdown
## Research Findings
{RESEARCH_FINDINGS}
```

## Workflow

### For Revise Mode (mode=revise)

**Step 1:** Read the existing `${ISSUE_PATH}/plan.md`.

**Step 2:** Read the revision context (`REVISION_CONTEXT` argument) to understand what changed.

**Step 3:** Classify scope as normal or broad using the Orchestrator Delegation Model.

**Step 4:** Gather bounded evidence. For independent searches, spawn evidence agents in parallel. For broad plans,
map subsystems and unknowns first, then run subsystem evidence gathering, local option drafting, and bounded
contradiction checks as needed.

**Step 5:** Update plan.md sections affected by the revision. Preserve completed work and adjust remaining execution
steps.

**Step 6:** Write the revised plan.md content to `PLAN_OUTPUT_PATH` = `${ISSUE_PATH}/plan.md`.

**Step 7:** Run Review Routing (see section below), passing `PLAN_OUTPUT_PATH` and `ISSUE_GOAL`
(from the existing plan.md `## Goal` section).

**Step 8:** Verify the file at `PLAN_OUTPUT_PATH` exists.

## Review Routing

The draft is already written to `PLAN_OUTPUT_PATH`.

Skip review only when curiosity is `low` and no Mandatory Strong Review Trigger applies.

For normal plans with curiosity `medium` or `high`, use the normal plan checker. For broad plans or any Mandatory
Strong Review Trigger, use the strong plan reviewer.

Review agents are review-only. They must not edit `plan.md`. The plan-builder orchestrator applies targeted fixes,
re-runs review, and owns the final plan.

**Prerequisite:** The draft plan.md must already be written to `PLAN_OUTPUT_PATH` before invoking the
review agent.

Spawn the selected review agent with the engine-native agent-spawning tool:

- Codex normal review: `cat-plan-review-agent`
- Codex strong review: `cat-plan-strong-review-agent`
- Claude normal review: `cat:plan-review-agent`
- Claude strong review: `cat:plan-strong-review-agent`

```
Agent-spawning tool:
  description: "Plan completeness review"
  agent_type/subagent_type: "{engine-native review agent name from the list above}"
  # Codex uses agent_type; Claude uses subagent_type.
  prompt: |
    Use the configured plan review model for this agent type.

    You are a review-only plan checker. Read the draft plan.md from disk and review it.
    Do NOT edit files.

    If the selected agent is the normal plan reviewer:
    Read and follow: ${CAT_PLUGIN_ROOT}/agents/common/plan-review-agent.md

    If the selected agent is the strong plan reviewer:
    Read and follow: ${CAT_PLUGIN_ROOT}/agents/common/plan-strong-review-agent.md

    ## Review Task

    1. Read the plan from PLAN_PATH.
    2. Apply the review methodology for this agent type.
    3. Return the required JSON verdict only.

    ## Inputs
    PLAN_PATH: {PLAN_OUTPUT_PATH}
    ISSUE_GOAL: {ISSUE_GOAL}
```

Handle the result:
- If `verdict` is `YES`, display `Plan review passed ({review_agent})`.
- If `verdict` is `NO`, the plan-builder orchestrator applies targeted fixes to the named plan sections, preserves
  unrelated content, writes `PLAN_OUTPUT_PATH`, and repeats this section.

## Output

- **revise mode**: Draft written to `${ISSUE_PATH}/plan.md` in Step 6, reviewed in Step 7 when routing requires it.
