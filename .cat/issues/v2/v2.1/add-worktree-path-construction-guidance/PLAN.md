# Plan: add-worktree-path-construction-guidance

## Goal

Add explicit worktree path construction guidance to work-execute delegation prompts to prevent agents
from defaulting to `/workspace` paths instead of using `${WORKTREE_PATH}/relative/path`.

## Parent Requirements

None — internal tooling improvement from learning M506.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — documentation/prompt improvement only
- **Mitigation:** Verify worktree isolation hook still catches incorrect paths as fallback

## Files to Modify

- `plugin/agents/work-execute.md` — Add explicit requirement for worktree-relative path construction
- `plugin/concepts/worktree-isolation.md` — Expand guidance with explicit examples of correct
  `${WORKTREE_PATH}`-prefixed paths

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add explicit path construction guidance to `plugin/agents/work-execute.md` Key Constraints section:
  "When working in a worktree, construct ALL file paths as `${WORKTREE_PATH}/relative/path`. Never use
  `/workspace` paths — the worktree isolation hook will block them."
- Update `plugin/concepts/worktree-isolation.md` to include explicit examples of correct vs incorrect
  path construction patterns.
- Update STATE.md to status: closed, progress: 100%.
  - Files: `plugin/agents/work-execute.md`, `plugin/concepts/worktree-isolation.md`,
    `.cat/issues/v2/v2.1/add-worktree-path-construction-guidance/STATE.md`

## Post-conditions

- [ ] `work-execute.md` explicitly states worktree-relative path construction requirement
- [ ] `worktree-isolation.md` includes concrete examples of correct vs incorrect paths
- [ ] Documentation references the `EnforceWorktreePathIsolation` hook as the enforcement mechanism
