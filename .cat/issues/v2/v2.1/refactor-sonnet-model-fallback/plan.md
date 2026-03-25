# Plan

## Goal

Move `model: sonnet` frontmatter out of 31 skill SKILL.md files into a centralized `plugin/rules/skill-models.md`
rule file. The rule instructs the agent to prefer Sonnet first, then fall back to Opus if Sonnet is rate-limited.
This enables graceful degradation when Sonnet quota is exhausted without requiring per-skill config changes.

Affected skills (currently `model: sonnet`):
- `cat:add-agent`
- `cat:decompose-issue-agent`
- `cat:empirical-test-agent`
- `cat:git-merge-linear-agent`
- `cat:git-rebase-agent`
- `cat:git-rewrite-history-agent`
- `cat:git-squash-agent`
- `cat:init`
- `cat:instruction-builder-agent`
- `cat:instruction-organizer-agent`
- `cat:learn`
- `cat:learn-agent`
- `cat:optimize-execution`
- `cat:optimize-execution-agent`
- `cat:plan-builder-agent`
- `cat:rebase-impact-agent`
- `cat:recover-from-drift-agent`
- `cat:research-agent`
- `cat:retrospective-agent`
- `cat:safe-remove-code-agent`
- `cat:skill-comparison-agent`
- `cat:stakeholder-review-agent`
- `cat:tdd-implementation-agent`
- `cat:verify-implementation-agent`
- `cat:work-agent`
- `cat:work-confirm-agent`
- `cat:work-implement-agent`
- `cat:work-merge-agent`
- `cat:work-prepare-agent`
- `cat:work-review-agent`
- `cat:work-with-issue-agent`

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior unchanged (skills still run on Sonnet by default)
- [ ] Tests passing
- [ ] Code quality improved (model selection centralized instead of scattered across 31 files)
- [ ] No SKILL.md file under `plugin/skills/` contains `model: sonnet` in frontmatter
- [ ] `plugin/rules/skill-models.md` exists and lists all 31 previously-Sonnet skills with preference order [sonnet, opus]
- [ ] E2E verification: invoke a skill that previously had `model: sonnet` and confirm the agent selects the correct model based on `plugin/rules/skill-models.md`
