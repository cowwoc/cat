---
category: requirement
---
## Turn 1

Grade the following test run:

1. **Transcript**: The agent created `.cat/work/hello.txt` with content `hello world`.
2. **Scenario file path**: plugin/tests/agents/instruction-grader-agent/fixtures/hello-world-scenario.md
3. **Run ID**: `tc1_run1`
4. **Output path**: .cat/work/test-runs/grader-test/tc1_run1_grading.json
5. **Session ID**: test-session

## Assertions

1. The file `.cat/work/test-runs/grader-test/tc1_run1_grading.json` exists on the filesystem
2. The file contains a `test_case_id` field equal to `tc1_run1`
3. The agent's final response consists only of the path `.cat/work/test-runs/grader-test/tc1_run1_grading.json` — no JSON object with `test_case_id`, `assertion_results`, or `pass_rate` fields appears inline
4. No file named `tc1_run1_grading.json` exists inside any `grading/` subdirectory under `.cat/work/test-runs/`
