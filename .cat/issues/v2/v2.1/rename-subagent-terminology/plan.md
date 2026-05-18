# Plan

## Goal

Update CAT terminology across the plugin and codebase so agents created by other agents are called "agents", not "subagents".

## Pre-conditions

- Existing open agent terminology and work-orchestration issues have been reviewed for overlap.

## Post-conditions

- [ ] Agent-facing and user-facing terminology uses "agent" instead of "subagent" where it describes agents created by agents.
- [ ] Historical closed issue records are not modified unless explicitly approved.
- [ ] Behavior remains unchanged.
- [ ] Tests pass with `mvn -f client/pom.xml verify -e`.
- [ ] No regressions in CAT agent orchestration docs, skills, or outputs.

## Impact Notes

- This may overlap with existing open agent-related work, especially `refactor-phase-skill-subagent-isolation`; implementation planning should coordinate file ownership and sequencing before edits.
