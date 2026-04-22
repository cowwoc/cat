# Plan

## Goal

Add ETA output to SPRT batch summaries. After each batch, the summary should display elapsed time, average batch duration, and estimated time remaining to reach ACCEPT, so users can monitor progress without manual calculation.

## Pre-conditions

(none)

## Post-conditions

- [ ] SPRT batch summaries include elapsed time since start and average batch duration
- [ ] ETA to ACCEPT is computed as `runs_to_convergence × avg_batch_duration` and displayed as `~Xh Ym` or `~Ym Xs`
- [ ] ETA line appears at the bottom of each batch summary table
- [ ] All existing tests pass, no regressions
- [ ] E2E: running SPRT on a test suite shows ETA in each batch summary
