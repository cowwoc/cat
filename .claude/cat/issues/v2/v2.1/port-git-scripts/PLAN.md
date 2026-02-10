# Plan: port-git-scripts

## Current State
Git operations (merge, squash, commit) are implemented as bash scripts in `plugin/scripts/`.
The hooks module already has `GitCommands.java` and `ProcessRunner.java` utilities.

## Target State
All git operation scripts ported to Java classes using `ProcessRunner` for git CLI invocation,
eliminating bash subprocess chaining.

## Satisfies
None - infrastructure/tech debt

## Risk Assessment
- **Risk Level:** HIGH
- **Breaking Changes:** Git operations are critical - any behavioral change risks data loss
- **Mitigation:** Extensive testing with real git repos; preserve exact exit codes and error messages

## Scripts to Port
- `git-merge-linear-optimized.sh` (7KB) - Linear merge with backup and conflict recovery
- `git-squash-optimized.sh` (9KB) - Commit squashing by type
- `merge-and-cleanup.sh` (12KB) - Post-merge worktree cleanup
- `write-and-commit.sh` (4KB) - Atomic file write + git commit

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/GitCommands.java` - Add merge/squash methods
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/` - New classes for complex git operations
- `plugin/scripts/` - Remove ported scripts
- `plugin/skills/*/SKILL.md` - Update script invocation paths

## Execution Steps
1. **Port write-and-commit.sh:** Simplest script - atomic write + commit in Java
2. **Port git-merge-linear-optimized.sh:** Linear merge with backup branch creation
3. **Port git-squash-optimized.sh:** Commit squashing with type-based grouping
4. **Port merge-and-cleanup.sh:** Worktree removal and branch cleanup
5. **Update skill references:** Modify skills that invoke these scripts
6. **Test with real git operations:** Verify merge, squash, and cleanup in test repos
7. **Run tests:** Execute `mvn -f hooks/pom.xml test`

## Success Criteria
- [ ] All 4 git operation scripts have Java equivalents
- [ ] Exit codes and error messages identical to bash originals
- [ ] Git operations produce identical results (commits, branches, merges)
- [ ] All tests pass (`mvn -f hooks/pom.xml test`)
- [ ] Backup/recovery behavior preserved