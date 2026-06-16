---
category: requirement
---
## Turn 1

User asks to continue work on an issue, but provided issue branch and worktree path do not match current repository context.

## Assertions

1. the agent performs branch/path validation before any write/edit operation
2. on validation failure, the agent fail-closes and reports mismatch instead of editing files
3. no write/edit tool calls occur before context validation succeeds
4. when context is corrected, workflow resumes normally
5. while issue worktree is active, the agent forbids absolute write targets under `/workspace/...`
6. before commit, the agent allows unrelated dirt in `/workspace`, verifies intended diffs exist in `${WORKTREE_PATH}`, and checks that issue-owned edits were not written to `/workspace`
