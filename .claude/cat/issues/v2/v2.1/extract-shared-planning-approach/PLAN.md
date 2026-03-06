# Plan: extract-shared-planning-approach

## Current State

`delegate-agent/first-use.md` and `work-with-issue-agent/first-use.md` both describe how to plan and delegate work
to subagents, but they document the approach independently. This leads to duplication and drift between the two
files. For example, `delegate-agent` describes a two-stage planning subagent pattern (Stage 1: approach outline,
Stage 2: detailed spec) while `work-with-issue-agent` has its own delegation prompt construction guidance.

## Target State

Common planning/delegation content is extracted into a shared file (e.g., `plugin/concepts/planning-approach.md`)
that both `delegate-agent` and `work-with-issue-agent` reference. Each skill retains only its unique orchestration
logic.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Skill file restructuring; content moves but semantics preserved
- **Mitigation:** Both skills reference the shared file; verify no behavioral regression via E2E

## Files to Modify

- `plugin/concepts/planning-approach.md` (new) ��� shared planning/delegation approach documentation
- `plugin/skills/delegate-agent/first-use.md` ��� extract common sections, reference shared file
- `plugin/skills/work-with-issue-agent/first-use.md` ��� extract common sections, reference shared file

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Identify common content between delegate-agent and work-with-issue-agent:
  - Files: `plugin/skills/delegate-agent/first-use.md`, `plugin/skills/work-with-issue-agent/first-use.md`
  - Compare planning/delegation sections in both files
  - Extract shared concepts: two-stage planning pattern, delegation prompt construction, subagent responsibilities

- Create shared planning approach file:
  - Files: `plugin/concepts/planning-approach.md`
  - Document the canonical planning/delegation approach in one place
  - Include: two-stage planning (Stage 1 approach, Stage 2 spec), delegation prompt patterns,
    main agent vs subagent responsibilities

- Update both skills to reference shared file:
  - Files: `plugin/skills/delegate-agent/first-use.md`, `plugin/skills/work-with-issue-agent/first-use.md`
  - Replace duplicated content with references to `plugin/concepts/planning-approach.md`
  - Keep skill-specific orchestration logic in each skill

- Commit: `refactor: extract shared planning approach from delegate and work-with-issue skills`

## Post-conditions

- [ ] Common planning content exists in a single shared file
- [ ] Both delegate-agent and work-with-issue-agent reference the shared file
- [ ] No duplication of planning approach between the two skills
- [ ] No behavioral regression in either skill
- [ ] E2E: Run /cat:work and verify delegation still works correctly