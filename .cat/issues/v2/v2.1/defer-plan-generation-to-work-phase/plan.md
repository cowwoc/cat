# Plan

## Goal

Update `/cat:add-agent` to create a lightweight PLAN.md at issue creation time (goal + pre/post-conditions only, no
implementation steps). Move full plan generation (implementation steps) to `cat:work-implement-agent`, where
`cat:plan-builder-agent` is invoked immediately before spawning the implementation subagent.

**Reason:** Implementation details may change between the time an issue is created and when it is actually worked on.
Deferring full plan generation ensures the implementation plan reflects the current codebase state when work begins.

## Pre-conditions

- `/cat:add-agent` currently invokes `cat:plan-builder-agent` in the `issue_create` step to generate a full PLAN.md
  (including implementation steps) at issue creation time
- `cat:work-implement-agent` currently assumes a complete PLAN.md exists when it runs

## Approaches

### A: Inline lightweight plan in add + plan-builder invocation in work-implement

- **Risk:** LOW
- **Scope:** 3 files (plugin/skills/add/first-use.md, plugin/skills/work-implement-agent/first-use.md,
  plugin/skills/plan-builder-agent/first-use.md and SKILL.md)
- **Description:** Replace plan-builder invocation in add/first-use.md with inline lightweight plan generation.
  Add plan-builder invocation in work-implement-agent/first-use.md before spawning the implementation subagent
  (only when plan.md lacks implementation steps).

> Selected: Approach A — minimal, targeted changes; backward compatible with existing full plan.mds.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Existing open issues have full PLAN.md files (with implementation steps). They must continue to work.
- **Mitigation:** `cat:work-implement-agent` checks whether plan.md already contains `## Sub-Agent Waves` or
  `## Execution Steps` before invoking plan-builder-agent. If the section exists, skip the invocation. This
  preserves existing full plans unchanged.

## Files to Modify

- `plugin/skills/add/first-use.md` — Replace plan-builder invocation in `issue_create` step with inline
  lightweight plan.md generation (lines ~948–988)
- `plugin/skills/work-implement-agent/first-use.md` — Add new "Generate implementation steps" section after
  displaying the implementing banner (after Step 3) and before "Read plan.md and Invoke Main Agent Waves"
- `plugin/skills/plan-builder-agent/SKILL.md` — Update description field to remove `/cat:add` reference
- `plugin/skills/plan-builder-agent/first-use.md` — Update "When to Use" section and Mode: `initial` description

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- **Modify `plugin/skills/add/first-use.md`**: Replace the plan-builder invocation in the `issue_create` step
  with lightweight inline plan generation.

  Locate the block starting with `**Generate plan.md via plan-builder-agent:**` (around line 948) through
  the end of the plan-builder invocation (around line 989, just before `**Apply auto-detected skill dependency
  updates...`).

  Replace with `**Generate lightweight plan.md:**` block that:
  1. Creates a unique temporary plan.md file using `planTempFile=$(mktemp --suffix=.md)` (multi-instance
     safe — avoids name collisions when multiple `/cat:add` invocations run concurrently). Writes content:
     - `# Plan` header
     - `## Goal` section containing `${ISSUE_DESCRIPTION}` verbatim
     - `## Post-conditions` section with POSTCONDITIONS items as a checklist

     Use this bash approach (the agent writes the file using the Write tool, not a heredoc, to avoid
     quoting issues with variable content):

     ```
     First: planTempFile=$(mktemp --suffix=.md)
     Then the agent writes the lightweight plan.md to ${planTempFile} using the Write tool
     (or Bash with printf/echo) with the following structure:

     # Plan

     ## Goal

     ${ISSUE_DESCRIPTION}

     ## Post-conditions

     - [ ] ${postcondition_1}
     - [ ] ${postcondition_2}
     ...
     ```

  2. Passes `${planTempFile}` to `create-issue` via `planFile` parameter — same as before, but
     pointing to the new lightweight file instead of the plan-builder output.
  3. Removes the PLAN_CONTEXT JSON file creation step.
  4. Removes the `cat:plan-builder-agent` Skill tool invocation.
  5. Keeps the `create-issue` bash call structure identical (only the `planFile` value changes).

  Files: `plugin/skills/add/first-use.md`

- **Modify `plugin/skills/work-implement-agent/first-use.md`**: Insert a new "### Generate Implementation Steps"
  section between the "Step 3 (Implementing Banner)" content and the "### Read plan.md and Invoke Main Agent
  Waves" section (around line 153).

  The new section:

  ```markdown
  ### Generate Implementation Steps

  Before reading Main Agent Waves, check whether plan.md already contains implementation steps:

  ```bash
  PLAN_MD="${ISSUE_PATH}/plan.md" && \
  grep -qE '^## (Sub-Agent Waves|Execution Steps)' "${PLAN_MD}" && \
  echo "hasSteps=true" || echo "hasSteps=false"
  ```

  **If `hasSteps=false`** (lightweight plan created by `/cat:add`): invoke `cat:plan-builder-agent` in
  revise mode to generate full implementation steps before spawning the implementation subagent:

  1. Read EFFORT from config:
     ```bash
     CONFIG=$("${CLAUDE_PLUGIN_DATA}/client/bin/get-config-output" effective)
     EFFORT=$(echo "$CONFIG" | grep -o '"effort"[[:space:]]*:[[:space:]]*"[^"]*"' \
       | sed 's/.*"effort"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
     ```

  2. Invoke plan-builder-agent to add implementation steps:
     ```
     Skill tool:
       skill: "cat:plan-builder-agent"
       args: "${CAT_AGENT_ID} ${EFFORT} revise ${ISSUE_PATH} Generate full implementation steps for
     this lightweight plan. Add Sub-Agent Waves or Execution Steps section with detailed step-by-step
     implementation guidance."
     ```

  3. After plan-builder-agent returns, re-read the updated plan.md in subsequent steps.

  **If `hasSteps=true`** (full plan with implementation steps): skip plan-builder-agent invocation.
  Proceed directly to "Read plan.md and Invoke Main Agent Waves".
  ```

  Files: `plugin/skills/work-implement-agent/first-use.md`

- **Modify `plugin/skills/plan-builder-agent/SKILL.md`**: Update the `description` field:
  - Old: `Invoked by /cat:add for initial plans and by /cat:work for mid-work revisions.`
  - New: `Invoked by /cat:work to generate full implementation steps before spawning the implementation
    subagent, and for mid-work revisions when requirements change during implementation.`

  Files: `plugin/skills/plan-builder-agent/SKILL.md`

- **Modify `plugin/skills/plan-builder-agent/first-use.md`**: Update two sections:

  Section 1 — "When to Use" (around line 61-64):
  - Old line 63: `- **Initial planning** (\`/cat:add\`): Generate plan.md from issue description and context`
  - New line 63: `- **Initial implementation** (\`/cat:work\`): Generate full implementation steps from a
    lightweight plan.md created by \`/cat:add\` (which contains only goal and post-conditions)`

  Section 2 — "### Mode: `initial`" (around line 33-48):
  - Old line 35: `Used by \`/cat:add\`. The \`contextPath\` points to a temporary JSON file containing:`
  - New line 35: `**Deprecated.** This mode was previously used by \`/cat:add\`. The \`revise\` mode is now
    used by \`/cat:work-implement-agent\` to generate full implementation steps from lightweight plan.mds.
    The \`contextPath\` points to a temporary JSON file containing:`

  Files: `plugin/skills/plan-builder-agent/first-use.md`

- **Commit all three file changes in one commit** with message:
  `feature: defer plan generation from add to work-implement`

  Also include index.json closure in this same commit:
  - Set status to `closed` and progress to `100%` in the issue's index.json

## Post-conditions

1. `/cat:add-agent` creates a lightweight PLAN.md containing only: goal description, pre-conditions, and
   post-conditions — no implementation steps or approach sections
2. `cat:work-implement-agent` invokes `cat:plan-builder-agent` at the start, before spawning the implementation
   subagent, to generate the full implementation plan
3. All tests pass (no regressions)
4. E2E verification: create a new issue via `/cat:add` and confirm its PLAN.md has no implementation steps; then
   start `/cat:work` on that issue and confirm `cat:plan-builder-agent` runs and populates implementation steps
   before the implementation subagent spawns
5. Existing open issues with full PLAN.md files (containing implementation steps) are unmodified by this change
6. `cat:plan-builder-agent` description updated to reflect it is now invoked by `cat:work-implement-agent` before
   spawning the implementation subagent (not by `/cat:add` for initial plans)
