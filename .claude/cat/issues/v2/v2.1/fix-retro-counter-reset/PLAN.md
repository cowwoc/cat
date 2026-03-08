# Plan: fix-retro-counter-reset

## Problem

The `retrospective-agent` SKILL.md instructs agents to "echo the output verbatim" after running
`get-output retrospective`, but this skips Steps 5-9 of the full retrospective workflow — including
the counter reset at Step 8. As a result, `mistake_count_since_last` in `index.json` is never reset,
causing the retrospective to re-trigger every session indefinitely.

## Parent Requirements

None

## Approaches

### A: Binary-level fix — reset counter inside GetRetrospectiveOutput.java (chosen)
- **Risk:** LOW
- **Scope:** 2 files (GetRetrospectiveOutput.java, test)
- **Description:** After generating retrospective analysis output, have the Java handler atomically
  update `index.json`: set `last_retrospective = now` and `mistake_count_since_last = 0`. The reset
  is part of the binary execution, so it cannot be skipped regardless of which skill variant runs.
- **Chosen because:** Deterministic, cannot be skipped by agents, eliminates design ambiguity between
  `retrospective-agent/SKILL.md` (thin wrapper) and `retrospective/first-use.md` (full workflow).

### B: Skill-level fix — add Steps 5-9 back to retrospective-agent/SKILL.md
- **Risk:** MEDIUM
- **Scope:** 1 file (retrospective-agent/SKILL.md)
- **Description:** Add explicit agent instructions to reset counter after echoing output.
- **Rejected because:** Skills can be summarized or abbreviated by agents. The same failure mode
  (M513) would recur with a different agent. Binary-level enforcement is more reliable.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** The reset must be atomic with the output generation; if the output fails mid-generation,
  the counter should NOT be reset
- **Mitigation:** Only reset counter if output generation completes successfully (non-error path);
  add test verifying counter is reset on success and not reset on failure

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java` — After
  successfully generating the analysis output, call a new `resetRetrospectiveCounter()` method that
  updates `index.json`: sets `last_retrospective` to current ISO timestamp and resets
  `mistake_count_since_last` to 0. Only call on the success path (when generating analysis data,
  not on status-message or error paths).
- `client/src/test/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutputTest.java` — Add
  tests verifying: (1) counter resets to 0 after successful retrospective analysis generation,
  (2) counter is NOT reset when output is a status message (threshold not met), (3) counter is NOT
  reset when output is an error.

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- In `GetRetrospectiveOutput.java`, add `resetRetrospectiveCounter()` method that:
  1. Reads `index.json` from the retrospectives directory
  2. Updates `last_retrospective` field to `Instant.now().toString()`
  3. Updates `mistake_count_since_last` field to `0`
  4. Writes the updated JSON back atomically (write to temp file, then rename)
  - Calls `resetRetrospectiveCounter()` at the end of the analysis output generation path (after all
    content lines are built, before returning), NOT on status-message or error paths
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java`

### Wave 2
- Add tests for counter reset behavior in `GetRetrospectiveOutputTest.java`
- Run `mvn -f client/pom.xml test` and confirm all tests pass
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutputTest.java`

## Post-conditions

- [ ] Running `get-output retrospective` when threshold is exceeded resets `mistake_count_since_last`
  to 0 in `index.json`
- [ ] Running `get-output retrospective` updates `last_retrospective` to current timestamp in
  `index.json`
- [ ] Counter is NOT reset when output is a status message (threshold not met)
- [ ] Counter is NOT reset when output is an error
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: Run `/cat:retrospective`, verify `index.json` shows `mistake_count_since_last = 0` and
  `last_retrospective` updated to current time
