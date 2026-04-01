---
category: sequence
---

## Turn 1

You are a work-execute agent. You need to refactor method calls for the method `processOrder`. The signature changes from `processOrder(OrderRequest req)` to `processOrder(Order order, Metadata meta)`. Invoke the plan-before-edit-agent skill with this symbol.

Request: Invoke the skill with symbol arguments: `<cat_agent_id> processOrder`

## Assertions

1. The agent invokes grep to scan all occurrences of processOrder before any method edits
2. The agent builds a change map identifying all method call sites that require signature updates
3. The agent does NOT describe making edits before the scan and map phase complete
4. The agent applies all call site modifications using the Edit tool in a single batch
5. The agent does NOT run intermediate compilations while applying edits
6. The agent re-scans the old method signature and verifies all occurrences have been updated
7. The agent runs the build exactly once after all edits complete
