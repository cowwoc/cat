# Plan

## Goal

Rename all stale `/cat:X` references to `/cat:X-agent` across the codebase. Many skill commands were
renamed to add the `-agent` suffix, but references in plugin skills, client Java source, tests, docs,
and config files still use the old names.

The following 27 renames are required (old → new):

| Old | New |
|-----|-----|
| `/cat:add` | `/cat:add-agent` |
| `/cat:collect-results` | `/cat:collect-results-agent` |
| `/cat:decompose-issue` | `/cat:decompose-issue-agent` |
| `/cat:empirical-test` | `/cat:empirical-test-agent` |
| `/cat:get-diff` | `/cat:get-diff-agent` |
| `/cat:get-history` | `/cat:get-history-agent` |
| `/cat:get-output` | `/cat:get-output-agent` |
| `/cat:git-merge-linear` | `/cat:git-merge-linear-agent` |
| `/cat:git-rebase` | `/cat:git-rebase-agent` |
| `/cat:git-rewrite-history` | `/cat:git-rewrite-history-agent` |
| `/cat:git-squash` | `/cat:git-squash-agent` |
| `/cat:instruction-builder` | `/cat:instruction-builder-agent` |
| `/cat:load-skill` | `/cat:load-skill-agent` |
| `/cat:plan-builder` | `/cat:plan-builder-agent` |
| `/cat:recover-from-drift` | `/cat:recover-from-drift-agent` |
| `/cat:register-hook` | `/cat:register-hook-agent` |
| `/cat:remove` | `/cat:remove-agent` |
| `/cat:research` | `/cat:research-agent` |
| `/cat:retrospective` | `/cat:retrospective-agent` |
| `/cat:safe-rm` | `/cat:safe-rm-agent` |
| `/cat:stakeholder-review` | `/cat:stakeholder-review-agent` |
| `/cat:tdd-implementation` | `/cat:tdd-implementation-agent` |
| `/cat:token-report` | `/cat:token-report-agent` |
| `/cat:verify-implementation` | `/cat:verify-implementation-agent` |
| `/cat:work` | `/cat:work-agent` |
| `/cat:work-complete` | `/cat:work-complete-agent` |
| `/cat:work-with-issue` | `/cat:work-with-issue-agent` |

**Exclusions:** Do not rename references that are intentionally using the old name as a detection
example in tests (e.g., test strings that verify the `/cat:[a-z]` pattern detects slash commands).
Also skip historical changelog entries and `.cat/issues/` files (closed issues are historical records).

## Pre-conditions

(none)

## Post-conditions

- [ ] All 27 stale `/cat:X` references updated to `/cat:X-agent` in plugin skills, client Java source, docs, tests, CLAUDE.md, AGENTS.md, and `.claude/rules/` files
- [ ] Historical changelog entries and closed issue files left unchanged
- [ ] Tests pass (`mvn -f client/pom.xml verify -e`)
- [ ] No behavioral regressions
