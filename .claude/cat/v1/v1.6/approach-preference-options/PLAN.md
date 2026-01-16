# Plan: approach-preference-options

## Objective
implement approach preference with three standardized options

## Details
PLAN.md templates now require three approaches:
- Conservative: minimal scope, low risk, may leave technical debt
- Balanced: reasonable scope, medium risk, good tradeoffs
- Aggressive: comprehensive solution, high risk, addresses root causes

Planning subagent produces all three options. Selection logic:
- conservative preference → auto-select Conservative (unless HIGH risk)
- aggressive preference → auto-select Aggressive (unless HIGH risk)
- balanced preference → present choice, user decides
- HIGH risk tasks → always present choice for confirmation

Text mappings added for planning subagents to guide approach style:
- conservative: "Favor safest path, minimize scope, avoid architectural changes"
- balanced: "Balance safety and thoroughness, address core issue"
- aggressive: "Favor comprehensive solutions, address root causes"

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
