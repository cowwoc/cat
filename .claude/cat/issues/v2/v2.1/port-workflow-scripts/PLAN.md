# Plan: port-workflow-scripts

## Current State
Workflow orchestration scripts handle issue discovery, locking, worktree preparation, and progress
tracking via a mix of bash and Python in `plugin/scripts/`.

## Target State
All workflow orchestration scripts ported to Java classes in the hooks module.

## Satisfies
None - infrastructure/tech debt

## Risk Assessment
- **Risk Level:** HIGH
- **Breaking Changes:** Workflow scripts coordinate multi-step operations; errors cascade
- **Mitigation:** Port incrementally; maintain JSON output contracts; test lock behavior

## Scripts to Port
- `work-prepare.py` (26KB) - Deterministic preparation phase for /cat:work
- `get-available-issues.sh` (27KB) - Find next executable issue with locking
- `issue-lock.sh` (12KB) - File-based issue locking with session tracking
- `check-existing-work.sh` (2KB) - Check for in-progress worktrees
- `create-issue.py` (5KB) - Create issue directory structure and commit
- `get-progress-banner.sh` (8KB) - Progress banner rendering during work
- `load-skill.sh` (1KB) - Skill file loading with preprocessing

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/` - Add/update workflow classes
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/` - Lock management, issue discovery
- `plugin/scripts/` - Remove ported scripts
- `plugin/skills/*/SKILL.md` - Update script invocation paths

## Decomposition

Decomposed into 4 sub-issues (2026-02-11):

| Sub-issue | Scripts | Dependencies |
|-----------|---------|--------------|
| port-lock-and-worktree | issue-lock.sh, check-existing-work.sh | None |
| port-standalone-scripts | create-issue.py, load-skill.sh, get-progress-banner.sh | None |
| port-issue-discovery | get-available-issues.sh, lib/version-utils.sh | port-lock-and-worktree |
| port-work-prepare | work-prepare.py | port-lock-and-worktree, port-issue-discovery |

## Success Criteria
- [ ] All 7 workflow scripts have Java equivalents
- [ ] JSON output contracts preserved exactly
- [ ] File locking behavior identical (including stale lock detection)
- [ ] All tests pass (`mvn -f hooks/pom.xml verify`)
- [ ] No bash/Python subprocess spawning for workflow operations