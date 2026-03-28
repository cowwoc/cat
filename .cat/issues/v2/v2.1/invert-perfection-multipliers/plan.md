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

## Research Findings

The bug is in `plugin/skills/work-review-agent/first-use.md` at the "Step 3: Apply the decision rule
based on perfection" section (~line 986). The current text reads:

```
- `low` (fix aggressively): multiplier = 0.5 — fix if `benefit >= cost × 0.5`
- `medium` (balanced): multiplier = 2 — fix if `benefit >= cost × 2`
- `high` (stay focused): multiplier = 5 — fix if `benefit >= cost × 5`
```

The `low` and `high` labels are swapped relative to the intended semantics. The comment at the top
of the config-read section (~line 373) already states the correct intent:
`# perfection scale: high=act immediately on improvements, low=defer`

The config description in `plugin/skills/config/first-use.md` also states the correct semantics:
`high=fix all issues, low=stay focused on primary goal`

The fix requires correcting only `plugin/skills/work-review-agent/first-use.md`:
1. Swap the `low`/`high` labels and their inline descriptions at ~lines 986–988
2. Update the combinations count text at ~lines 1007–1008 from
   "low perfection fixes 13/16 combinations, medium fixes 8/16, high fixes 6/16"
   to
   "high perfection fixes 13/16 combinations, medium fixes 8/16, low fixes 6/16"

No other files need to change — `plugin/skills/config/first-use.md` and the comment at line 373
already use the correct semantics.

**Verification of expected FIX/DEFER counts:**

With multiplier 0.5 (high perfection, aggressive), cost values {0, 1, 4, 10} and benefit values
{0, 1, 4, 10}: fix if `benefit >= cost × 0.5`. This gives many FIX outcomes.

With multiplier 5 (low perfection, stay focused), fix if `benefit >= cost × 5`. This gives fewer
FIX outcomes.

The stated "13/16 vs 6/16" ratio should be verified by the implementation subagent when it applies
the fix, as a sanity check on the semantic meaning.

## Commit Type

`bugfix:`

## Jobs

### Job 1
- In `plugin/skills/work-review-agent/first-use.md`, locate "Step 3: Apply the decision rule based
  on perfection" (~line 983). Change the three bullet lines so that:
  - `high` (fix aggressively): multiplier = 0.5 — fix if `benefit >= cost × 0.5`
  - `medium` (balanced): multiplier = 2 — fix if `benefit >= cost × 2`
  - `low` (stay focused): multiplier = 5 — fix if `benefit >= cost × 5`
- In the same file, locate the combinations count sentence (~line 1007):
  "low perfection fixes 13/16 combinations, medium fixes 8/16, high fixes 6/16"
  Change it to:
  "high perfection fixes 13/16 combinations, medium fixes 8/16, low fixes 6/16"
- Run `mvn -f client/pom.xml test` to verify no regressions
- Update index.json: status=closed, progress=100%
- Commit all changes in a single commit with message `bugfix: fix inverted perfection multipliers in work-review-agent`
