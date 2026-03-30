# Plan

## Goal

Change the VERIFY_DIR base path from `${CLAUDE_PROJECT_DIR}` to `${WORKTREE_PATH}` in all files that reference
`.cat/work/verify`. The directory name `.cat/work/verify` stays the same — only the base path variable changes. This
ensures verify files are written inside the issue worktree rather than the main project directory, maintaining worktree
isolation.

## Research Findings

Codebase analysis identified 5 files that reference the VERIFY_DIR path using `${CLAUDE_PROJECT_DIR}/.cat/work/verify`:
1. `plugin/concepts/work.md` — Concept documentation describing VERIFY_DIR path
2. `plugin/skills/work-merge-agent/first-use.md` — Skill implementation setting VERIFY_DIR
3. `plugin/skills/work-confirm-agent/first-use.md` — Skill implementation setting VERIFY_DIR
4. `plugin/agents/work-verify.md` — Agent documentation setting VERIFY_DIR
5. `plugin/migrations/2.1.sh` — Migration script (directory name unchanged, no base path variable)

All references are string literals in shell/Markdown files. The migration script uses the relative path
`.cat/work/verify` without a base path variable, so it requires no change.

## Pre-conditions

(none)

## Post-conditions

- [ ] All 4 identified plugin files updated from `${CLAUDE_PROJECT_DIR}/.cat/work/verify` to `${WORKTREE_PATH}/.cat/work/verify`
- [ ] Migration script unchanged (already uses relative path `.cat/work/verify`)
- [ ] No references to `${CLAUDE_PROJECT_DIR}/.cat/work/verify` remain in plugin source files
- [ ] The directory name `.cat/work/verify` is preserved (not renamed)
- [ ] Tests passing

## Execution Steps

1. **Update plugin/concepts/work.md**
   - Change: `${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}/` to `${WORKTREE_PATH}/.cat/work/verify/${CLAUDE_SESSION_ID}/`
   - Rationale: Verify files should be written inside the worktree

2. **Update plugin/skills/work-merge-agent/first-use.md**
   - Change: `VERIFY_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}"` to `VERIFY_DIR="${WORKTREE_PATH}/.cat/work/verify/${CLAUDE_SESSION_ID}"`
   - Rationale: Merge-agent reads verify files from the worktree

3. **Update plugin/skills/work-confirm-agent/first-use.md**
   - Change: `VERIFY_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}"` to `VERIFY_DIR="${WORKTREE_PATH}/.cat/work/verify/${CLAUDE_SESSION_ID}"`
   - Rationale: Confirm-agent writes verify files inside the worktree

4. **Update plugin/agents/work-verify.md**
   - Change: `VERIFY_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}"` to `VERIFY_DIR="${WORKTREE_PATH}/.cat/work/verify/${CLAUDE_SESSION_ID}"`
   - Rationale: Verify agent writes analysis files inside the worktree

5. **Verify migration script unchanged**
   - Confirm `plugin/migrations/2.1.sh` still has `new_verify_base=".cat/work/verify"` (relative path, no base variable)

6. **Verify no remaining CLAUDE_PROJECT_DIR references to .cat/work/verify**
   - Run: `grep -r "CLAUDE_PROJECT_DIR.*\.cat/work/verify" plugin/`
   - Expected: No output

7. **Run tests**
   - Command: `mvn -f client/pom.xml test`
   - Rationale: Ensures refactoring doesn't break existing functionality
