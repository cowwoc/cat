# Plan: verify-pre-conditions-on-work-start

## Goal

Verify pre-conditions in `cat:work-prepare` before starting work on an issue, mirroring how
`cat:verify-implementation` checks post-conditions before closing.

## Satisfies

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Pre-conditions are currently free-form markdown with no consistent format
- **Mitigation:** Match the approach used by `cat:verify-implementation` for post-conditions

## Files to Modify

- `plugin/skills/work-prepare-agent/SKILL.md` — add pre-condition verification step
- `plugin/skills/work-implement-agent/SKILL.md` — add pre-condition check before implementation begins (if applicable)

## Pre-conditions

- [ ] Pre-conditions section format in PLAN.md is understood
- [ ] `cat:verify-implementation` source reviewed to understand the post-condition verification approach

## Post-conditions

- [ ] `cat:work-prepare` reads and evaluates pre-conditions from PLAN.md before starting work
- [ ] Agent is blocked or warned if any pre-condition is not satisfied
- [ ] Behavior mirrors `cat:verify-implementation` (same evaluation approach, opposite direction)
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: Start work on an issue with an explicitly unmet pre-condition and confirm the agent reports it

## Execution Waves

### Wave 1 — Research

1. Read `plugin/skills/work-prepare-agent/SKILL.md` to understand where to insert the check
2. Read `plugin/skills/work-confirm-agent/SKILL.md` and `plugin/skills/verify-implementation-agent/SKILL.md` to understand how post-condition verification works
3. Identify the pre-conditions format used in existing PLAN.md files

### Wave 2 — Implementation

1. Add a pre-condition verification step to `cat:work-prepare` that:
   - Reads pre-conditions from PLAN.md
   - Evaluates each condition
   - Reports any unmet conditions
   - Blocks or warns as appropriate
2. Verify: Works correctly on an issue with pre-conditions

### Wave 3 — Verification

1. Run all tests: `mvn -f client/pom.xml test`
2. E2E test: work on an issue with unmet pre-conditions and confirm they are reported
