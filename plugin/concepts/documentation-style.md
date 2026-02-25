# Documentation Style

## Document When Each Scenario Applies

When code or documentation describes multiple formats, modes, or scenarios, always explain **when** each one occurs —
not just **what** they are.

```java
// Bad - lists formats without explaining when each occurs
/**
 * Handles two content formats:
 * - Plain string: {"content":"text here"}
 * - Array format: {"content":[{"type":"text","text":"..."}]}
 */

// Good - explains when each format occurs
/**
 * Handles two content formats used by Claude Code:
 * - Plain string: {"content":"text here"} — used for direct user-typed messages
 * - Array format: {"content":[{"type":"tool_result",...}]} — used for messages containing tool results
 */
```

This applies to Javadoc, code comments, markdown documentation, and configuration files. The reader should never need
to investigate separately to understand when each scenario applies.

## M-Code References

M-code labels (e.g., `M088`, `M252`) must only appear in `.claude/cat/retrospectives/` files. They must not appear in
any other file — including plugin source, Java source, project rules, configuration, or documentation.

M-codes in agent-visible files consume context tokens without providing value, and create a priming pattern where agents
copy the convention into new code.
