# Plan: empirical-test-strict-defaults

## Goal
Update EmpiricalTestRunner to default to 10 trials with 100% pass rate requirement, fail-fast on first failure, and run
trials in parallel.

## Satisfies
None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Parallel execution may cause resource contention; fail-fast changes exit code semantics
- **Mitigation:** Use bounded thread pool; maintain backward compatibility via CLI flags

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/EmpiricalTestRunner.java` - Change defaults, add
  parallelism, add fail-fast
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EmpiricalTestRunnerTest.java` - Update tests for new behavior

## Acceptance Criteria
- [ ] Default trials changed from 5 to 10
- [ ] 100% pass rate required by default (any failure = overall failure)
- [ ] Fail-fast: stop remaining trials on first failure
- [ ] Trials run in parallel (bounded thread pool)
- [ ] Exit code 1 if any trial fails (not just 0% across multiple configs)
- [ ] All existing tests pass (updated as needed)

## Execution Steps
1. **Change default trials to 10:** In `main()`, change `int trials = 5` to `int trials = 10`
   - Files: `EmpiricalTestRunner.java`
2. **Add fail-fast behavior:** In the trial loop (`runTrials` or equivalent), check each trial result and abort
   remaining trials on first failure
   - Files: `EmpiricalTestRunner.java`
3. **Add parallel execution:** Replace sequential trial loop with parallel execution using ExecutorService. Use
   CompletableFuture or similar. Respect fail-fast by cancelling remaining futures on first failure.
   - Files: `EmpiricalTestRunner.java`
4. **Fix exit code logic:** Change exit criteria from "0% across multiple configs" to "any config below 100% = exit 1"
   - Files: `EmpiricalTestRunner.java`
5. **Update tests:** Adjust test expectations for new defaults and behavior
   - Files: `EmpiricalTestRunnerTest.java`
6. **Run test suite:** `mvn -f client/pom.xml test`

## Success Criteria
- [ ] `--trials` defaults to 10 when not specified
- [ ] A single trial failure causes immediate termination and exit code 1
- [ ] Multiple trials execute concurrently (not sequentially)
- [ ] `mvn -f client/pom.xml test` passes
