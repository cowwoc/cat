<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Benchmarking

## Overview

CAT uses the Sequential Probability Ratio Test (SPRT) to decide whether a skill meets its compliance target. SPRT is
a statistically efficient hypothesis test that accumulates evidence run by run, stopping as soon as enough evidence
exists to render a verdict — without committing to a fixed sample size upfront. Each benchmark run produces a
pass/fail observation, and the test maintains a running log-likelihood ratio that moves toward an "accept" boundary
when the skill performs well and toward a "reject" boundary when it does not. This makes SPRT faster than fixed-sample
tests for skills that clearly pass or clearly fail, while still controlling error rates rigorously.

**Practical consequence:** Rather than running a fixed number of tests (e.g., always 100), SPRT stops early when the
evidence becomes conclusive. A skill that clearly passes might be accepted after 20 runs; a skill that clearly fails
might be rejected after just 3-4 runs. This efficiency is why SPRT is preferred over simpler testing methods.

## Configuration

| Parameter | Value | Plain-English Meaning |
|-----------|-------|-----------------------|
| `p0` | 0.95 | The "good enough" compliance rate — the null hypothesis the test tries to confirm |
| `p1` | 0.85 | The "not good enough" compliance rate — the alternative the test tries to reject |
| `α` | 0.05 | False-positive rate — probability of accepting a skill whose true rate is below p1 |
| `β` | 0.05 | False-negative rate — probability of rejecting a skill whose true rate is above p0 |

**Design Rationale:**

The 10% gap between p0 and p1 (95% vs 85%) represents the practical difference CAT cares about. A skill at 90%
compliance is genuinely borderline; the test is designed to tolerate ambiguity in this middle zone rather than force a
verdict. The error rates (α and β = 0.05 each) mean CAT accepts a 5% risk of either false acceptance or false
rejection. These choices balance test rigor against feedback speed: stricter error rates (e.g., 1%) would require many
more test runs; looser error rates (e.g., 10%) would accept more unreliable verdicts.

## Indifference Zone

The indifference zone is the range `[p1, p0]` = `[0.85, 0.95]`. If a skill's true compliance rate falls anywhere
within this interval, the test design deliberately treats either ACCEPT or REJECT as an acceptable outcome. The test
is only required to distinguish skills above 95% from skills below 85%; it makes no promise about skills in between.
In practice this means a skill that passes 90% of the time may receive either verdict, and that is by design — the
zone represents a region of ambiguity where the practical difference between accepting and rejecting is small.

## What ACCEPT, REJECT, and INCONCLUSIVE Guarantee

- **ACCEPT** means the algorithm has accumulated enough statistical evidence that the skill's compliance rate likely
  exceeds p0 (95%). This is a probabilistic statement, not a certainty: the false-negative rate β = 0.05 means there
  is a 5% chance of accepting a skill whose true rate is actually below p0.
- **REJECT** means the algorithm has accumulated enough evidence that the skill's compliance rate is likely below p1
  (85%). Again, a probabilistic claim: the false-positive rate α = 0.05 means there is a 5% chance of rejecting a
  skill whose true rate is actually above p1.
- **INCONCLUSIVE** means neither boundary was crossed before the run limit was reached. The evidence is ambiguous;
  more runs are needed before a verdict can be rendered. To resolve INCONCLUSIVE results, either increase the run
  limit for more evidence or investigate whether the skill's true compliance is genuinely near the indifference zone
  boundary (between 85% and 95%).

**What these verdicts mean in practice:**

An ACCEPT decision does not guarantee the skill always passes — it guarantees the evidence is strong enough to conclude
the skill passes at least 95% of the time, given the configured error tolerance. A REJECT verdict means the evidence
strongly indicates the skill's compliance is below 85%, making it unsuitable for production use. INCONCLUSIVE means the
test ran to its limit without decisive evidence; additional runs or investigation may be needed.

## Sample Efficiency

SPRT adapts the number of runs dynamically rather than fixing a sample size in advance:

1. **Smoke phase (3 runs):** Three runs execute first to catch obvious failures quickly. If the skill fails all
   three smoke runs, the test rejects immediately without entering the full SPRT loop.
2. **SPRT phase:** After the smoke phase, runs continue accumulating until the log-likelihood ratio crosses either
   the accept boundary (`ln((1−β)/α) ≈ 2.944`) or the reject boundary (`ln(β/(1−α)) ≈ −2.944`).
3. **Prior boost:** When a skill was previously benchmarked and received an ACCEPT verdict and its test cases have not
   changed, the SPRT starts with a log-ratio of 1.112 rather than 0. This is equivalent to approximately 10 prior
   PASS observations and gives frequently-tested skills a head start, reducing the number of re-runs needed to
   confirm a stable skill.

**Typical run counts under these settings:**

- A perfectly compliant skill (true rate = 100%) accepts after approximately 27 runs total (3 smoke + ~24 SPRT).
- A completely failing skill (true rate = 0%) rejects after approximately 3–4 runs.
- A borderline skill (true rate = 90%, within the indifference zone) typically accepts within 30–50 runs (though
  occasional rejection is possible, since 90% is statistically closer to the 95% pass boundary than the 85% fail boundary).

**Practical expectations:**

The actual time to reach a verdict depends on how long each benchmark run takes for your skill. If each run takes 1
second, 27 runs requires roughly 30 seconds. If each run takes 5 seconds, that same skill requires roughly 2 minutes.
Skills with longer-running test cases (e.g., integration tests, API calls) may see higher wall-clock times despite the
same run count. The advantage of SPRT is that clearly passing or failing skills get quick verdicts; only borderline
skills require the full number of runs.

## Benchmark Results Schema

Each test case in `benchmark.json` contains the following fields:

| Field | Type | Description |
|-------|------|-------------|
| `decision` | string | Per-test-case verdict: `ACCEPT`, `REJECT`, or `INCONCLUSIVE` |
| `log_ratio` | number | The statistical evidence score SPRT uses to track whether recent runs favor pass or fail. Positive values mean more evidence of passing (skill is performing well); negative values mean more evidence of failing (skill has problems). When this crosses +2.944, the test accepts; at −2.944, it rejects |
| `pass_count` | integer | Number of runs in which the test case passed |
| `fail_count` | integer | Number of runs in which the test case failed |
| `total_runs` | integer | Total number of runs executed for this test case |
| `overall_decision` | string | Aggregate verdict across all test cases: `ACCEPT` only when every test case accepts; `REJECT` if any test case rejects; `INCONCLUSIVE` otherwise |

## Workflow: Interpreting Benchmark Results

Benchmark results are written to `benchmark.json` in the CAT work directory. When a skill is executed against its test
cases:

1. **ACCEPT result** — The skill meets the compliance target (≥95% pass rate within statistical tolerance). The skill is
   ready for production use.
2. **REJECT result** — The skill falls below acceptable compliance (≤85% within statistical tolerance). The skill needs
   debugging or test case revision before it can be deployed.
3. **INCONCLUSIVE result** — The test reached its run limit without enough evidence to decide. This is rare and suggests
   the skill's true compliance is near the indifference zone boundary. Run additional tests or review the skill's
   implementation to understand why results are borderline.

**For developers:** Examine the `pass_count` and `fail_count` fields to understand which test cases are problematic.
High `fail_count` on specific test cases often indicates either a bug in the skill or an overly strict test case
definition. The `log_ratio` field shows the direction and magnitude of evidence; negative values that are close to zero
(e.g., `−1.5`) suggest the skill is near the pass/fail boundary and may benefit from focused debugging.
