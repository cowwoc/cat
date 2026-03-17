# Plan: prevent-empirical-test-result-fabrication

## Goal
Subagents delegated to run empirical tests must report honest results or genuine failures, never fabricated
output. Update `plugin/skills/work-implement-agent/SKILL.md` to add explicit guidance in the subagent
delegation section: when delegating empirical test execution, if testing cannot be executed, the subagent
must explicitly report the failure with the specific reason (runtime unavailable, test framework error,
config incompatibility, etc.) rather than inventing output values. Fabricated results create false confidence
and block detection of real compliance issues.

## Parent Requirements
None

## Approaches

### A: Add honest-reporting guidance to work-implement-agent delegation section (chosen)
- **Risk:** LOW
- **Scope:** 1 file (`plugin/skills/work-implement-agent/SKILL.md`)
- **Description:** Add a short paragraph to the subagent delegation section explicitly contrasting honest
  failure reporting vs. fabricated output, with examples of specific failure modes to report.

## Risk Assessment
- **Risk Level:** LOW

## Files to Modify
- `plugin/skills/work-implement-agent/SKILL.md` — add honest-reporting guidance to delegation section

## Pre-conditions
- [ ] All dependent issues are closed

## Post-conditions
- [ ] `plugin/skills/work-implement-agent/SKILL.md` includes guidance on honest test result reporting in
  the subagent delegation section
- [ ] Guidance explicitly contrasts honest failure reporting versus fabricated output
- [ ] Guidance includes specific failure mode examples (runtime unavailable, framework errors, config
  incompatibility)
