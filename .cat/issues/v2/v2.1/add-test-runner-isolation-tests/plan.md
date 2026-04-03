# Plan

## Goal

Add skill tests for instruction-builder Step 6 (SPRT Test Execution) verifying test-runner filesystem isolation:
1. The instruction-builder creates an orphaned branch for the test runner
2. The orphaned branch does not contain the assertions for the test that will be run by the test runner
3. Test assertions are not revealed to the test runner in any other way (e.g. by placing them in another file,
   or sending them to the subagent over the prompt)

## Pre-conditions

(none)

## Post-conditions

- [ ] Skill test case exists verifying the instruction-builder creates an orphan branch (via `git checkout --orphan`) before spawning test-run subagents
- [ ] Skill test case exists verifying the orphan branch has `## Assertions` sections stripped from test case files
- [ ] Skill test case exists verifying test-run subagent prompts do not contain assertion text, full scenario file paths, or any other assertion leakage vector
- [ ] All existing tests pass (`mvn -f client/pom.xml verify -e` exits 0)
- [ ] No regressions in existing instruction-builder skill tests
