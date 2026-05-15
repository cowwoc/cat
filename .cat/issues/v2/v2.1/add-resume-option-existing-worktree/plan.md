# Plan

## Goal

Update the work skill's existing-issue/worktree handling so that when a requested issue already has an existing worktree, the user can resume working on that issue. If the original user request explicitly asked to resume or continue the issue, the workflow should resume immediately without asking the user to confirm the same intent again.

## Pre-conditions

(none)

## Post-conditions

- [ ] Existing-worktree handling offers a resume option whenever a specific requested issue already exists or has an existing worktree and the original request was not an explicit resume/continue request.
- [ ] Explicit resume/continue requests immediately invoke the documented resume flow for the existing issue ID without a second confirmation prompt.
- [ ] Cleanup and abort options remain available when the user did not explicitly ask to resume.
- [ ] Automated or manual verification covers both paths: explicit resume resumes directly, while non-resume requests present resume/cleanup/abort choices.
