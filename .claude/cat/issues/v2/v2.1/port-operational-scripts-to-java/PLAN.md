# Plan: port-operational-scripts-to-java

## Goal
Port operational monitoring, batch operations, and hook registration scripts to Java.

## Current State
Three bash scripts handle subagent monitoring, batch file reading, and hook registration.

## Target State
Java classes replacing these scripts with equivalent functionality.

## Satisfies
Parent: port-utility-scripts

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None if output matches
- **Mitigation:** Verify output parity for each script

## Scripts to Port
- `monitor-subagents.sh` (152 lines) - Real-time subagent status monitoring via git worktree
  inspection and session JSONL token counting
- `batch-read.sh` (222 lines) - Efficient multi-file reading with grep pattern matching,
  reduces LLM round-trips
- `register-hook.sh` (224 lines) - Interactive hook registration wizard for settings.json

## Files to Create
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/SubagentMonitor.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/BatchReader.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/HookRegistrar.java`
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/SubagentMonitorTest.java`
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/BatchReaderTest.java`

## Execution Steps
1. Read monitor-subagents.sh to understand worktree parsing, session file reading, token counting
2. Create `SubagentMonitor` class with JSON output
3. Read batch-read.sh to understand grep + read pattern
4. Create `BatchReader` class with structured output
5. Read register-hook.sh to understand wizard flow
6. Create `HookRegistrar` class implementing hook registration logic
7. Write tests for SubagentMonitor and BatchReader
8. Run `mvn verify` to confirm all tests pass

## Success Criteria
- [ ] All three operational scripts have Java equivalents
- [ ] JSON output matches original scripts
- [ ] All tests pass (`mvn verify`)
