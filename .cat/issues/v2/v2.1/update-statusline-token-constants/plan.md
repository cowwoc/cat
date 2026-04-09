# Plan: Update StatuslineCommand Token Constants

## Goal
Update two token buffer constants in `StatuslineCommand` to reflect current observed values:
- `AUTOCOMPACT_BUFFER_TOKENS`: 21_000 → 33_000
- `TOOL_DEFINITIONS_TOKENS`: 7_100 → 6_800

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — these are calibration constants, not logic
- **Mitigation:** Values are self-contained; no API surface changes

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java` — update both constants

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Update `AUTOCOMPACT_BUFFER_TOKENS` from `21_000` to `33_000`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java`
- Update `TOOL_DEFINITIONS_TOKENS` from `7_100` to `6_800`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java`

## Post-conditions
- [ ] `AUTOCOMPACT_BUFFER_TOKENS` equals `33_000` in StatuslineCommand.java
- [ ] `TOOL_DEFINITIONS_TOKENS` equals `6_800` in StatuslineCommand.java
- [ ] `mvn -f client/pom.xml verify -e` passes
