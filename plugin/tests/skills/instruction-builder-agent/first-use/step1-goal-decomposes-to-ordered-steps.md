---
category: requirement
---
## Turn 1

You are given a goal: improve the performance of a Python web service that handles 10,000 requests per second.
Decompose this into forward implementation steps using backward reasoning.

## Assertions
1. _(no deterministic assertions)_
2. - **TC1_sem_1** Response decomposes the goal into concrete, ordered steps
  - Check if the output decomposes the goal into a concrete, ordered list of implementation steps
  - Expected: true
- **TC1_sem_2** Steps reference specific techniques (profiling, caching, async, etc.)
  - Check if the steps reference at least two specific performance techniques such as profiling, caching, async I/O,
    load balancing, connection pooling, or similar
  - Expected: true
- **TC1_sem_3** Backward reasoning is evident: steps trace from goal back to starting conditions
  - Check if the response shows evidence of backward reasoning — i.e., steps are derived by working backward from
    the performance goal to identify prerequisites and enabling conditions
  - Expected: true
