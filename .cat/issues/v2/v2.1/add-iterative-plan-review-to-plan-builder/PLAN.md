# Plan: add-iterative-plan-review-to-plan-builder

## Goal

Add an iterative completeness-review loop to `cat:plan-builder-agent`. When effort is `medium` or `high`, after
the initial PLAN.md is generated, spawn a Sonnet reviewer subagent that checks whether the plan is comprehensive
enough for a Haiku-level model to implement mechanically (no architectural decisions required). If the reviewer
returns NO, identify the gaps, fix them, and repeat. Cap at 3 iterations.

## Background

The `2.1-sprt-benchmarking-for-skill-builder` issue required 4 manual reviewer subagent cycles to produce a
PLAN.md comprehensive enough for mechanical implementation. This pattern — generate plan, review for gaps, patch,
repeat — is high-value and should be automated inside `cat:plan-builder-agent` so every medium/high-effort issue
benefits from it without manual intervention.

Currently, `cat:plan-builder-agent` generates a PLAN.md once and commits it. There is no verification that the
plan is detailed enough for a Haiku-level subagent to implement without making architectural decisions. This issue
closes that gap by adding a structured review loop with a fresh Sonnet reviewer each iteration.

## Approaches

### A: Inline review loop in first-use.md with a dedicated plan-review-agent.md

- **Risk:** LOW
- **Scope:** 2 files (moderate)
- **Description:** After the initial PLAN.md is written, the plan-builder spawns a `plan-review-agent` subagent
  (defined in `plugin/agents/plan-review-agent.md`) using the Task tool. The reviewer receives the PLAN.md
  content and the issue goal, evaluates comprehensiveness, and returns a YES/NO verdict with a gap list on NO.
  On NO, the plan-builder applies targeted fixes and re-spawns the reviewer. The loop caps at 3 iterations.
  On YES or cap reached, plan-builder proceeds to commit.

### B: Embed review logic directly in the plan-builder with no separate agent file

- **Risk:** MEDIUM
- **Scope:** 1 file (minimal)
- **Description:** Reviewer prompt is inlined in first-use.md. Simpler but harder to maintain; reviewer
  instructions cannot be versioned or reused independently. Rejected.

> **Chosen: Approach A.** Separate agent file makes the reviewer independently maintainable and follows the
> established CAT pattern of `first-use.md` + dedicated `plugin/agents/*.md` for subagent prompts.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Reviewer could flag acceptable design latitude as "missing" (over-strict); loop could converge
  slowly if gap descriptions are vague and the plan-builder patches insufficiently; Sonnet reviewer adds token
  cost per plan-builder invocation.
- **Mitigation:** Reviewer returns specific, actionable gap descriptions (not vague). Plan-builder applies
  targeted fixes (not full rewrites) to converge quickly. Cap at 3 iterations prevents runaway cost. Review
  only triggers for effort `medium`/`high` — effort `low` plans are unaffected.

## Files to Modify

- `plugin/skills/plan-builder-agent/first-use.md` — add the iterative review loop as new steps after the
  initial PLAN.md generation step, before the final commit step
- `plugin/agents/plan-review-agent.md` — new file defining the Sonnet reviewer subagent (model frontmatter,
  review criteria, YES/NO response format, gap list structure)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Create plan-review-agent.md

Create `plugin/agents/plan-review-agent.md` with the following content:

**Frontmatter:**
```yaml
---
model: claude-sonnet-4-6
---
```

**Agent instructions must define:**

1. **Role:** You are a plan completeness reviewer. Your job is to evaluate whether a PLAN.md is detailed enough
   for a Haiku-level model to implement mechanically, without making any architectural decisions.

2. **Pass criterion:** The plan passes if a Haiku-level model could read it and implement every step without
   needing to make a design choice, invent a file path, resolve an ambiguity, or decide between approaches.

3. **Checks to perform (evaluate each explicitly):**
   - Are all file paths that must be created or modified specified exactly (no "some file in X" or "the
     relevant file")?
   - Are all acceptance criteria stated as concrete, verifiable conditions (not vague goals)?
   - Are there unresolved design decisions the implementer would have to make?
   - Are edge cases that affect implementation identified and addressed?
   - Are integration points (other skills, agents, hooks) called out with their exact paths and invocation
     patterns?
   - Is the PLAN.md's Sub-Agent Waves section detailed enough that a subagent knows exactly what to write in
     each file, not just "update X to do Y"?

4. **Response format:** Return a JSON block (and nothing else outside it):
   ```json
   {
     "verdict": "YES" | "NO",
     "gaps": [
       {
         "location": "Sub-Agent Waves § Wave 1",
         "description": "Does not specify the exact model frontmatter value for plan-review-agent.md"
       }
     ]
   }
   ```
   - `verdict`: `"YES"` if the plan is mechanically implementable; `"NO"` if gaps exist.
   - `gaps`: empty array on YES; list of specific, actionable gaps on NO. Each gap must name the exact
     section/location and describe the missing information concretely.

5. **Scope restriction:** Do NOT suggest stylistic improvements, reorganization, or additions beyond what is
   necessary for mechanical implementability. Do NOT flag missing rationale or background as a gap — only
   missing implementation specifics.

**File placement:** `plugin/agents/plan-review-agent.md`

**License header:** Required. HTML comment block after any frontmatter, before the heading:
```markdown
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
```

### Wave 2: Add review loop to first-use.md

Read `plugin/skills/plan-builder-agent/first-use.md` and locate:
1. The step that writes/finalizes the PLAN.md content (the last step before the commit step).
2. The step that commits the PLAN.md.

Insert a new step between those two steps. The new step must follow the existing step numbering (renumber the
commit step if needed).

**New step content (adapt numbering to fit the existing sequence):**

```markdown
### Step N: Iterative Completeness Review (effort: medium or high only)

Skip this step entirely if effort is `low`. Proceed directly to the commit step.

For effort `medium` or `high`, run the following review loop (maximum 3 iterations):

**Iteration setup:**
- `ITERATION` = 1
- `PLAN_CONTENT` = full text of the PLAN.md just written

**Review subagent (spawn fresh each iteration):**

```
Task tool:
  description: "Plan completeness review (iteration {ITERATION})"
  prompt: |
    {content of plugin/agents/plan-review-agent.md — injected verbatim}

    ## Issue Goal
    {ISSUE_GOAL}

    ## PLAN.md to Review
    {PLAN_CONTENT}
```

**On verdict YES:** Exit loop. Proceed to commit step.

**On verdict NO:**
1. Read the `gaps` array from the reviewer's JSON response.
2. For each gap, apply a targeted fix to the relevant section of PLAN.md. Do NOT rewrite sections
   unrelated to the reported gaps. Preserve all existing correct content.
3. Update `PLAN_CONTENT` with the patched PLAN.md.
4. Increment `ITERATION`.
5. If `ITERATION` <= 3: spawn reviewer again with updated `PLAN_CONTENT`.
6. If `ITERATION` > 3 (cap reached): emit the following warning and exit loop:

   ```
   ⚠️  Plan review cap reached (3 iterations). Proceeding with current PLAN.md.
   Remaining gaps: {list gaps from last NO verdict}
   ```

**After loop exits:** Write the final (possibly patched) `PLAN_CONTENT` to disk, overwriting the PLAN.md
file. Then proceed to the commit step.
```

**Important implementation notes for the subagent:**
- The reviewer agent file path is `plugin/agents/plan-review-agent.md`. Read it and inject its content
  verbatim into the Task prompt so the reviewer has its instructions regardless of agent loading state.
- `ISSUE_GOAL` is the goal/description text that was provided to plan-builder when it was invoked.
- The Task tool's `subagent_type` should be `"general-purpose"` (Sonnet model is specified in the agent file's
  frontmatter — but since Task tool spawns a general-purpose agent, use `model: claude-sonnet-4-6` explicitly
  in the prompt preamble if the frontmatter cannot be passed via Task tool).
- Each review iteration spawns a fresh subagent with no memory of prior iterations — only the current
  `PLAN_CONTENT` and the issue goal are passed.
- Targeted fixes mean: edit only the sections identified in the gap `location` field. Do not restructure the
  plan, add new sections not called for by the gaps, or remove existing content.

**Verification after implementing the step:**
- Confirm the new step is numbered correctly (no gaps in numbering, no duplicate step numbers).
- Confirm the commit step (previously last) is renumbered if needed.
- Confirm the effort gate (`low` = skip) is clearly stated at the top of the step.

## Post-conditions

- [ ] `plugin/agents/plan-review-agent.md` exists with `model: claude-sonnet-4-6` in YAML frontmatter
- [ ] `plan-review-agent.md` includes the license header (HTML comment after frontmatter)
- [ ] `plan-review-agent.md` defines the pass criterion ("Haiku can implement mechanically")
- [ ] `plan-review-agent.md` lists all 6 comprehensiveness checks explicitly
- [ ] `plan-review-agent.md` specifies the JSON-only response format with `verdict` and `gaps` fields
- [ ] `plan-review-agent.md` restricts scope: no stylistic suggestions, only implementation-specifics gaps
- [ ] `plugin/skills/plan-builder-agent/first-use.md` contains a new step titled "Iterative Completeness
      Review" (or equivalent) between the PLAN.md finalization step and the commit step
- [ ] The new step in `first-use.md` has an explicit effort gate: skipped entirely when effort is `low`
- [ ] The new step spawns a fresh reviewer subagent each iteration (no shared state between iterations)
- [ ] The new step passes `PLAN_CONTENT` and `ISSUE_GOAL` to each reviewer invocation
- [ ] The new step caps at 3 iterations; emits a warning with remaining gaps if cap is reached
- [ ] On NO verdict, the step applies targeted fixes (not full regeneration) before re-review
- [ ] On YES verdict, the step exits immediately and proceeds to the commit step
- [ ] After the loop, the final (patched) PLAN.md is written to disk before committing
- [ ] Step numbering in `first-use.md` is sequential with no gaps or duplicates after insertion
- [ ] No changes to effort `low` behavior: plan is generated once and committed without review
- [ ] E2E smoke check: invoke `/cat:plan-builder` with effort `medium` on a sample issue; confirm the review
      loop step executes, the reviewer returns a JSON verdict, and the loop terminates on YES or after 3
      iterations
