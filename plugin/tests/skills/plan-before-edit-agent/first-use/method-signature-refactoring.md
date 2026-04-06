---
category: requirement
---
## Turn 1

Refactor all call sites of `processOrder` — the signature changed from `processOrder(OrderRequest req)` to `processOrder(Order order, Metadata meta)`.

## Assertions

1. agent invokes the plan-before-edit-agent skill with processOrder as the symbol argument
2. skill invocation uses the correct argument format
