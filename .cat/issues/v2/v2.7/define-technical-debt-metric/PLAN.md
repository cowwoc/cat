# Plan: define-technical-debt-metric

## Goal
Define what "technical debt" means in the context of CAT, establish a scoring rubric, and document the metric so it can
be implemented consistently across calculation and reporting tools.

## Parent Requirements
- REQ-001

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Metric definition is subjective; a poorly chosen metric produces misleading scores
- **Mitigation:** Research existing metrics (SonarQube SQALE, CodeClimate), adapt to CAT's context, validate with
  concrete examples

## Files to Modify
- `plugin/concepts/technical-debt-metric.md` - Document the metric definition, scoring rubric, and examples

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
1. Research existing technical debt metrics (SQALE, CodeClimate maintainability, CISQ) and summarize strengths/weaknesses
2. Define the CAT technical debt metric:
   - What signals contribute to the score (complexity, duplication, coupling, test coverage gaps, code smells, age of
     TODO/FIXME markers, etc.)
   - How each signal is weighted
   - What the score range is (e.g., 0.0-1.0 where 0 = no debt, 1 = maximum debt)
   - How the score maps to human-readable categories (e.g., A-F grades or low/medium/high/critical)
3. Write `plugin/concepts/technical-debt-metric.md` with the full metric definition
4. Validate the metric against 3-5 real files from this project, showing expected scores and reasoning

## Post-conditions
- [ ] `plugin/concepts/technical-debt-metric.md` exists with complete metric definition
- [ ] Metric includes: signals, weights, score range, category mapping
- [ ] At least 3 worked examples demonstrating the metric on real files
