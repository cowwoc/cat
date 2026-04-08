# Plan: add-solution-completeness-rule

## Goal
Add `plugin/rules/solution-completeness.md` to counter the simplicity bias baked into Claude's default
behavior. Two rules: (1) investigation depth must not be sacrificed for communication brevity, and (2)
choose the correct and complete implementation, not the simplest one that passes surface checks.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Rule phrasing must be precise enough to be actionable without being over-broad
- **Mitigation:** Keep rules focused on the two specific failure modes identified from the gist analysis

## Files to Modify
- `plugin/rules/solution-completeness.md` - create new rule file

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Create `plugin/rules/solution-completeness.md` with frontmatter `mainAgent: true` (subAgents omitted
  so it injects into all subagents by default)
- Rule 1 — Investigation depth: brevity guidelines apply to communication only; investigation depth,
  root-cause analysis, and file reads are never shortened for conciseness
- Rule 2 — Solution quality: choose the implementation that fully and correctly solves the problem;
  simplicity heuristics apply to task scope, not to correctness or completeness; do not substitute a
  simpler implementation that passes surface checks when a more complete one is required

## Post-conditions
- [ ] `plugin/rules/solution-completeness.md` exists with correct frontmatter and both rules
- [ ] File follows the same style as existing rules (no license header — `plugin/rules/` files are exempt)
- [ ] Both rules are clearly actionable and distinct from existing rules in the directory
