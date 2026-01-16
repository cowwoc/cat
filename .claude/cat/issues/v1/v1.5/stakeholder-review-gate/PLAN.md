# Plan: stakeholder-review-gate

## Objective
add multi-perspective stakeholder review gate

## Details
Add quality gate that runs 5 parallel stakeholder reviews after implementation:
- architect: System design, module boundaries, API design
- security: Vulnerabilities, input validation, resource limits
- quality: Code quality, complexity, duplication
- tester: Test coverage, edge cases, test quality
- performance: Algorithm complexity, memory, resource usage

Each stakeholder reviews implementation and returns concerns with severity
(CRITICAL/HIGH/MEDIUM). Aggregation rules determine if implementation proceeds:
- Any CRITICAL → REJECTED (must fix)
- 3+ HIGH total → REJECTED (must fix)
- Only MEDIUM → proceed with notes

Loops back to implementation phase until concerns resolved or max iterations.
Integrates between token check and user approval gate in execute-task.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
