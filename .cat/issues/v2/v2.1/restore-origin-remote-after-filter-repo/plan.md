# Plan

## Goal

`git filter-repo` always removes the `origin` remote as a safety measure. Update the
`git-rewrite-history-agent` skill to save the `origin` URL before running filter-repo and restore it
afterward, so callers never need to repair the remote manually.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** If no `origin` remote exists before the run, the save step must not fail
- **Mitigation:** Guard the save with `git remote get-url origin 2>/dev/null` so it is a no-op when absent

## Files to Modify

- `plugin/skills/git-rewrite-history-agent/first-use.md` — add save/restore steps around the
  filter-repo invocation

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Update git-rewrite-history-agent skill

- In `first-use.md`, insert a new step between the current "Create a backup branch" step and the
  "Run git filter-repo" step:
  - Save the origin remote URL: `ORIGIN_URL=$(git remote get-url origin 2>/dev/null || true)`
  - Files: `plugin/skills/git-rewrite-history-agent/first-use.md`
- After the "Run git filter-repo" step, insert a new step to restore the remote:
  - If `ORIGIN_URL` is non-empty: `git remote add origin "$ORIGIN_URL"`
  - Files: `plugin/skills/git-rewrite-history-agent/first-use.md`
- Renumber all steps sequentially after the insertions
- Update the Verify checklist to include: origin remote is present and points to the correct URL

## Post-conditions

- [ ] `git-rewrite-history-agent/first-use.md` includes a step that saves the origin URL before
      filter-repo runs
- [ ] `git-rewrite-history-agent/first-use.md` includes a step that restores the origin remote after
      filter-repo completes
- [ ] The save step is guarded so it does not fail when no origin remote exists
- [ ] All steps are numbered sequentially with no gaps
- [ ] The verify checklist includes an origin-remote check
