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
(measurable outcomes). Do NOT include expected values like "score = 1.0" in Execution sections as this primes subagents
to fabricate results.

## Sub-Agent Waves for Parallel Execution

When the issue has clearly independent work that can run simultaneously, use `## Sub-Agent Waves` with `### Wave N`
sections to enable parallel subagent spawning. Use sparingly — only when items genuinely don't depend on each other and
won't modify the same files.

Rules for sub-agent waves:
- Create `## Sub-Agent Waves` section (replaces `## Execution Steps`)
- Each `### Wave N` subsection contains bullet items for parallel execution
- Waves execute sequentially (Wave 1 completes before Wave 2 starts)
- All items within a wave run in parallel
- Waves must not modify the same files (to avoid merge conflicts)
- The last wave is responsible for updating STATE.md

**Main Agent Waves (optional):** If the issue requires skills that spawn their own subagents (e.g.,
`/cat:optimize-doc`, `/cat:compare-docs`, `/cat:stakeholder-review-agent`), add a `## Main Agent Waves` section
**above** `## Sub-Agent Waves`. The main agent executes these skills directly before spawning implementation
subagents. Each bullet is a skill invocation:

```markdown
## Main Agent Waves

- /cat:optimize-doc path/to/file.md
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

1. Read the context JSON from `${CONTEXT_PATH}`
2. Read `${CLAUDE_PLUGIN_ROOT}/concepts/issue-plan.md` for PLAN.md templates
3. If `research_findings` is non-empty, incorporate into a `## Research Findings` section
4. If `impact_notes` is non-empty, incorporate into an `## Impact Notes` section
5. Based on EFFORT level, research approaches:
   - `low`: Skip research, use obvious approach
   - `medium`: Brief exploration of 2-3 alternatives
   - `high`: Deep research, document rejected alternatives with rationale
6. Generate PLAN.md content using the appropriate template for `issue_type`
7. Write PLAN.md to `${CONTEXT_PATH%.json}.plan.md`

### For Mid-Work Revision (mode=revise)

1. Read the existing `${ISSUE_PATH}/PLAN.md`
2. Read the revision context (REVISION_CONTEXT argument) to understand what changed
3. Update PLAN.md sections affected by the revision
4. Preserve completed work and adjust remaining execution steps
5. Write updated PLAN.md to `${ISSUE_PATH}/PLAN.md`

## Output

- **initial mode**: Writes PLAN.md to `${CONTEXT_PATH%.json}.plan.md`. The caller reads this file and passes the
  content to `create-issue`.
- **revise mode**: Writes updated PLAN.md in place at `${ISSUE_PATH}/PLAN.md`.
