# Plan

## Goal

Inspired by: https://gist.github.com/roman01la/483d1db15043018096ac3babf5688881

Add a plugin rule that guides agents to apply abstraction judgment during implementation, scaled by the `perfection` config value. The rule replaces any mechanical line-count heuristic ("3 similar lines = don't abstract") with a decision matrix based on two axes: **maintenance risk** (how much pain will this duplication cause — divergence risk, shotgun surgery, conceptual coupling) and **abstraction cost** (how complex/invasive is the proposed abstraction).

Per-level behavior:

- **perfection=low**: Only flag duplication when maintenance risk is *high* AND abstraction cost is *low* (e.g., trivial helper extraction). All other duplication is tolerated. Stance: duplication is cheaper than a wrong abstraction; stay focused on the task.
- **perfection=medium**: Flag when maintenance risk is *high* (regardless of cost), OR when maintenance risk is *medium* AND abstraction cost is *low*. Skip when risk is low or the fix is expensive relative to benefit. Stance: act on clear wins; don't chase theoretical future divergence.
- **perfection=high**: Evaluate any duplication that expresses a shared concept in semantically-coupled locations — "if this logic needs to change, will both copies need to change in lockstep? Will future engineers know to find both?" Flag when yes, even for two copies, even if abstraction requires design effort. Skip only purely incidental duplication (e.g., two unrelated functions that happen to share a loop structure). Stance: maintenance risk is real; abstractions that reduce it are worth the cost.

## Pre-conditions

(none)

## Post-conditions

- [ ] A new plugin rule (`plugin/rules/abstraction-judgment.md`) exists describing the decision matrix
- [ ] The rule is conditioned on `perfection` with distinct behavior for low, medium, and high
- [ ] The rule explicitly overrides any mechanical line-count threshold heuristic
- [ ] No regressions in existing plugin rules
