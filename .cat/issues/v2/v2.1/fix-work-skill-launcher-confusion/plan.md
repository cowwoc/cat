# Plan: fix-work-skill-launcher-confusion

## Goal

Prevent agents from attempting to execute `work-with-issue` as a `client/bin` launcher by clarifying, in the work orchestration guidance, that `work-with-issue` is a skill-only orchestrator and must be invoked via Skill tool (`cat:work-with-issue`).

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Documentation/skill-instruction change may become inconsistent with runtime conventions
- **Mitigation:** Keep wording aligned with current launcher registry and existing `work-with-issue` skill contract

## Files to Modify

- `client/plugin/skills/common/work/first-use.md`
- `client/plugin/skills/common/work-with-issue/first-use.md` (if needed for consistency)

## Pre-conditions

- [ ] Confirm current launcher registry does not include `work-with-issue`

## Sub-Agent Waves

### Wave 1

- Add explicit guidance in work orchestration instructions that:
  - `work-with-issue` is a skill, not a launcher binary
  - invocation must use Skill tool (`cat:work-with-issue`)
  - direct shell execution of `${CAT_PLUGIN_ROOT}/client/bin/work-with-issue` is invalid

## Post-conditions

- [ ] Skill instructions unambiguously differentiate launcher binaries vs skill-only orchestrators
- [ ] Work guidance includes a concrete valid invocation pattern for `cat:work-with-issue`
- [ ] Tests pass (`mvn -f client/pom.xml verify -e`)
