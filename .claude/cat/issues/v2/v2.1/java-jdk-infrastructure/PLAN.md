# Plan: java-jdk-infrastructure

## Metadata
- **Parent:** migrate-python-to-java
- **Sequence:** 1 of 5
- **Estimated Tokens:** 25K

## Objective
Create jlinked JDK 25 bundle with Jackson 3, and bootstrap/runner scripts.

## Scope
- JDK jlink configuration to create minimal runtime
- Jackson 3 module inclusion
- SessionStart bootstrap script (check/download JDK)
- Java hook runner intermediary script

## Dependencies
- None (first in sequence - other tasks depend on this)

## Files

All files at `plugin/hooks/jdk/`:

| File | Status | Purpose |
|------|--------|---------|
| `jlink-config.sh` | Exists | JDK jlink build configuration |
| `session_start.sh` | Exists | SessionStart hook to bootstrap JDK |
| `java_runner.sh` | Exists (fixed) | Intermediary script to run Java hooks |

Package prefix: `io.github.cowwoc.cat.hooks` (set in `java_runner.sh` line ~171)

JDK requirement: `CAT_JAVA_HOME` must be set (jlinked runtime only, no system JDK fallback)

## Execution Steps
1. Verify jlink configuration includes Jackson 3 modules
2. Verify session_start.sh correctly detects existing JDK or downloads bundle
3. Verify java_runner.sh correctly invokes Java hooks with correct package prefix
4. Verify scripts work on Linux (primary platform)

## Acceptance Criteria
- [x] jlink config creates minimal JDK with Jackson 3
- [x] session_start.sh correctly detects existing JDK or downloads bundle
- [x] java_runner.sh correctly invokes Java hooks with `io.github.cowwoc.cat.hooks` prefix
- [x] CAT_JAVA_HOME required (no system JDK fallback)
- [x] Scripts work on Linux (primary platform)
