# v2.7: Technical Debt Analysis

## Objective
Define a technical debt metric, implement calculation at multiple scopes (file, directory, module, product), and provide
reporting and aggregation. Eventually feed technical debt signals back into CAT's workflows (e.g., prioritizing issues,
flagging hotspots during review).

## Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| REQ-001 | Define technical debt metric | must-have | Clear, documented metric with scoring rubric |
| REQ-002 | Per-file debt calculation | must-have | Calculate debt score for any single file |
| REQ-003 | Aggregation across scopes | must-have | Aggregate scores for directory, module, and product |
| REQ-004 | Reporting skill | must-have | `/cat:tech-debt` skill produces formatted report |
| REQ-005 | Workflow feedback loop | should-have | Debt signals influence issue prioritization or review |

## Pre-conditions
- v2.1 complete (stable jlink tooling and skill infrastructure)

## Post-conditions
- All issues complete
- Technical debt metric documented and testable
- Reporting works at file, directory, module, and product scopes
