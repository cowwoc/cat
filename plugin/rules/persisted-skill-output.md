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
(up to 2000 lines at a time) to read the content in chunks — reading the entire file at once
will trigger persisted output again, creating an infinite loop
2. Execute the workflow instructions it contains exactly as if the output had been returned inline
3. When the persisted-output message states the file size (e.g., "30.3KB"), pre-compute the number of
   2000-line chunks needed and issue ALL Read calls in a single parallel message — do NOT read one chunk
   at a time. Estimate total lines as `ceil(size_in_bytes / 45)`, then issue `ceil(total_lines / 2000)`
   Read calls with `limit=2000` each. Overshooting is fine — the Read tool returns only lines that exist.
   Example for a 30.3KB file (~674 lines, 1 chunk):
   - Read offset=1 limit=2000
   Example for a 150KB file (~3334 lines, 2 chunks):
   - Read offset=1 limit=2000
   - Read offset=2001 limit=2000
   All calls in one message.

**When file size is NOT stated:** Fall back to sequential chunk-by-chunk reading as described in item 1.

Do NOT treat output size as a failure signal or skip the workflow because the output was persisted.
