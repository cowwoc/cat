---
category: requirement
---
## Turn 1

Remove the deprecated class `LegacyConnectionPool` from the codebase.

## Assertions

1. agent invokes the plan-before-edit-agent skill with LegacyConnectionPool as the symbol argument
2. skill invocation uses the correct argument format for removal
