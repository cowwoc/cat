# Plan: fix-work-review-autofix-plan-path

## Type
bugfix

## Goal
Fix the planning subagent in `work-review-agent`'s auto-fix iteration to write intermediate plan artifacts
to `.cat/work/` instead of `.claude/`.

## Problem
When `work-review-agent` runs its auto-fix iteration loop (to address stakeholder concerns), it spawns a
planning subagent to create fix plans. That subagent creates `review-fix-plans.md` in `.claude/` — a
developer-facing directory that is not shipped to end users and is not intended for runtime artifacts.

The closed issue `2.1-move-review-artifacts-to-cat-work` fixed concern artifacts (`.cat/review/` →
`.cat/work/review/`), but did NOT fix the planning subagent's output path. This is a different code path
and a recurrence of the same class of bug.

**Root cause:** The planning subagent was not given an explicit output path in its prompt. It defaulted to
`.claude/` (which it treated as a project working directory).

## Target State
The planning subagent in `work-review-agent`'s auto-fix loop writes `review-fix-plans.md` to
`${WORKTREE_PATH}/.cat/work/review-fix-plans.md` (or similar path under `.cat/work/`).

## Post-conditions
- [ ] `work-review-agent` auto-fix planning subagent writes artifacts to `.cat/work/` not `.claude/`
- [ ] The explicit output path is passed to the planning subagent in its prompt
- [ ] No new files appear under `.claude/` during a normal `work-review-agent` auto-fix iteration

## Files to Modify
- `plugin/skills/work-review-agent/first-use.md` — update the planning subagent prompt to specify
  output path as `${WORKTREE_PATH}/.cat/work/review-fix-plans.md`

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. In `plugin/skills/work-review-agent/first-use.md`, locate the section where the planning subagent
   is spawned for auto-fix iteration
2. Add explicit output path instruction to the planning subagent prompt:
   - The subagent must write its plan to `${WORKTREE_PATH}/.cat/work/review-fix-plans.md`
   - The path must use `${WORKTREE_PATH}` to be worktree-isolated
3. Update any references in the skill that read the plan from `.claude/review-fix-plans.md` to read
   from `${WORKTREE_PATH}/.cat/work/review-fix-plans.md`
4. Run relevant tests to confirm no regressions
5. Commit as `bugfix: fix work-review auto-fix plan path from .claude to .cat/work`
