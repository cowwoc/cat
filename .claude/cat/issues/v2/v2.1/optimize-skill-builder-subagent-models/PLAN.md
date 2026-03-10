# Plan: optimize-skill-builder-subagent-models

## Goal
Right-size model assignments for skill-builder-agent subagents: upgrade red-team to opus for deeper adversarial
reasoning, downgrade mechanical/structural subagents (grader, validator, description-tester) to haiku for cost and
speed efficiency.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Red-team opus usage increases token cost per skill-builder run
- **Mitigation:** Opus is only used for the red-team agent which runs bounded iterations (max 10 rounds)

## Files to Modify
- `plugin/skills/skill-builder-agent/first-use.md` — Add `model: "opus"` to red-team Task tool calls (round 1 spawn and round 2+ resume)
- `plugin/agents/skill-grader-agent/SKILL.md` — Change `model: sonnet` to `model: haiku`
- `plugin/agents/skill-validator-agent/SKILL.md` — Change `model: sonnet` to `model: haiku`
- `plugin/agents/description-tester-agent/SKILL.md` — Change `model: sonnet` to `model: haiku`

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update red-team Task tool calls in first-use.md to pass `model: "opus"`
  - Files: `plugin/skills/skill-builder-agent/first-use.md`
- Change model frontmatter from `sonnet` to `haiku` in grader, validator, and description-tester agents
  - Files: `plugin/agents/skill-grader-agent/SKILL.md`, `plugin/agents/skill-validator-agent/SKILL.md`, `plugin/agents/description-tester-agent/SKILL.md`

## Post-conditions
- [ ] Red-team agent spawned with `model: "opus"` in both round 1 and round 2+ Task calls
- [ ] skill-grader-agent SKILL.md has `model: haiku`
- [ ] skill-validator-agent SKILL.md has `model: haiku`
- [ ] description-tester-agent SKILL.md has `model: haiku`
- [ ] skill-comparison-agent and skill-analyzer-agent remain `model: sonnet` (unchanged)
- [ ] All tests pass: `mvn -f client/pom.xml test`
- [ ] E2E: Invoke skill-builder-agent and confirm red-team subagent spawns with opus model override
