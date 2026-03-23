# Plan

## Goal

Add docs/benchmarking.md with plain-English explanation of the SPRT benchmark configuration and its practical
implications for developers.

## Pre-conditions

(none)

## Post-conditions

- [ ] docs/benchmarking.md exists at the project root
- [ ] Document explains the current SPRT configuration (p0=0.95, p1=0.85, α=β=0.05) in plain English
- [ ] Document covers the practical implications: indifference zone, what PASS/FAIL guarantees, sample efficiency
- [ ] No regressions to existing documentation

## Research Findings

The SPRT (Sequential Probability Ratio Test) implementation lives in
`client/src/main/java/io/github/cowwoc/cat/hooks/skills/BenchmarkRunner.java`.

Key constants extracted from source:
- `p0 = 0.95` — the null hypothesis compliance rate (skill is "good enough")
- `p1 = 0.85` — the alternative hypothesis compliance rate (skill is "not good enough")
- `α = β = 0.05` — type I and type II error rates (5% false-positive, 5% false-negative)
- `SPRT_LOG_PASS = 0.1112` — log-likelihood increment per passing observation: ln(0.95/0.85)
- `SPRT_LOG_FAIL = -1.0986` — log-likelihood decrement per failing observation: ln(0.05/0.15)
- `SPRT_ACCEPT = 2.944` — upper boundary: ln((1-β)/α) = ln(19)
- `SPRT_REJECT = -2.944` — lower boundary: ln(β/(1-α)) = ln(0.0526)
- `SMOKE_RUNS = 3` — number of initial smoke runs before escalating to full SPRT
- `PRIOR_BOOST = 1.112` — initial log-ratio applied when prior benchmark shows ACCEPT (≈ 10 prior PASSes)

Benchmark flow:
1. Smoke phase (3 runs): quickly catches obvious failures before committing to full SPRT
2. SPRT phase: runs accumulate until log_ratio crosses SPRT_ACCEPT (Accept) or SPRT_REJECT (Reject)
3. Overall decision: ACCEPT if all test cases ACCEPT; REJECT if any REJECT; INCONCLUSIVE otherwise

Prior boost: when a skill was previously accepted and test cases haven't changed, the SPRT starts from
PRIOR_BOOST (1.112) instead of 0.0, providing a head start equivalent to ~10 prior PASS observations.

Decision interpretations:
- ACCEPT: the skill's compliance rate is likely ≥ 85% (with 95% confidence the true rate is above p1)
- REJECT: the skill's compliance rate is likely < 95% (with 95% confidence the true rate is below p0)
- INCONCLUSIVE: insufficient evidence to decide; more runs needed

Sample efficiency: For a perfectly compliant skill (true rate = 1.0), SPRT accepts after approximately
27 passes (3 smoke + ~24 SPRT runs). For a completely failing skill (true rate = 0.0), SPRT rejects
after approximately 3-4 observations.

## Execution Steps

1. Create `docs/benchmarking.md` with the following sections (line-wrap at 120 chars):
   - License header (HTML comment, per `.claude/rules/license-header.md` — `docs/` is NOT exempt)
   - `# Benchmarking` title
   - `## Overview` — one-paragraph plain-English introduction to SPRT as used in CAT
   - `## Configuration` — table or list of current parameter values with plain-English explanations:
     - p0 = 0.95 (the "good enough" threshold — compliance rate we want to confirm)
     - p1 = 0.85 (the "not good enough" threshold — compliance rate we want to reject)
     - α = 0.05 (false-positive rate — probability of accepting a bad skill)
     - β = 0.05 (false-negative rate — probability of rejecting a good skill)
   - `## Indifference Zone` — explains the [p1, p0] = [0.85, 0.95] range: if a skill's true compliance rate
     falls in this range, either ACCEPT or REJECT is acceptable per the test design
   - `## What PASS and FAIL Guarantee` — explains what ACCEPT/REJECT decisions mean statistically:
     - ACCEPT: the algorithm has gathered enough evidence that the skill's compliance rate likely exceeds p1 (85%)
     - REJECT: the algorithm has gathered enough evidence that the skill's compliance rate is below p0 (95%)
   - `## Sample Efficiency` — explains how SPRT adapts run count dynamically:
     - 3 smoke runs first (fast failure detection)
     - SPRT then continues until evidence crosses a boundary
     - Prior boost for previously-accepted skills (starts with credit equivalent to ~10 prior PASSes)
     - Typical run counts: ~27 runs to accept a perfectly compliant skill; ~3-4 runs to reject a completely failing one
   - `## Benchmark Results Schema` — brief explanation of `benchmark.json` fields developers will encounter:
     - `decision`: ACCEPT | REJECT | INCONCLUSIVE per test case
     - `log_ratio`: current accumulated evidence score (positive = evidence for passing, negative = evidence for failing)
     - `pass_count` / `fail_count` / `total_runs`
     - `overall_decision`: ACCEPT only if ALL test cases accept; REJECT if any reject

2. Update `index.json` in the same commit:
   - Set `status` to `"closed"`

3. Commit both files together with message: `docs: add benchmarking.md explaining SPRT configuration`
   - Use commit type `docs:` (per CLAUDE.md: docs/ files use `docs:` commit type)
   - `docs/benchmarking.md` and `.cat/issues/v2/v2.1/add-benchmarking-docs/index.json` in one commit

## Sub-Agent Waves

### Wave 1
Follow Execution Steps 1–3 exactly:
- Create `docs/benchmarking.md` with license header and all six sections (Overview, Configuration, Indifference Zone,
  What PASS and FAIL Guarantee, Sample Efficiency, Benchmark Results Schema) as specified in Execution Step 1
- Update `.cat/issues/v2/v2.1/add-benchmarking-docs/index.json`: set `"status"` to `"closed"` (Execution Step 2)
- Commit both files with message `docs: add benchmarking.md explaining SPRT configuration` (Execution Step 3)
