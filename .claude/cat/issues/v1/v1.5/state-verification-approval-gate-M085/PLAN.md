# Plan: state-verification-approval-gate-M085

## Objective
add STATE.md verification before approval gate (M085 prevention)

## Details
Add explicit verification step in approval_gate that checks git diff
for STATE.md presence. If missing, blocks approval gate presentation.

Escalated from documentation (spawn-subagent checklist was ignored).

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
