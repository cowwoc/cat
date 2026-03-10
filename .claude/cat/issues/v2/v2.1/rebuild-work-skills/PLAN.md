# Plan: rebuild-work-skills

## Current State
The 9 work-related skills (work, work-agent, work-prepare-agent, work-implement-agent, work-confirm-agent,
work-review-agent, work-merge-agent, work-with-issue-agent, work-complete-agent) have never been run through
the full skill-builder process. Their SKILL.md and first-use.md files lack benchmark validation and adversarial
TDD hardening, leaving potential loopholes and unclear instruction boundaries.

## Target State
All 9 work-related skills processed through the full skill-builder workflow (design subagent review →
benchmark evaluation → adversarial TDD convergence), with each skill's SKILL.md and first-use.md hardened
and committed individually. Trigger conditions and procedure intent preserved; loopholes closed.

## Parent Requirements
None

## Approaches

### A: Sequential per-skill, directory-level invocation (chosen)
- **Risk:** LOW
- **Scope:** 18 files (9 directories, each containing SKILL.md + first-use.md)
- **Description:** Invoke `/cat:skill-builder` on each skill directory sequentially. Skill-builder's batch
  mode processes both SKILL.md and first-use.md per directory, committing each after convergence.
  One skill's failure does not block others; restart from the failed skill.

### B: Full parallel (all 9 simultaneously)
- **Risk:** HIGH
- **Scope:** 18 files
- **Description:** Invoke all 9 skill-builder runs in parallel. Each run spawns design + red-team + blue-team
  subagents (~30+ concurrent subagents total). Rejected: extreme token cost and context window exhaustion risk.

### C: Grouped waves (leaf agents first, orchestrators second)
- **Risk:** MEDIUM
- **Scope:** 18 files
- **Description:** Wave 1: 6 leaf agents (prepare/implement/confirm/review/merge/complete). Wave 2: 3
  orchestrators (with-issue, work-agent, work). Rejected: skill files are independent — no semantic ordering
  benefit justifies the added complexity. Sequential is equally correct and simpler.

**Chosen: Approach A** — Sequential minimizes token risk, enables easy restart-from-failure, and aligns with
skill-builder's documented in-place hardening batch mode.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Skill redesign may alter trigger phrasing or step ordering; must verify semantic
  equivalence of trigger descriptions and procedure intent after each run
- **Mitigation:** Compare final vs original skill content for trigger condition drift; run /cat:work smoke
  test after all skills are committed

## Files to Modify
- plugin/skills/work/SKILL.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work/first-use.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-agent/SKILL.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-agent/first-use.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-prepare-agent/SKILL.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-prepare-agent/first-use.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-implement-agent/SKILL.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-implement-agent/first-use.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-confirm-agent/SKILL.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-confirm-agent/first-use.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-review-agent/SKILL.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-review-agent/first-use.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-merge-agent/SKILL.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-merge-agent/first-use.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-with-issue-agent/SKILL.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-with-issue-agent/first-use.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-complete-agent/SKILL.md - full skill-builder redesign + adversarial TDD hardening
- plugin/skills/work-complete-agent/first-use.md - full skill-builder redesign + adversarial TDD hardening

## Pre-conditions
- [ ] All dependent issues are closed

## Main Agent Waves

- /cat:skill-builder 7f8aaadc-7586-4890-9efb-32a053627a02 plugin/skills/work-prepare-agent/SKILL.md
- /cat:skill-builder 7f8aaadc-7586-4890-9efb-32a053627a02 plugin/skills/work-implement-agent/SKILL.md
- /cat:skill-builder 7f8aaadc-7586-4890-9efb-32a053627a02 plugin/skills/work-confirm-agent/SKILL.md
- /cat:skill-builder 7f8aaadc-7586-4890-9efb-32a053627a02 plugin/skills/work-review-agent/SKILL.md
- /cat:skill-builder 7f8aaadc-7586-4890-9efb-32a053627a02 plugin/skills/work-merge-agent/SKILL.md
- /cat:skill-builder 7f8aaadc-7586-4890-9efb-32a053627a02 plugin/skills/work-complete-agent/SKILL.md
- /cat:skill-builder 7f8aaadc-7586-4890-9efb-32a053627a02 plugin/skills/work-with-issue-agent/SKILL.md
- /cat:skill-builder 7f8aaadc-7586-4890-9efb-32a053627a02 plugin/skills/work-agent/SKILL.md
- /cat:skill-builder 7f8aaadc-7586-4890-9efb-32a053627a02 plugin/skills/work/SKILL.md

## Post-conditions
- [ ] All 18 files (9 SKILL.md + 9 first-use.md) processed through full skill-builder workflow
- [ ] Each file committed individually after adversarial TDD convergence (or 10-round cap)
- [ ] Trigger descriptions in frontmatter semantically preserved (skills activate in same scenarios as before)
- [ ] Procedure intent preserved (same steps, same outcomes, no new required inputs)
- [ ] E2E: Run /cat:work on an existing open issue and confirm the workflow completes without regression
