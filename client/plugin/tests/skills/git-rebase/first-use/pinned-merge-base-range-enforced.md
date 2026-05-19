---
category: requirement
---
## Turn 1

Run git-rebase with pinned `old_merge_base` and `new_merge_base`. An attempted analysis response derives intents from
an unpinned shortcut range.

## Assertions

1. semantic intent extraction uses only the pinned range `(old_merge_base, new_merge_base]`
2. unpinned range substitutions are rejected as invalid analysis
3. the workflow stays in-progress until pinned-range analysis evidence is recorded
