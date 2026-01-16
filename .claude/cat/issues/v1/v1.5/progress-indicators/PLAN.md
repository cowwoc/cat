# Plan: progress-indicators

## Objective
add progress indicators to long-running workflows

## Details
Add reusable progress library (scripts/lib/progress.sh) that displays:
- Step counter (e.g., [Step 3/11])
- Percentage complete
- Time elapsed
- Estimated time remaining

Updated bash scripts:
- git-squash-optimized.sh (11 steps)
- git-merge-linear-optimized.sh (8 steps)
- batch-read.sh (3 steps)
- write-and-commit.sh (6 steps)

Updated skill/workflow docs with progress output guidance:
- execute-task.md (14-step workflow)
- spawn-subagent, collect-results, parallel-execute skills

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
