# Pre-existing Problems

**MANDATORY:** When working on an issue, fix pre-existing problems if they fall within the issue's goal, scope, and
post-conditions. Do not dismiss problems as "out of scope" merely because they existed before the current commit.

**Rationale:** The issue's acceptance criteria define what must be true when the issue is closed. If a pre-existing
problem violates those criteria, it must be fixed — regardless of when it was introduced.

**Example:** If an issue's goal is "remove all Python from the project" and pre-existing shell scripts contain inline
`python3` calls, those must be addressed. The fact that they existed before the issue started is irrelevant.

## Recurring Problems After Closed Issues

If a problem recurs after a closed issue was supposed to fix it, the fix was insufficient. Create a new issue to address
the gap — do NOT dismiss it as "already covered" by the closed issue.

**Rationale:** A closed issue means the fix was attempted, not that it succeeded. Recurrence is evidence that the
original fix had an incomplete scope, missed an edge case, or addressed symptoms rather than root cause. The new issue
should reference the closed one and identify what the original fix missed.
