# State

- **Status:** closed
- **Resolution:** won't-fix
- **Reason:** Hook aggregator pattern is simpler and sufficient. See benchmarks below.
- **Progress:** 25%
- **Decomposed:** true
- **Decomposed At:** 2026-02-10
- **Dependencies:** [hook-sh-fix, json-core-api-migration, json-complex-handler-migration, build-optimize-validate]
- **Last Updated:** 2026-02-11

## Decomposed Into
- hook-sh-fix
- json-core-api-migration
- json-complex-handler-migration
- build-optimize-validate

## Parallel Execution Plan

### Wave 1 (Concurrent)
| Issue | Est. Tokens | Dependencies |
|-------|-------------|--------------|
| hook-sh-fix | 5K | None |
| json-core-api-migration | 45K | None |

### Wave 2 (After Wave 1)
| Issue | Est. Tokens | Dependencies |
|-------|-------------|--------------|
| json-complex-handler-migration | 35K | json-core-api-migration |

### Wave 3 (After Wave 2)
| Issue | Est. Tokens | Dependencies |
|-------|-------------|--------------|
| build-optimize-validate | 10K | json-complex-handler-migration |

**Total sub-issues:** 4
**Max concurrent subagents:** 2 (in Wave 1)

## Conflict Check
- Wave 1: hook-sh-fix touches `plugin/hooks/hook.sh`, json-core-api-migration touches `hooks/src/main/java/**`
- No file overlap - safe to parallelize

## Benchmark Results (2026-02-10)

All measurements are end-to-end median, including process spawn overhead.

### Per-invocation JVM (current architecture)

| Scenario | Median | Notes |
|----------|--------|-------|
| JVM + JsonParser (jackson-core) + AOT | 57ms | Streaming API, minimal classes |
| JVM + JsonMapper (jackson-databind) + AOT | 81ms | Tree model, heavier classpath |
| JVM + full handler (GetBashPretoolOutput) + AOT | 93ms | JsonMapper + 11 handler classes + logic |
| JVM + full handler, no AOT | 250ms | Cold JVM startup |

### Daemon architecture (long-running JVM, client per-invocation)

| Client | Median | Notes |
|--------|--------|-------|
| bash `/dev/tcp` (in-loop, no spawn) | 3-4ms | Theoretical best, no process spawn |
| bash `/dev/tcp` (new bash each time) | 7ms | Realistic, but not portable (fails on macOS, some Debian) |
| curl (new bash+curl each time) | 15ms | Portable, but 2x slower than `/dev/tcp` |

### Non-Java JSON parsing (for comparison)

| Approach | Median | Notes |
|----------|--------|-------|
| bash + grep/sed (coreutils) | 9ms | Fragile, not real JSON parsing |
| bash + jq | 28ms | jq process spawn is surprisingly heavy |
| bash + python3 | 30ms | Interpreter startup dominates |

### Process spawn baselines

| Process | Median | Notes |
|---------|--------|-------|
| bare bash (cat + echo) | 6-7ms | Minimum bash overhead |
| C compiled HTTP client | 3ms | Fastest possible, requires per-platform binary |
| curl (HTTP POST + verify 200) | 14ms | Includes full HTTP round-trip |
| jq (parse + extract) | 24ms | Heavier than expected |
| python3 (bare startup) | 13ms | No imports |
| python3 (urllib) | 55ms | With import |
| java (java.net.http) | 673ms | Full JVM startup, no AOT |

## Decision Rationale

The daemon approach (7-15ms) is faster than per-invocation JVM (93ms), but the complexity cost is high: process
lifecycle management, crash recovery, security (auth tokens), cold-start fallback, and portability issues with
`/dev/tcp` on macOS.

A **hook aggregator** pattern achieves the same goal more simply: Claude Code makes one hook call per event, the single
JVM invocation dispatches to all registered hooks internally, and returns all responses. The 93ms cost is paid once per
event instead of once per hook, making the daemon unnecessary.
