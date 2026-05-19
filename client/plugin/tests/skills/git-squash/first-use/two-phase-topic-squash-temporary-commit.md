---
category: requirement
---
## Turn 1

Squash by topic with mixed implementation and config commits. Some commits are interleaved and require two-phase handling.

## Assertions

1. the agent uses a two-phase squash approach for topic grouping
2. phase 1 is represented as temporary framing work and not reported as final topic output
3. final commits are grouped by topic boundaries, not by raw type prefixes alone
4. verification confirms no file-loss regressions after phase 2
