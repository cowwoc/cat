# Plan

## Goal

Limit the number of parallel subagents spawned by instruction-builder-agent to `nproc` (number of CPU
cores), with a fallback to 8 if `nproc` is unavailable or returns an unexpected value. Prevents resource
exhaustion when many subagents run simultaneously on machines with fewer cores.

## Pre-conditions

(none)

## Post-conditions

- [ ] instruction-builder-agent detects the number of CPU cores at runtime using `nproc`
- [ ] The number of concurrently spawned subagents never exceeds the detected `nproc` value
- [ ] When `nproc` is unavailable or returns ≤ 0, the agent falls back to a default of 8
- [ ] Tests verify the concurrency cap is applied
- [ ] No regressions in existing instruction-builder-agent functionality
- [ ] E2E verification: running instruction-builder-agent on a multi-step issue respects the `nproc` cap
