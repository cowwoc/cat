# Plan

## Goal

Update project conventions in `.claude/rules/common.md` to specify that resumable progress state
must be written to `.cat/work` inside the issue worktree, not to `/tmp`, because `/tmp` may be
cleared between sessions when resuming a conversation.

## Pre-conditions

(none)

## Post-conditions

- [ ] `.claude/rules/common.md` is updated with a convention specifying `.cat/work` inside the
  issue worktree as the target for resumable progress state
- [ ] The updated convention includes a rationale explaining that `/tmp` is unsuitable for
  resumable progress state because it may be cleared between sessions
- [ ] The existing Multi-Instance Safety section guidance permitting `/tmp` is reconciled to
  clearly scope it to ephemeral (non-resumable) data only, distinguishing it from resumable
  progress state
- [ ] No regressions introduced to existing conventions
- [ ] E2E verification: convention is correctly documented without conflicting with other
  guidance in `common.md`
