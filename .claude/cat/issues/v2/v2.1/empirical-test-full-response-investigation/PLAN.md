# Plan: Empirical Test Full Response Investigation

## Problem
The empirical test runner shows only truncated output previews (80 chars) when trials fail. This led
to incorrect conclusions — multi-invocation tests appeared to fail at 0% when in fact only the first
invocation was failing while 2nd+ invocations succeeded. The `--output` flag exists but crashes due
to a Jackson serialization bug (ConfigResult record not public for module reflection).

## Satisfies
None (infrastructure/tooling improvement)

## Root Cause
1. `EmpiricalTestRunner.ConfigResult` is a private record inside EmpiricalTestRunner. Jackson 3.x
   module system cannot serialize it due to access restrictions.
2. The `/cat:empirical-test` skill methodology doesn't include a step for examining full agent
   responses when failures occur, leading to incorrect root cause analysis.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/EmpiricalTestRunner.java` - Fix
  ConfigResult visibility for Jackson serialization
- `plugin/skills/empirical-test-first-use/SKILL.md` - Add investigation step using `--output` flag
  and manual conversation inspection

## Acceptance Criteria
- [ ] `--output` flag produces valid JSON file without Jackson exception
- [ ] `/cat:empirical-test` skill includes step for inspecting full agent responses on failure
- [ ] Skill methodology warns about multi-message evaluation pitfalls

## Execution Steps
1. **Step 1:** Make ConfigResult and TrialResult records public (or add Jackson module opens) in
   EmpiricalTestRunner.java so `--output` serialization succeeds
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/EmpiricalTestRunner.java`
2. **Step 2:** Add investigation guidance to empirical-test skill between Step 3 (baseline) and
   Step 4 (isolation). When baseline confirms failure, instruct user to run with `--output` and
   examine full agent responses before proceeding to isolation.
   - Files: `plugin/skills/empirical-test-first-use/SKILL.md`
3. **Step 3:** Add warning about multi-message evaluation: when priming messages exist, each
   message gets its own response. Failures in priming responses don't indicate the test prompt
   failed — check which specific response caused the failure.
   - Files: `plugin/skills/empirical-test-first-use/SKILL.md`
4. **Step 4:** Run `mvn -f client/pom.xml test` to verify changes compile and tests pass

## Success Criteria
- [ ] Running `--output /tmp/test.json` produces valid JSON with full trial responses
- [ ] Skill document includes response investigation step
