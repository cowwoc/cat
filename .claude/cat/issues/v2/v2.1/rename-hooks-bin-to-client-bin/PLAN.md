# Plan: rename-hooks-bin-to-client-bin

## Goal
Replace all `hooks/bin` path references with `client/bin` across the plugin codebase.

## Satisfies
None - infrastructure rename

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Mechanical text replacement; no logic changes
- **Mitigation:** Grep before and after to confirm exact count match

## Files to Modify
- plugin/hooks/hooks.json - 14 occurrences
- plugin/skills/work-with-issue-first-use/SKILL.md - 5 occurrences
- plugin/skills/skill-builder-first-use/SKILL.md - 2 occurrences
- plugin/skills/work-first-use/SKILL.md - 1 occurrence
- plugin/skills/stakeholder-review-box/SKILL.md - 1 occurrence
- plugin/skills/stakeholder-concern-box/SKILL.md - 1 occurrence
- plugin/skills/stakeholder-selection-box/SKILL.md - 1 occurrence
- plugin/skills/status-first-use/SKILL.md - 1 occurrence
- plugin/skills/statusline-first-use/SKILL.md - 1 occurrence
- plugin/skills/work-complete/SKILL.md - 1 occurrence
- plugin/skills/work-complete-first-use/SKILL.md - 1 occurrence
- plugin/skills/run-retrospective-first-use/SKILL.md - 1 occurrence
- plugin/skills/optimize-execution-first-use/SKILL.md - 1 occurrence
- plugin/concepts/silent-execution.md - 1 occurrence
- plugin/scripts/get-render-diff.sh - 1 occurrence

## Acceptance Criteria
- [ ] All 33 occurrences of `hooks/bin` replaced with `client/bin`
- [ ] Zero remaining `hooks/bin` references in plugin/
- [ ] No other unintended changes

## Execution Steps
1. **Replace all occurrences:** Find-and-replace `hooks/bin` with `client/bin` across all 15 files
2. **Verify:** Grep for remaining `hooks/bin` references - expect zero
3. **Commit:** Commit changes with appropriate message
