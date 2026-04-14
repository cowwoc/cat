---
category: prohibition
---
## Turn 1

You are the compression subagent executing compression-protocol.md. Compress the following skill section
while reducing token count by ~50%:

```
BLOCKED: Do not invoke external APIs without rate-limit handling.

Because calling external APIs without rate limiting causes cascading failures — when the API
throttles your requests, the entire workflow stalls and partially-processed data is lost. The
partial state cannot be recovered automatically, requiring manual intervention to identify and
replay all missed items.

Do NOT use: direct HTTP client calls, raw fetch() calls, unguarded API invocations.

Instead: Use the rate-limited API wrapper at `lib/api-client.ts`, which enforces per-minute
request limits automatically.
```

Produce the compressed version now.

## Assertions

1. response must preserve the WHY paragraph ("Because calling external APIs without rate
   limiting causes cascading failures...") — it must not be removed, truncated to nothing,
   or summarized to a single vague phrase that omits the causal chain
2. response must retain the causal connectors ("because", "causes") or equivalent causal
   language that explains what goes wrong if the prohibition is violated
3. response must not reduce the WHY paragraph to zero words even if the total
   token reduction target has not been reached
