# Plan: update-skill-builder-docs

## Goal
Update skill-builder documentation to teach the new `!`command`` silent preprocessing mechanism instead of the OUTPUT
TEMPLATE + handler pattern.

## Satisfies
- Parent: migrate-to-silent-preprocessing

## Files to Modify
- plugin/skills/skill-builder/SKILL.md - Add section on silent preprocessing
- plugin/skills/skill-builder/workflow-output.md - Update output generation guidance
- plugin/concepts/agent-architecture.md - Update if it references handler pattern

## Acceptance Criteria
- [ ] skill-builder SKILL.md documents `!`command`` syntax
- [ ] workflow-output.md updated to prefer preprocessing over templates
- [ ] Examples show how to create skills with silent command execution
- [ ] Old OUTPUT TEMPLATE pattern documented as legacy/deprecated
