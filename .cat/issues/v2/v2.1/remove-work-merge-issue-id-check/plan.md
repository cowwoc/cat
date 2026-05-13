# Plan

## Goal

Remove the `work-merge` protocol requirement that the target branch's new tip commit subject must reference the issue
id after merge. Merge verification should not force issue ids into implementation commit messages.

## Pre-conditions

(none)

## Post-conditions

- [x] `work-merge` no longer requires the latest target-branch commit subject to contain `ISSUE_ID`.
- [x] Post-merge verification still detects that the target branch advanced.
- [x] Post-merge verification still detects whether the intended issue commit was integrated without relying on commit
  message text.
- [x] Documentation or skill text no longer instructs agents to add issue ids to commit messages solely for merge
  verification.
