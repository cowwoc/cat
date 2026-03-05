# Plan: pass-absolute-paths-to-reviewer-subagents

## Goal
Pass absolute file paths (worktree_path/relative_path) to stakeholder reviewer subagent prompts
so that when reviewers read files, they read from the correct worktree location. This eliminates
false-positive "file not found" reports caused by reviewers defaulting to `/workspace/`.

## Satisfies
None

## Problem
Stakeholder reviewers receive the worktree path as an argument and are instructed to use it, but
frequently default to `/workspace/` (the main workspace) when reading files. This produces false
positives such as "implementation files not found" even when the files exist in the worktree.
Observed in the 2.1-prevent-git-user-changes session: requirements stakeholder reported all
implementation files as non-existent because it scanned `/workspace/client/src/` instead of the
worktree path.

## Root Cause
The changed files listing in reviewer prompts uses relative paths (from `git diff --name-only`).
Reviewers must combine these with the worktree path to get the correct absolute path, but they
frequently default to `/workspace/` as the root instead. Passing absolute paths removes this
ambiguity — the full path is directly usable without any prefix logic.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None significant — absolute paths are longer strings but negligible token impact
- **Mitigation:** N/A

## Files to Modify
- `plugin/skills/stakeholder-review-agent/first-use.md` — change the Batch 3 file content
  preparation to use absolute paths in file headers, and update the `## Files to Review` section
  to list absolute paths

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- In Batch 3 (file content preparation, ~line 505-571), change `### File: ${file}` to use the
  absolute path: `### File: $(pwd)/${file}` so that file headers in the reviewer prompt contain
  the full worktree-rooted path
  - Files: `plugin/skills/stakeholder-review-agent/first-use.md`
- Commit type: `feature:`

## Post-conditions
- [ ] Changed file headers in reviewer prompts use absolute worktree paths, not relative paths
- [ ] No regression: file content is still embedded for small files, structure+diff for large files
- [ ] All tests pass
