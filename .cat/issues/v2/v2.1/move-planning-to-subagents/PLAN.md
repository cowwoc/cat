# Plan: move-planning-to-subagents

## Current State

`delegate-agent/first-use.md` lists 6 "Main Agent Responsibilities" for issues with an existing PLAN.md:
make architectural decisions, resolve ambiguities, identify edge cases, write explicit examples, specify verification,
produce comprehensive execution plan. These all burn main agent context and should be handled by planning subagents
instead.

The two-stage planning pattern (Stage 1: approach outline, Stage 2: detailed spec) already exists in delegate-agent
but is only described for issues WITHOUT an existing PLAN.md. For issues WITH an existing PLAN.md, the main agent
is incorrectly told to do the planning work itself.

## Target State

For ALL issue types (with or without existing PLAN.md), planning subagents handle:
- Architectural decisions
- Ambiguity resolution
- Edge case identification
- Example generation
- Verification specification
- Execution plan production

The main agent role is limited to:
- Choosing an approach from Stage 1 options (if multiple exist)
- Passing the selected approach to Stage 2
- Handing the resulting PLAN.md path to the implementation subagent
- Orchestrating skill invocations that require main-agent spawning capability

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None ��� this narrows main agent responsibilities and delegates more to subagents
- **Mitigation:** Planning subagents already have full Read/Grep/Glob tool access

## Files to Modify

- `plugin/skills/delegate-agent/first-use.md` ��� remove main agent planning responsibilities,
  update to use planning subagents for all cases

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Update `plugin/skills/delegate-agent/first-use.md`:
  - Files: `plugin/skills/delegate-agent/first-use.md`
  - Remove the 6-item "Main Agent Responsibilities" list for existing PLAN.md cases
  - Replace with: main agent hands PLAN.md path to a planning subagent which resolves
    ambiguities, identifies edge cases, and produces the detailed execution plan
  - Unify the approach: both new-issue and existing-PLAN.md cases use planning subagents
  - Main agent responsibilities reduced to: orchestration, approach selection, skill pre-invocation

- Commit: `refactor: delegate planning responsibilities from main agent to planning subagents`

## Post-conditions

- [ ] delegate-agent no longer instructs main agent to make architectural decisions or resolve ambiguities
- [ ] delegate-agent describes planning subagents as responsible for all planning work
- [ ] Main agent responsibilities are limited to orchestration (approach selection, skill pre-invocation)
- [ ] No behavioral regression: /cat:work workflow produces same quality outcomes
- [ ] E2E: Run /cat:work and verify planning subagent handles ambiguity resolution