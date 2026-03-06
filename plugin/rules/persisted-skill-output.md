---
mainAgent: true
subAgents: []
---
## Persisted Skill Output
When a skill invocation returns output larger than ~2KB, Claude Code persists the full content to a
file and shows only a 2KB preview inline with:

```
Output too large (X.XKB). Full output saved to: /path/to/file.txt
```

This is NOT a failure — it is normal behavior for large skill content.

**MANDATORY**:
1. Use the `Read` tool on the provided file path with `offset` and `limit` parameters
(e.g., 200 lines at a time) to read the content in chunks — reading the entire file at once
will trigger persisted output again, creating an infinite loop
2. Execute the workflow instructions it contains exactly as if the output had been returned inline

Do NOT treat output size as a failure signal or skip the workflow because the output was persisted.
