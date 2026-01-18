# Plan: status-display-improvements

## Objective
consolidate yoloMode into trust levels and add stakeholder research

## Details
Configuration:
- Remove yoloMode in favor of trust: low|medium|high
- trust now controls both autonomy level and review behavior
- Update migration 1.9.sh to handle yoloMode -> trust conversion

Commands:
- Add stakeholder research step to add-major-version
- Refactor research command for version-level research
- Simplify config command (remove game mode option)
- Update status display format

Workflows:
- Update approval-gates to use trust-based mode selection
- Add rejection behavior by trust level to stakeholder-review
- Register 2.0 migration in registry

Hooks:
- Add license notice hook to SessionStart
- Check for PLAN.md files missing Research section on upgrade

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
