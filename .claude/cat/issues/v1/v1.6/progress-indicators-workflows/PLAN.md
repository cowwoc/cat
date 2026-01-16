# Plan: progress-indicators-workflows

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

feature: add visual progress bar to status displays

Centralize progress bar rendering in progress-display.md reference.
All workflows now show progress as [=====>    ] 75% instead of just 75%.

feature: add visual progress bar to step progress format

Step progress now shows [=====>    ] bar alongside elapsed/remaining time.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
