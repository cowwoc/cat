# Plan: extract-shared-planning-approach

## Current State

Planning logic is scattered across three skills:

- **`add/first-use.md`** contains effort-based planning depth, PLAN.md comprehensiveness requirements, execution wave
  guidance, and batch execution checks — all inline within the add workflow
- **`delegate-agent/first-use.md`** contains two-stage planning pattern, model selection, hook inheritance block,
  main agent responsibilities, execution plan format, prompt completeness checklist, and batch orchestration
- **`work-with-issue-agent/first-use.md`** contains delegation prompt construction guidance

This duplication and scattering causes:
1. Planning logic drifts between files
2. No single authoritative source for how to build a comprehensive PLAN.md
3. `delegate-agent` mixes planning with batch orchestration with subagent principles — all of which are covered
   better elsewhere or unnecessary

## Target State

1. A new `cat:plan-builder-agent` skill owns all PLAN.md generation logic (effort-based depth, approach research,
   execution plan format, comprehensiveness requirements)
2. `/cat:add` invokes `plan-builder-agent` to generate initial PLAN.md
3. `/cat:work` can invoke `plan-builder-agent` for mid-work PLAN.md revisions when requirements change
4. `delegate-agent` is deleted entirely — its content is either moved or unnecessary:
   - Model selection → `subagent-delegation.md`
   - Hook inheritance block → deleted (rules injection handles this)
   - Batch orchestration → deleted (agent already knows how to spawn parallel subagents)
   - Planning content → `plan-builder-agent`
   - Subagent principles → already in `subagent-delegation.md`
5. Planning-related instructions removed from `work-with-issue-agent` (only a hook for mid-work revision remains)

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** HIGH
- **Breaking Changes:** Deleting `delegate-agent` removes a user-invocable skill; `optimize-doc` references it
- **Mitigation:** Update `optimize-doc` to remove delegate references; verify `/cat:work` still functions after
  removing planning content from `work-with-issue-agent`

## Files to Modify

- `plugin/skills/plan-builder-agent/SKILL.md` (new) — skill definition
- `plugin/skills/plan-builder-agent/first-use.md` (new) — planning logic extracted from add and delegate
- `plugin/skills/add/first-use.md` — replace inline planning logic with plan-builder invocation
- `plugin/skills/add-agent/SKILL.md` — add Skill to allowed-tools if needed
- `plugin/skills/work-with-issue-agent/first-use.md` — remove planning content, add mid-work revision hook
- `plugin/skills/delegate-agent/` (delete) — entire skill directory
- `plugin/concepts/subagent-delegation.md` — add model selection table from delegate-agent
- `plugin/skills/optimize-doc/first-use.md` — remove `/cat:delegate-agent` references
- `plugin/concepts/planning-approach.md` (delete if exists) — replaced by plan-builder-agent

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Create `plan-builder-agent` skill:
  - Files: `plugin/skills/plan-builder-agent/SKILL.md`, `plugin/skills/plan-builder-agent/first-use.md`
  - Extract from `add/first-use.md`: effort-based planning depth, PLAN.md comprehensiveness requirements,
    execution wave guidance, batch execution check, PLAN.md templates
  - Extract from `delegate-agent/first-use.md`: two-stage planning pattern, execution plan format,
    prompt completeness checklist
  - The skill should accept an issue description and effort level, and produce a complete PLAN.md

- Move model selection to `subagent-delegation.md`:
  - Files: `plugin/concepts/subagent-delegation.md`
  - Move the model selection table and opus guidance from `delegate-agent/first-use.md`

- Commit: `feature: create plan-builder-agent skill with extracted planning logic`

### Wave 2

- Update `add` skill to invoke plan-builder:
  - Files: `plugin/skills/add/first-use.md`, `plugin/skills/add-agent/SKILL.md`
  - Replace inline planning logic (effort-based depth, comprehensiveness, wave guidance) with
    invocation of `plan-builder-agent`
  - Keep issue creation workflow (version selection, naming, STATE.md generation)

- Remove planning content from `work-with-issue-agent`:
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`
  - Remove "Delegation Prompt Construction" section
  - Add a brief mid-work revision hook: "If PLAN.md needs revision mid-work, invoke
    `cat:plan-builder-agent` to update it, then resume execution"

- Update `optimize-doc` to remove delegate references:
  - Files: `plugin/skills/optimize-doc/first-use.md`
  - Remove references to `/cat:delegate-agent` for batch operations
  - Simplify batch section (agent spawns parallel subagents directly)

- Delete `delegate-agent`:
  - Files: `plugin/skills/delegate-agent/` (entire directory)
  - Verify no other skills reference it beyond optimize-doc

- Delete `planning-approach.md` if it exists from prior implementation attempt:
  - Files: `plugin/concepts/planning-approach.md`

- Commit: `refactor: integrate plan-builder-agent, delete delegate-agent`

## Post-conditions

- [ ] `plan-builder-agent` skill exists and contains all planning logic
- [ ] `add` skill invokes `plan-builder-agent` instead of inline planning
- [ ] `work-with-issue-agent` has no planning content except mid-work revision hook
- [ ] `delegate-agent` is fully deleted
- [ ] `subagent-delegation.md` contains model selection guidance
- [ ] `optimize-doc` has no references to `delegate-agent`
- [ ] No other files reference `delegate-agent`
- [ ] E2E: Run `/cat:add` with a test issue and verify PLAN.md is generated correctly
- [ ] E2E: Run `/cat:work` and verify implementation subagent receives proper PLAN.md
