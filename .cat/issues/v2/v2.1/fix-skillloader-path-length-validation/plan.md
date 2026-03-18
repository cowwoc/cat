# Plan: fix-skillloader-path-length-validation

## Problem
When `$ARGUMENTS` is passed quoted to skill-loader (e.g., `"$ARGUMENTS"`), the entire string
(catAgentId + description) becomes a single shell argument. SkillLoader treats `$0` as the
catAgentId and resolves it as a filesystem path. When the description is long, this exceeds the
OS filename limit (~255 bytes), causing `ENAMETOOLONG`.

## Satisfies
None

## Root Cause
SkillLoader assumes `skillArgs.getFirst()` is purely a catAgentId (path-safe UUID or subagent
path). But when the agent passes `"sessionId description text"` as a single quoted argument,
the entire string (including the description) is used in path construction at line 192.

## Fix
In the SkillLoader constructor, after extracting `catAgentId` from `skillArgs.getFirst()`:
1. Split on first whitespace to separate catAgentId from any trailing description
2. If a space is found, insert the remainder (after the space) as a new element after index 0
   in the tokens list, shifting existing elements forward

This ensures `$0` = catAgentId (path-safe) and `$1` = description text (if present), with
original `$1...$N` becoming `$2...$N+1`.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Existing skills that rely on `$1` positional args could shift by one
- **Mitigation:** Only splits when `$0` contains a space. catAgentId (UUID or subagent path)
  never contains spaces, so this only triggers when description text is concatenated.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` — split `$0` on
  first whitespace in the constructor (lines 170-178)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` — add TDD test
  for the split behavior

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Write a failing test that passes `"UUID description text"` as `skillArgs.getFirst()` and
  verifies that after construction, `$0` = UUID and `$1` = "description text"
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java`
- Implement the fix: in SkillLoader constructor (lines 170-178), after extracting catAgentId,
  check for space in the first arg. If found, split into catAgentId (before space) and
  remainder (after space), inserting remainder at index 1 in the tokens list
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`
- Run all tests to verify no regressions
- Update STATE.md: status=closed, progress=100%
  - Files: `.cat/issues/v2/v2.1/fix-skillloader-path-length-validation/STATE.md`

## Post-conditions
- [ ] SkillLoader splits `$0` on first whitespace when it contains a space
- [ ] After split: `$0` = catAgentId, `$1` = remainder, original args shift forward
- [ ] catAgentId without spaces is unchanged (no regression)
- [ ] Blank `$0` still falls back to CLAUDE_SESSION_ID
- [ ] E2E: `skill-loader add-agent "UUID long description text"` succeeds without
  ENAMETOOLONG error
- [ ] All tests pass: `mvn -f client/pom.xml test`
