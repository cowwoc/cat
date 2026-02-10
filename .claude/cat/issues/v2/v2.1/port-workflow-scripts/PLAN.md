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

## Execution Steps
1. **Port issue-lock.sh:** File locking with Java FileLock API
2. **Port check-existing-work.sh:** Simple worktree existence check
3. **Port create-issue.py:** Directory creation and git commit
4. **Port load-skill.sh:** Skill file loading and env var substitution
5. **Port get-progress-banner.sh:** Progress rendering
6. **Port get-available-issues.sh:** Issue discovery with lock integration
7. **Port work-prepare.py:** Full preparation orchestration
8. **Verify JSON output contracts:** All scripts produce identical JSON
9. **Run tests:** Execute `mvn -f hooks/pom.xml test`

## Success Criteria
- [ ] All 7 workflow scripts have Java equivalents
- [ ] JSON output contracts preserved exactly
- [ ] File locking behavior identical (including stale lock detection)
- [ ] All tests pass (`mvn -f hooks/pom.xml test`)
- [ ] No bash/Python subprocess spawning for workflow operations