# Plan: fix-rebase-dry-run-flag

## Problem
When checking whether a rebase would produce conflicts before committing to it, the agent used
`git rebase --dry-run`, which is not a valid git rebase option. Git returned "error: unknown option
`dry-run'" and usage information. The agent had no documented guidance on how to probe for rebase
conflicts without performing the actual rebase.

## Parent Requirements
None

## Root Cause
`git rebase` does not support `--dry-run`. The agent assumed the flag existed by analogy with
other git commands (e.g., `git fetch --dry-run`), and no convention document warned against it or
provided the correct alternative.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None — documentation-only change
- **Mitigation:** Verify the documented alternative approach works before closing

## Files to Modify
- `plugin/skills/git-merge-linear-agent/first-use.md` — add "Common Issues" entry noting that
  `git rebase --dry-run` does not exist and documenting the recommended alternative
  (attempt the actual rebase; on conflict, resolve then continue, or abort with `git rebase --abort`)

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Add a "Common Issues" entry to `plugin/skills/git-merge-linear-agent/first-use.md`:
  - Title: "Issue: `git rebase --dry-run` does not exist"
  - Explain that git rebase has no dry-run flag
  - Document the correct approach: attempt the rebase; if conflicts arise, resolve them and
    run `git rebase --continue`, or abort with `git rebase --abort`
  - Files: `plugin/skills/git-merge-linear-agent/first-use.md`

## Post-conditions
- [ ] `plugin/skills/git-merge-linear-agent/first-use.md` contains a "Common Issues" entry
  stating that `git rebase --dry-run` does not exist
- [ ] The entry documents the correct alternative (attempt rebase, resolve conflicts or abort)
- [ ] E2E: Read the updated skill file and confirm the new entry is present and accurate
