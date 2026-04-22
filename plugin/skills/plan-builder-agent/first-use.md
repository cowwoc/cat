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
<cat_agent_id> <curiosity> <mode> <contextPath> [revision-context]
```

| Position | Name | Description |
|----------|------|-------------|
| 1 | cat_agent_id | CAT session identifier |
| 2 | curiosity | Planning depth: `low`, `medium`, or `high` |
| 3 | mode | `revise` |
| 4 | contextPath | Path to a file containing context (see below) |
| 5 | [revision-context] | Optional: Revision instructions for revise mode (e.g., 'add performance tests') |

### Mode: `revise`

Used by `/cat:work-agent` in two contexts: (1) generating execution steps for a lightweight plan (created by `/cat:add-agent`,
containing only goal and post-conditions), and (2) revising an existing plan when requirements change during
implementation. The `contextPath` points to the issue directory (which contains plan.md and index.json).
An additional revision description follows as remaining arguments:

```bash
read CAT_AGENT_ID CURIOSITY MODE ISSUE_PATH REVISION_CONTEXT <<< "$ARGUMENTS"
# REVISION_CONTEXT receives all remaining words after ISSUE_PATH (may contain spaces)
```

The skill reads the existing plan.md, applies the revision, and writes the updated plan.md in place.

> **Design rationale:** Both contexts use `revise` mode because adding execution steps to a lightweight plan.md (which
> already contains goal and post-conditions) is a revision of that existing document, not creation from scratch. The
> plan already exists; the skill revises it to include implementation details.

## When to Use

- **Adding execution steps** (`/cat:work-agent`): Generate full execution steps for a lightweight plan.md created by
  `/cat:add-agent` (which contains only goal and post-conditions, not a full plan from scratch)
- **Mid-work revision** (`/cat:work-agent`): Revise plan.md when requirements change during implementation

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

If the execution subagent needs to make judgment calls about "how" to implement, the plan.md is not detailed enough.
The subagent should only decide "how to write the code", not "what approach to take".

## plan.md Templates

Use the appropriate template based on issue type. Read the issue-plan.md reference for Feature, Bugfix, or Refactor
templates:

```bash
cat "${CLAUDE_PLUGIN_ROOT}/concepts/issue-plan.md"
```

**CRITICAL:** Follow template guidance to separate Execution Jobs/Steps (actions only) from Success Criteria
(measurable outcomes). Do NOT include expected values like "score = 1.0" in Execution sections as this primes
subagents to fabricate results.

## Jobs for Parallel Execution

> See `${CLAUDE_PLUGIN_ROOT}/concepts/work-decomposition.md` for the full execution model, hierarchy, and
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

When writing jobs, size each job's work to stay within 40% of a subagent's context budget.

**Estimation heuristic:**
- Count the number of files the job's work must modify or create
- Assess change complexity: trivial (rename, formatting), medium (logic changes, new methods), high
  (new module, significant refactor)
- A job whose work spans > 5 medium-complexity files or > 10 trivial files is likely to exceed 40% context

**Splitting jobs with too much work:**
If a job's work would exceed the 40% budget, split it into two jobs of roughly equal scope before
writing plan.md. Move the second half of the job's items into a new job immediately after it.
Aim for jobs with equal work scope so each subagent uses approximately the same context fraction.

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

**Main Agent Jobs (optional):** If the issue requires skills that spawn their own subagents (e.g.,
`/cat:instruction-builder-agent`, `/cat:stakeholder-review-agent`), add a `## Main Agent Jobs` section
**above** `## Jobs`. The main agent executes these skills directly before spawning implementation
subagents. Each bullet is a skill invocation:

```markdown
## Main Agent Jobs

- /cat:instruction-builder-agent goal="create or update skill"
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

If research was performed (via `/cat:research-agent` or inline), add a Research Findings section to plan.md after the
Goal/Problem section:

```markdown
## Research Findings
{RESEARCH_FINDINGS}
```

## Workflow

### For Revise Mode (mode=revise)

**Step 1:** Read the existing `${ISSUE_PATH}/plan.md`.

**Step 2:** Read the revision context (`REVISION_CONTEXT` argument) to understand what changed.

**Step 3:** Update plan.md sections affected by the revision.

**Step 4:** Preserve completed work and adjust remaining execution steps.

**Step 5:** Write the revised plan.md content to `PLAN_OUTPUT_PATH` = `${ISSUE_PATH}/plan.md` (delegate the
file write to a subagent).

**Step 6:** Run the Iterative Completeness Review (see section below), passing `PLAN_OUTPUT_PATH` and `ISSUE_GOAL`
(from the existing plan.md `## Goal` section).

**Step 7:** Verify the file at `PLAN_OUTPUT_PATH` exists.

## Iterative Completeness Review

Skip this section entirely if curiosity is `low`. The draft is already written to `PLAN_OUTPUT_PATH`.

For curiosity `medium` or `high`, delegate the entire review-and-fix loop to a single subagent. The subagent
reads the draft from disk, reviews it, fixes gaps directly in the file, and returns only the iteration count.
No plan content flows through the main agent's context.

**Prerequisite:** The draft plan.md must already be written to `PLAN_OUTPUT_PATH` before invoking the
review subagent.

Spawn the review-and-fix subagent:

```
Task tool:
  description: "Plan completeness review"
  subagent_type: "general-purpose"
  prompt: |
    Use model claude-sonnet-4-5.

    You are a plan review-and-fix agent. Read the draft plan.md from disk, review it for
    completeness, fix any gaps, and re-verify. Iterate until the plan passes review.

    Read and follow: ${CLAUDE_PLUGIN_ROOT}/agents/plan-review-agent.md

    ## Review Loop

    1. Read the plan from PLAN_PATH.
    2. Apply the review methodology. Produce a verdict (YES or NO with gaps).
    3. If YES: return success.
    4. If NO: apply targeted fixes directly to the file at PLAN_PATH (fix ONLY sections identified
       in each gap's "location" field, preserve all unrelated content), then return to step 1.

    ## Inputs
    PLAN_PATH: {PLAN_OUTPUT_PATH}
    ISSUE_GOAL: {ISSUE_GOAL}

    ## Return Format
    Return ONLY compact JSON — no other text:
    {"iterations": N}
```

Handle the result:
- Display `✓ Plan review passed ({iterations} iterations)`

## Output

- **revise mode**: Draft written to `${ISSUE_PATH}/plan.md` in Step 5, reviewed in Step 6.
