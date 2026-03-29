# Plan

## Goal

Add atomicity scoring quality gate to cat:learn before recording learnings. The gate uses an LLM rubric to evaluate each prevention rule against criteria: is it specific, verifiable, and actionable? Vague rules are hard-blocked and the subagent must revise before recording proceeds.

## Pre-conditions

(none)

## Post-conditions

- [ ] Atomicity gate evaluates each prevention rule against rubric: specific, verifiable, and actionable
- [ ] Vague prevention rules (e.g., "be more careful", "ensure correctness") are hard-blocked before recording
- [ ] Specific prevention rules (e.g., "always validate commit hash before calling record-learning") pass the gate
- [ ] Subagent receives clear, actionable revision guidance when a rule is rejected
- [ ] Tests pass
- [ ] No regressions
- [ ] E2E verification: trigger learn with a vague prevention and confirm recording is blocked; trigger with a specific prevention and confirm recording succeeds
