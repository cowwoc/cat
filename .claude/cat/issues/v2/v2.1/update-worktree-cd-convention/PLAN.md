# Plan: update-worktree-cd-convention

## Current State
Convention files and skill instructions mandate `git -C` for ALL worktree operations and explicitly prohibit `cd` into worktrees. This was established by M464 (learn-skill-worktree-safety) after a shell corruption incident where concurrent git operations in a worktree caused problems.

## Target State
Allow agents to `cd` into worktrees freely. The only restriction is: never remove (rm, git worktree remove) the directory you are currently inside. The safe-rm skill already documents this pattern correctly and remains unchanged.

## Satisfies
None - convention update

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Convention text changes only; no operational code changes
- **Mitigation:** All changes are to instruction/convention text in markdown files, not executable code

## Files to Modify

### Convention/Instruction Files (update text to allow cd)
1. `.claude/rules/common.md` (line 154) - Replace `git -C` mandate with worktree directory safety rule
2. `plugin/concepts/git-operations.md` (lines 47-48) - Replace "Use Absolute Paths" section with worktree directory safety
3. `plugin/skills/cleanup/content.md` (line 19) - Change "NEVER cd into a worktree" to "ensure you are not inside a worktree before removing it"
4. `plugin/skills/cleanup/content.md` (line 109) - Change CRITICAL instruction from "NEVER cd" to "ensure cwd is not inside worktree before removal"
5. `plugin/skills/work-merge/content.md` (lines 89-103) - Remove "CRITICAL: Use git -C, not cd" instruction; replace with safety note about not being inside worktree during removal
6. `plugin/skills/work/phase-prepare.md` (lines 405-406) - Remove "Never cd into worktree directories" instruction; replace with safety note
7. `plugin/concepts/merge-and-cleanup.md` (line 251) - Update recovery example comment
8. `plugin/skills/work-with-issue/content.md` (line 316) - No convention text change needed (just uses git -C operationally)

### Memory File
9. `/home/node/.config/claude/projects/-workspace/memory/MEMORY.md` - Update M464 entry to reflect new convention

### Files NOT Changed (operational git -C usage is fine as-is)
- `plugin/skills/git-merge-linear/content.md` - Uses git -C operationally by design (operates from outside worktree)
- `plugin/skills/collect-results/content.md` - Uses git -C operationally
- `plugin/skills/learn/phase-record.md` - Uses git -C operationally
- `plugin/skills/work-prepare/content.md` - Uses git -C operationally
- `plugin/skills/decompose-issue/content.md` - Uses git -C operationally
- `plugin/skills/safe-rm/content.md` - Already correctly documents the "don't rm your cwd" pattern

## Acceptance Criteria
- [ ] No convention text mandates `git -C` as the only way to work with worktrees
- [ ] No convention text prohibits `cd` into worktrees
- [ ] All modified files include the safety rule: do not remove the directory you are inside
- [ ] Operational `git -C` usage in scripts/skills is left unchanged
- [ ] MEMORY.md M464 entry updated
- [ ] Tests pass

## Execution Steps
1. **Step 1:** Update `.claude/rules/common.md` line 154
   - Old: `**Use \`git -C\`** instead of \`cd\` + \`git\` to operate on worktrees without changing directory.`
   - New: `**Worktree directory safety:** You may \`cd\` into worktrees to work. However, before removing a directory (via \`rm\`, \`git worktree remove\`, etc.), ensure your shell is NOT inside the directory being removed. See \`/cat:safe-rm\`.`
   - Files: `.claude/rules/common.md`

2. **Step 2:** Update `plugin/concepts/git-operations.md` lines 44-49
   - Replace "Use Absolute Paths" subsection with "Worktree Directory Safety" subsection
   - Old heading: `### Use Absolute Paths`
   - New heading: `### Worktree Directory Safety`
   - New content: Explain that cd into worktrees is allowed, but must ensure not inside directory before removing it. Reference `/cat:safe-rm`.
   - Files: `plugin/concepts/git-operations.md`

3. **Step 3:** Update `plugin/skills/cleanup/content.md` line 19
   - Old: `- NEVER cd into a worktree that will be deleted - use \`git -C <path>\` instead`
   - New: `- Before removing a worktree, ensure your shell is NOT inside it (cd out first if needed)`
   - Files: `plugin/skills/cleanup/content.md`

4. **Step 4:** Update `plugin/skills/cleanup/content.md` line 109
   - Old: `**CRITICAL: Use \`git -C <path>\` - NEVER cd into a worktree that will be deleted.**`
   - New: `**CRITICAL: Before removing a worktree, ensure your shell is NOT inside it.** If you are inside the worktree, \`cd /workspace\` first.`
   - Files: `plugin/skills/cleanup/content.md`

5. **Step 5:** Update `plugin/skills/work-merge/content.md` lines 89-103
   - Replace "CRITICAL: Use git -C, not cd into the worktree" block
   - Keep the rebase command but remove the cd prohibition
   - New: Explain the rebase must target the worktree (via git -C or from within worktree), and note that before Step 5 removal, agent must ensure it is NOT inside the worktree
   - Files: `plugin/skills/work-merge/content.md`

6. **Step 6:** Update `plugin/skills/work/phase-prepare.md` lines 405-406
   - Old: `Use \`git -C "$WORKTREE_PATH"\` or absolute paths for all operations on the worktree. Never cd into worktree directories, as this corrupts shell state when the worktree is later removed (M392, M464).`
   - New: `You may \`cd\` into the worktree to work. However, before the worktree is removed (during merge/cleanup), ensure your shell is NOT inside the worktree directory.`
   - Files: `plugin/skills/work/phase-prepare.md`

7. **Step 7:** Update `plugin/concepts/merge-and-cleanup.md` line 251
   - Old comment: `# If in wrong location, use absolute paths or git -C for all subsequent operations`
   - New comment: `# If in wrong location, cd to the correct directory or use git -C`
   - Files: `plugin/concepts/merge-and-cleanup.md`

8. **Step 8:** Update MEMORY.md M464 entry
   - Update to reflect that cd into worktrees is now allowed
   - Keep the safety rule about not removing cwd
   - Files: `/home/node/.config/claude/projects/-workspace/memory/MEMORY.md`

9. **Step 9:** Run tests
   - Run: `mvn -f hooks/pom.xml test`
   - Files: none

## Success Criteria
- [ ] No file contains text prohibiting cd into worktrees
- [ ] All modified files reference safe-rm or the cwd-removal safety rule
- [ ] All tests pass