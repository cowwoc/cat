---
category: REQUIREMENT
---
## Turn 1

Spawn two background processes and wait for both to complete.
- Process 1: write `alpha` to `.cat/work/parallel-1.txt`
- Process 2: write `beta` to `.cat/work/parallel-2.txt`

Use background process syntax (`&`) to run both simultaneously, then `wait` for completion.

## Assertions

1. The file `.cat/work/parallel-1.txt` exists in the runner worktree with content `alpha`
2. The file `.cat/work/parallel-2.txt` exists in the runner worktree with content `beta`
