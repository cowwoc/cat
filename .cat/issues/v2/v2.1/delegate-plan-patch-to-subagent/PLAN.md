# Plan: delegate-plan-patch-to-subagent

## Current State
In `plugin/skills/plan-builder-agent/first-use.md`, the NO-verdict handler in the iterative completeness
review loop (Step 7, lines ~209-215) instructs the main agent to directly apply gap corrections to
`PLAN_CONTENT`:

```
1. Read the `gaps` array from the reviewer's JSON response.
2. For each gap, apply a targeted fix to the relevant section of `PLAN_CONTENT`. Do NOT rewrite sections
   unrelated to the reported gaps. Preserve all existing correct content.
3. Update `PLAN_CONTENT` with the patched content.
```

This means the main agent (which should act as orchestrator only) performs direct text editing of the
plan content, consuming its own context.

## Target State
The iterative completeness review delegates the entire review-and-fix loop to a single subagent.
The main agent passes file paths (`PLAN_OUTPUT_PATH`, `ISSUE_GOAL`) to one general-purpose subagent
which reads the draft from disk, reviews it, fixes gaps directly in the file, iterates until
convergence, and returns only the iteration count (`{"iterations":N}`). No plan content, gap arrays,
or patched text flow back through the main agent's context.

## Parent Requirements
None

## Approaches

### A: Task subagent with general-purpose type (chosen)
- **Risk:** LOW
- **Scope:** 1 file (`plugin/skills/plan-builder-agent/first-use.md`)
- **Description:** Replace the inline patching instructions in the NO-verdict handler with a Task tool
  spawn. The subagent receives the gaps array and current PLAN_CONTENT and returns the corrected plan.
  No new skill file required — a general-purpose Task is sufficient for mechanical text patching.

### B: Dedicated `plan-patcher-agent` skill
- **Risk:** MEDIUM
- **Scope:** 2-3 files (new skill + first-use.md)
- **Description:** Create a new agent skill specifically for plan patching. Rejected: adds infrastructure
  for a mechanical text-editing task that doesn't benefit from a dedicated skill wrapper.

### C: Inline agent invocation (Agent tool instead of Task tool)
- **Risk:** LOW
- **Scope:** 1 file
- **Description:** Use the Agent tool instead of Task tool for patching. Rejected: Task tool is the
  correct tool for delegation within skill workflows; Agent tool is for spawning background subagents.

**Chosen: Approach A** — Task tool spawn is the natural delegation mechanism in CAT skills. No new
infrastructure needed.

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — user-visible PLAN.md output is unchanged; only who applies patches differs
- **Mitigation:** Error handling ensures the loop continues (with un-patched content) if the subagent
  fails, preventing the review loop from hanging or crashing

## Files to Modify
- `plugin/skills/plan-builder-agent/first-use.md` — replace NO-verdict patching instructions (lines
  ~209-215) with Task tool delegation pattern

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Edit `plugin/skills/plan-builder-agent/first-use.md`: replace the NO-verdict handler's inline patching
  instructions with a Task tool delegation block.
  - Files: `plugin/skills/plan-builder-agent/first-use.md`

  **Exact location to edit:** Find the `**On verdict NO:**` block inside `### Step 7: Iterative Completeness
  Review`. The block currently reads (lines ~209-215 in the workspace file):

  ```
  **On verdict NO:**
  Display progress message: `⏳ Plan review iteration {ITERATION}: {gap_count} gaps found, refining...`
  Render the gaps to the user so they understand what is being fixed.
  1. Read the `gaps` array from the reviewer's JSON response.
  2. For each gap, apply a targeted fix to the relevant section of `PLAN_CONTENT`. Do NOT rewrite sections
     unrelated to the reported gaps. Preserve all existing correct content.
  3. Update `PLAN_CONTENT` with the patched content.
  4. Increment `ITERATION`.
  5. If `ITERATION` <= 3: Display progress message `⏳ Spawning review iteration {ITERATION}...` then spawn reviewer again with updated `PLAN_CONTENT`.
  6. If `ITERATION` > 3 (cap reached): Display the following warning message and exit loop:
  ```

  **Replace** steps 1-3 with the following Task tool delegation block (keep steps 4-6 unchanged):

  ```
  **On verdict NO:**
  Display progress message: `⏳ Plan review iteration {ITERATION}: {gap_count} gaps found, refining...`
  Render the gaps to the user so they understand what is being fixed.
  1. Read the `gaps` array from the reviewer's JSON response.
  2. Spawn a patching subagent via the Task tool to apply the gap corrections:

     ```
     Task tool:
       description: "Apply plan gap corrections (iteration {ITERATION})"
       subagent_type: "general-purpose"
       prompt: |
         Use model claude-sonnet-4-6.

         You are a plan patcher. Apply targeted fixes to the PLAN.md content below based on the
         reported gaps. For each gap, fix ONLY the section identified in the gap's "location" field.
         Do NOT rewrite sections unrelated to the reported gaps. Preserve all existing correct content.

         Return the complete corrected PLAN.md text and nothing else — no preamble, no explanation,
         no markdown fences around the output.

         ## Gaps to Fix
         {gaps as JSON array}

         ## Current PLAN.md
         {PLAN_CONTENT}
     ```

  3. Capture the subagent's output as PATCHED_CONTENT.
     - If PATCHED_CONTENT is empty or the subagent failed: display warning
       `⚠️  Patching subagent returned no content (iteration {ITERATION}). Retaining current plan.`
       and keep `PLAN_CONTENT` unchanged.
     - Otherwise: set `PLAN_CONTENT = PATCHED_CONTENT`.
  4. Increment `ITERATION`.
  5. If `ITERATION` <= 3: Display progress message `⏳ Spawning review iteration {ITERATION}...` then spawn reviewer again with updated `PLAN_CONTENT`.
  6. If `ITERATION` > 3 (cap reached): Display the following warning message and exit loop:
  ```

  The remainder of the NO-verdict block (the cap-reached warning text) is unchanged.

## Post-conditions
- [ ] `plugin/skills/plan-builder-agent/first-use.md` Iterative Completeness Review spawns a single
  review-and-fix subagent (verify: the section contains ONE Task tool invocation, not separate
  reviewer + patcher invocations)
- [ ] `plugin/skills/plan-builder-agent/first-use.md` does NOT pass PLAN_CONTENT back to the main
  agent from the review loop (verify: no `PATCHED_CONTENT` variable assignment in the Iterative
  Completeness Review section)
- [ ] All existing tests pass with no regressions (`mvn -f client/pom.xml test` exits 0)
- [ ] User-visible behavior unchanged: PLAN.md output quality is same or better (no functional regression
  in the planning workflow)
- [ ] E2E: Invoke `cat:plan-builder-agent` on a sample issue with effort=medium or high; confirm the review
  loop runs without errors and produces a complete PLAN.md
