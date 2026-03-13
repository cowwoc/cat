<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan Builder

## Purpose

Build or revise a comprehensive PLAN.md for a CAT issue. This skill centralizes all planning logic so that
PLAN.md generation is consistent regardless of which workflow invokes it.

## Arguments

Positional space-separated arguments:

```
<cat_agent_id> <effort> <mode> <context_path>
```

| Position | Name | Description |
|----------|------|-------------|
| 1 | cat_agent_id | CAT session identifier |
| 2 | effort | Planning depth: `low`, `medium`, or `high` |
| 3 | mode | `initial` or `revise` |
| 4 | context_path | Path to a file containing context (see below) |

Parse from ARGUMENTS:
```bash
read CAT_AGENT_ID EFFORT MODE CONTEXT_PATH <<< "$ARGUMENTS"
```

### Mode: `initial`

Used by `/cat:add`. The `context_path` points to a temporary JSON file containing:

```json
{
  "issue_type": "feature|bugfix|refactor|performance",
  "description": "The full issue description",
  "postconditions": ["criterion 1", "criterion 2"],
  "research_findings": "optional research text or empty string",
  "impact_notes": "optional impact notes or empty string"
}
```

The skill reads this file, generates PLAN.md content, and writes it to `${context_path%.json}.plan.md`
(same directory, replacing `.json` extension with `.plan.md`). The caller reads the output file.

### Mode: `revise`

Used by `/cat:work`. The `context_path` points to the issue directory (which contains PLAN.md and STATE.md).
An additional revision description follows as remaining arguments:

```bash
read CAT_AGENT_ID EFFORT MODE ISSUE_PATH REVISION_CONTEXT <<< "$ARGUMENTS"
```

The skill reads the existing PLAN.md, applies the revision, and writes the updated PLAN.md in place.

## When to Use

- **Initial planning** (`/cat:add`): Generate PLAN.md from issue description and context
- **Mid-work revision** (`/cat:work`): Revise PLAN.md when requirements change during implementation

## Effort-Based Planning Depth

Apply the following depth to PLAN.md content based on `$EFFORT`:

- `low`: Generate a concise plan. Assume the obvious approach. Skip alternative analysis. List only essential steps
  and post-conditions.
- `medium`: Explore two or three alternative approaches before settling on one. Note key trade-offs in a brief
  section. Execution steps should cover non-obvious edge cases.
- `high`: Perform deep research on the problem space. Document the reasoning for the chosen approach and explicitly
  list rejected alternatives with rationale. Execution steps must cover all known edge cases and failure modes.

## PLAN.md Comprehensiveness

The PLAN.md must be comprehensive enough for a haiku-level model to implement mechanically without making architectural
decisions. Include:
- Exact file paths to create/modify
- Specific code patterns or formats to use
- Complete lists (all files, all references to update, all post-conditions)
- Research findings that inform implementation decisions

If the execution subagent needs to make judgment calls about "how" to implement, the PLAN.md is not detailed enough.
The subagent should only decide "how to write the code", not "what approach to take".

## PLAN.md Templates

Use the appropriate template based on issue type. Read the issue-plan.md reference for Feature, Bugfix, or Refactor
templates:

```bash
cat "${CLAUDE_PLUGIN_ROOT}/concepts/issue-plan.md"
```

**CRITICAL:** Follow template guidance to separate Execution Waves/Steps (actions only) from Success Criteria
(measurable outcomes). Do NOT include expected values like "score = 1.0" in Execution sections as this primes
subagents to fabricate results.

## Sub-Agent Waves for Parallel Execution

When the issue has clearly independent work that can run simultaneously, use `## Sub-Agent Waves` with `### Wave N`
sections to enable parallel subagent spawning. Use sparingly — only when items genuinely don't depend on each other
and won't modify the same files.

Rules for sub-agent waves:
- Create `## Sub-Agent Waves` section (replaces `## Execution Steps`)
- Each `### Wave N` subsection contains bullet items for parallel execution
- Waves execute sequentially (Wave 1 completes before Wave 2 starts)
- All items within a wave run in parallel
- Waves must not modify the same files (to avoid merge conflicts)
- The last wave is responsible for updating STATE.md

**Main Agent Waves (optional):** If the issue requires skills that spawn their own subagents (e.g.,
`/cat:instruction-builder-agent`, `/cat:stakeholder-review-agent`), add a `## Main Agent Waves` section
**above** `## Sub-Agent Waves`. The main agent executes these skills directly before spawning implementation
subagents. Each bullet is a skill invocation:

```markdown
## Main Agent Waves

- /cat:instruction-builder-agent goal="create or update skill"
```

Omit `## Main Agent Waves` entirely when the issue has no pre-delegation skills.

Example valid sub-agent wave structure (independent modules):

```markdown
## Sub-Agent Waves

### Wave 1
- Implement parser module
- Add parser tests

### Wave 2
- Implement formatter module
- Add formatter tests
- Run full test suite
```

Do NOT use multiple waves if items share files or if the sequential dependency is unclear. In such cases, use a single
`## Sub-Agent Waves` / `### Wave 1` section or revert to `## Execution Steps` for sequential execution.

## Research Findings

If research was performed (via `/cat:research-agent` or inline), add a Research Findings section to PLAN.md after the
Goal/Problem section:

```markdown
## Research Findings
{RESEARCH_FINDINGS}
```

## Workflow

### For Initial Planning (mode=initial)

**Step 1:** Read the context JSON from `${CONTEXT_PATH}`.

**Step 2:** Read `${CLAUDE_PLUGIN_ROOT}/concepts/issue-plan.md` for PLAN.md templates.

**Step 3:** If `research_findings` is non-empty, incorporate into a `## Research Findings` section.

**Step 4:** If `impact_notes` is non-empty, incorporate into an `## Impact Notes` section.

**Step 5:** Based on EFFORT level, research approaches:
- `low`: Skip research, use obvious approach
- `medium`: Brief exploration of 2-3 alternatives
- `high`: Deep research, document rejected alternatives with rationale

**Step 6:** Generate PLAN.md content using the appropriate template for `issue_type`.

**Step 7:** Write the draft PLAN.md content to `PLAN_OUTPUT_PATH` = `${CONTEXT_PATH%.json}.plan.md` (delegate the
file write to a subagent).

**Step 8:** Run the Iterative Completeness Review (see section below), passing `PLAN_OUTPUT_PATH` and `ISSUE_GOAL`.

**Step 9:** Verify the file at `PLAN_OUTPUT_PATH` exists.

### For Mid-Work Revision (mode=revise)

**Step 1:** Read the existing `${ISSUE_PATH}/PLAN.md`.

**Step 2:** Read the revision context (`REVISION_CONTEXT` argument) to understand what changed.

**Step 3:** Update PLAN.md sections affected by the revision.

**Step 4:** Preserve completed work and adjust remaining execution steps.

**Step 5:** Write the revised PLAN.md content to `PLAN_OUTPUT_PATH` = `${ISSUE_PATH}/PLAN.md` (delegate the
file write to a subagent).

**Step 6:** Run the Iterative Completeness Review (see section below), passing `PLAN_OUTPUT_PATH` and `ISSUE_GOAL`
(from the existing PLAN.md `## Goal` section).

**Step 7:** Verify the file at `PLAN_OUTPUT_PATH` exists.

## Iterative Completeness Review

Skip this section entirely if effort is `low`. The draft is already written to `PLAN_OUTPUT_PATH`.

For effort `medium` or `high`, delegate the entire review-and-fix loop to a single subagent. The subagent
reads the draft from disk, reviews it, fixes gaps directly in the file, and returns only the iteration count.
No plan content flows through the main agent's context.

**Prerequisite:** The draft PLAN.md must already be written to `PLAN_OUTPUT_PATH` before invoking the
review subagent.

Spawn the review-and-fix subagent:

```
Task tool:
  description: "Plan completeness review"
  subagent_type: "general-purpose"
  prompt: |
    Use model claude-sonnet-4-6.

    You are a plan review-and-fix agent. Read the draft PLAN.md from disk, review it for
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

- **initial mode**: Draft written to `${CONTEXT_PATH%.json}.plan.md` in Step 7, reviewed in Step 8. The caller
  reads this file and passes the content to `create-issue`.
- **revise mode**: Draft written to `${ISSUE_PATH}/PLAN.md` in Step 5, reviewed in Step 6.
