# Plan: hook-daemon-httpserver

## Goal
Replace per-invocation JVM startup with a long-running daemon process that uses JDK's built-in `jdk.httpserver`
(`com.sun.net.httpserver.HttpServer`). hook.sh becomes a thin bash `/dev/tcp` client, reducing hook latency from ~8ms
(AOT cold start) to ~4ms (bash spawn + HTTP round-trip on localhost).

## Satisfies
Parent: optimize-hook-json-parser (eliminates startup overhead entirely, making jackson-databind optimization moot)

## Risk Assessment
- **Risk Level:** HIGH
- **Concerns:** Process lifecycle management (crash recovery, orphan cleanup), security (localhost port access), graceful
  degradation when daemon is unavailable, testing daemon start/stop lifecycle
- **Mitigation:** Shared secret token for auth, cold-start fallback in hook.sh, session-scoped daemon lifetime with
  inactivity timeout, comprehensive integration tests

## Architecture

### Components
1. **HookDaemon.java** — Long-running JVM process using `HttpServer` on loopback, random port. Dispatches HTTP requests
   to existing handler classes. Writes port and auth token to session-scoped files.
2. **hook.sh** — Thin client: reads port/token files, uses bash `/dev/tcp` to send HTTP request to daemon. Falls back
   to cold JVM start if daemon unavailable. No external dependencies (curl/python not needed).
3. **session_start.sh** — Launches daemon in background, waits for readiness (port file appears).
4. **Lifecycle management** — Daemon exits after configurable inactivity timeout. Session end triggers explicit shutdown.

### Protocol
- Transport: HTTP/1.1 on `127.0.0.1` (loopback only)
- Auth: Bearer token in Authorization header (token file chmod 600)
- Endpoint: `POST /hook?handler=GetBashPretoolOutput`
- Request body: hook stdin JSON
- Response body: handler output JSON

### Client Selection (Benchmark Results)
| Client | IPC only | + Process spawn | Notes |
|--------|----------|-----------------|-------|
| Java-to-Java TCP | ~245µs | N/A (in-process) | Theoretical best, not usable as hook client |
| C compiled | ~466µs | ~2-3ms | Requires binary distribution per platform |
| bash `/dev/tcp` | ~1.8ms | **~4ms** | **Selected** — zero dependencies |
| curl | ~1ms | ~11.5ms | Process spawn dominates |
| Python (urllib) | ~1ms | ~50-60ms | Interpreter + import startup |

### Performance Target
- End-to-end per hook invocation: ~4ms (bash spawn + `/dev/tcp` + HTTP round-trip)
- Daemon startup: <500ms (one-time cost at session start)
- Memory: ~30-50MB resident

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/HookDaemon.java` — New: daemon server class
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` — Add `requires jdk.httpserver`
- `hooks/build-jlink.sh` — Add `jdk.httpserver` to `--add-modules`
- `plugin/hooks/hook.sh` — Rewrite as thin bash `/dev/tcp` client with cold-start fallback
- `plugin/hooks/session_start.sh` — Launch daemon on session start
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/HookDaemonTest.java` — New: daemon integration tests

## Acceptance Criteria
- [ ] HookDaemon starts on random loopback port, writes port to session-scoped file
- [ ] HookDaemon generates auth token, writes to chmod 600 file
- [ ] HookDaemon dispatches requests to existing handler classes correctly
- [ ] hook.sh uses bash `/dev/tcp` to contact daemon, falls back to cold JVM on failure
- [ ] Daemon shuts down on inactivity timeout or explicit signal
- [ ] Auth token validated on every request, rejects unauthorized requests
- [ ] `mvn -f hooks/pom.xml verify` passes
- [ ] End-to-end latency <1ms median on localhost (benchmarked)

## Execution Steps
1. **Add `jdk.httpserver` module** to module-info.java and build-jlink.sh `--add-modules`
2. **Create HookDaemon.java:** HttpServer on loopback with random port, Bearer token auth filter, handler dispatch via
   reflection or registry, port/token file writing, inactivity shutdown timer
3. **Create daemon lifecycle scripts:** Start in session_start.sh, readiness check (poll for port file), shutdown hook
4. **Rewrite hook.sh:** Read port/token, bash `/dev/tcp` HTTP request, parse response, cold-start fallback path
5. **Write integration tests:** Daemon start/stop, handler dispatch, auth rejection, concurrent requests, fallback path
6. **Benchmark:** Measure end-to-end latency, compare to current AOT cold-start approach
7. **Run `mvn -f hooks/pom.xml verify`** to ensure everything passes

## Success Criteria
- [ ] `mvn -f hooks/pom.xml verify` exits 0
- [ ] Daemon handles all existing hook types correctly
- [ ] Latency benchmark shows <5ms end-to-end per hook invocation (bash spawn + `/dev/tcp`)
- [ ] Cold-start fallback works when daemon is unavailable
