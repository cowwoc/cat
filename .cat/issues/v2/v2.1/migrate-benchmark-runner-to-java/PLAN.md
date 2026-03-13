# Plan: migrate-benchmark-runner-to-java

## Goal
Migrate plugin/skills/instruction-builder-agent/benchmark-runner.sh (~1300 lines of Bash business logic)
to a Java jlink tool, following project conventions that require Java for complex business logic.

## Parent Requirements
None

## Current State
benchmark-runner.sh contains a full SPRT state machine, numeric arithmetic via awk, multi-pass JSON
parsing, statistical boundary evaluation, and incremental change-detection algorithms — all in Bash.

## Target State
A Java class `BenchmarkRunner.java` dispatches the 10 subcommands (extract-units, detect-changes,
map-units, extract-model, persist-artifacts, init-sprt, update-sprt, check-boundary, smoke-status,
merge-results) using Jackson JsonMapper for JSON and standard Java APIs for SHA-256 and file I/O.
The Bash script becomes a thin wrapper that delegates to the jlink binary.

## Risk Assessment
- **Risk Level:** HIGH
- **Breaking Changes:** Internal tool interface; no user-visible changes
- **Mitigation:** TDD approach (write tests first), functional parity verification

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/BenchmarkRunner.java` (NEW)
- `plugin/skills/instruction-builder-agent/benchmark-runner.sh` (replace with thin wrapper)
- Build configuration to register new jlink entry point

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Create BenchmarkRunner.java with all 10 command handlers
- Replace benchmark-runner.sh with thin wrapper delegating to jlink binary
- Register in build configuration
- Run tests to verify functional parity

## Post-conditions
- [ ] All 10 benchmark-runner commands produce identical JSON output in Java and original Bash
- [ ] benchmark-runner.sh is a thin wrapper (<20 lines) delegating to jlink binary
- [ ] E2E: Run instruction-builder-agent benchmark flow and confirm it works end-to-end
