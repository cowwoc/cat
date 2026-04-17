# Plan: Fix Grader Schema Compliance

## Problem

The instruction-grader-agent produces JSON output that doesn't match the required schema. The agent outputs field names like "assertions" instead of "assertion_results", "status" instead of "verdict", or adds prohibited fields like "id", "summary", causing downstream processing failures.

## Parent Requirements

None (quality/reliability fix)

## Reproduction Code

```bash
# Run instruction-grader-agent on any test case
# Observe output contains wrong field names or structure
cat output.json
# Shows: {"assertions": [...]} instead of {"assertion_results": [...]}
# Or: {"verdict": "PASS", "status": "..."} with prohibited "status" field
```

## Expected vs Actual

- **Expected:** JSON with top-level `assertion_results` array, each entry having only `assertion`, `verdict`, `evidence`, `explanation` fields, with uppercase PASS/FAIL values
- **Actual:** JSON with wrong field names (`assertions` instead of `assertion_results`, `status` instead of `verdict`), prohibited extra fields (`id`, `summary`), or lowercase verdict values

## Root Cause

The current instruction-grader-agent.md instructions may not emphasize schema requirements strongly enough, or the formatting guidance may be ambiguous, leading the agent to use intuitive but incorrect field names.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Changes to agent instructions could affect grading accuracy if not tested thoroughly
- **Mitigation:** Use instruction-builder's test-driven approach with test cases that verify both schema compliance and grading correctness

## Files to Modify

- plugin/agents/instruction-grader-agent.md - strengthen schema requirements, add examples, improve field name emphasis

## Test Cases

- [ ] Grader produces JSON with correct top-level field name (`assertion_results`)
- [ ] Each assertion result has exactly 4 required fields with correct names
- [ ] Verdict values are uppercase PASS/FAIL
- [ ] No prohibited fields are present
- [ ] Evidence and explanation fields are never empty
- [ ] Grading accuracy is maintained (assertions are correctly evaluated)

## Pre-conditions

- [ ] All dependent issues are closed

## Main Agent Jobs

- /cat:instruction-builder-agent plugin/agents/instruction-grader-agent.md

## Jobs

### Job 1: Run instruction-builder on grader agent

- Invoke instruction-builder-agent with plugin/agents/instruction-grader-agent.md as the target
  - Files: plugin/agents/instruction-grader-agent.md
- Create test cases that verify schema compliance (field names, structure, value formats)
  - Files: plugin/tests/agents/instruction-grader-agent/schema-compliance-*.md
- Iterate until all schema requirements are met consistently
  - Files: plugin/agents/instruction-grader-agent.md

## Post-conditions

- [ ] instruction-builder test suite shows 100% schema compliance across all test cases
- [ ] All test cases in plugin/tests/agents/instruction-grader-agent/ pass
- [ ] Grader output matches the exact schema specified in the current instruction-grader-agent.md
- [ ] E2E: Running instruction-grader-agent produces valid JSON that passes grade-json-transformer validation without errors
