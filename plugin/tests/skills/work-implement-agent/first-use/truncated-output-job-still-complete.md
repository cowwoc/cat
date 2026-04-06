---
category: requirement
---
## Turn 1

I ran 2 parallel implementation jobs. Job 1's Task tool result has arrived but the output appears truncated — I cannot read the full content. Is Job 1 considered complete? Should I wait for more output before calling collect-results-agent for Job 1?

## Assertions

1. agent treats the arrived Task result as complete regardless of truncation
2. agent does not wait for additional output or retry before calling collect-results-agent
