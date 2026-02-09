# Plan: unify-project-hooks

## Current State
2 project-specific hooks in .claude/settings.json:
- PreToolUse Write: validate-state-md-format.sh - Validates STATE.md bullet-point format
- PreToolUse Bash: block-worktree-cd.sh - Blocks cd into worktree directories

These run as separate bash processes in addition to the plugin hooks.

## Target State
Migrate both project hooks into the existing Java PreToolUse dispatchers:
- validate-state-md-format.sh logic into the Write|Edit dispatcher (or a new Write-specific handler)
- block-worktree-cd.sh logic into GetBashPretoolOutput (already handles Bash PreToolUse)

Remove .claude/settings.json hook registrations and .claude/hooks/ directory.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** These are project hooks (settings.json) not plugin hooks - different registration mechanism
- **Mitigation:** Move logic to plugin Java handlers; remove settings.json hook entries

## Files to Modify
- plugin/hooks/java/src/main/java/com/cat/hooks/bash/BlockWorktreeCd.java - NEW: Port of block-worktree-cd.sh
- plugin/hooks/java/src/main/java/com/cat/hooks/GetBashPretoolOutput.java - Add BlockWorktreeCd handler
- plugin/hooks/java/src/main/java/com/cat/hooks/write/ValidateStateMdFormat.java - NEW: Port of validate-state-md-format.sh
- plugin/hooks/java/src/main/java/com/cat/hooks/EnforceWorktreeIsolation.java - Add ValidateStateMdFormat handler (or new Write dispatcher)
- plugin/hooks/java/src/test/java/com/cat/hooks/bash/BlockWorktreeCdTest.java - NEW
- plugin/hooks/java/src/test/java/com/cat/hooks/write/ValidateStateMdFormatTest.java - NEW
- .claude/settings.json - Remove hooks section entirely
- .claude/hooks/validate-state-md-format.sh - DELETE
- .claude/hooks/block-worktree-cd.sh - DELETE

## Acceptance Criteria
- [ ] validate-state-md-format.sh STATE.md format validation ported to Java
- [ ] block-worktree-cd.sh cd detection and blocking ported to Java
- [ ] .claude/settings.json hooks section removed
- [ ] .claude/hooks/ directory removed
- [ ] Both bash scripts deleted
- [ ] Tests pass

## Key Implementation Details
- validate-state-md-format.sh: Checks Write tool_input.file_path for STATE.md pattern, validates bullet-point format (Status, Progress, Dependencies), blocks invalid writes
- block-worktree-cd.sh: Detects cd commands targeting .claude/cat/worktrees/*, blocks with explanation, suggests git -C
- BlockWorktreeCd can integrate into GetBashPretoolOutput since it matches Bash tool
- ValidateStateMdFormat needs Write matcher - can integrate into EnforceWorktreeIsolation (Write|Edit matcher) or create new Write-specific dispatcher

## Execution Steps
1. Create BlockWorktreeCd handler, integrate into GetBashPretoolOutput
2. Create ValidateStateMdFormat handler, integrate into Write|Edit dispatcher
3. Write tests
4. Remove .claude/settings.json hooks section
5. Delete .claude/hooks/ bash scripts
6. Run full test suite

## Success Criteria
- [ ] All tests pass
- [ ] .claude/settings.json has no hooks section
- [ ] .claude/hooks/ directory removed
- [ ] Project hook behavior preserved via Java plugin hooks