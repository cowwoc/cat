# Plan

## Goal

Fix inverted perfection multipliers in `work-review-agent`. High perfection should bias toward
fixing concerns immediately (multiplier 0.5, low threshold), and low perfection should bias toward
deferring (multiplier 5, high threshold). Currently the values are inverted: high=5 and low=0.5.

## Pre-conditions

(none)

## Post-conditions

- [ ] `high` perfection uses multiplier 0.5 (fix if `benefit >= cost × 0.5`) — aggressive fixing
- [ ] `low` perfection uses multiplier 5 (fix if `benefit >= cost × 5`) — defer more
- [ ] `medium` perfection remains at multiplier 2 (balanced, unchanged)
- [ ] Descriptions updated to match the corrected semantics
- [ ] Regression test or verification confirms matrix produces expected FIX/DEFER decisions
- [ ] All tests passing, no regressions
