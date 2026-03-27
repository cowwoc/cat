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

## Why Run Multiple Tests

A single test run can pass or fail for two very different reasons: the skill's instructions may be defective (a
systematic problem that will recur), or the LLM's sampling may have produced an unlucky output (random noise that will
not reliably recur). A single run cannot distinguish these two cases. Multiple runs can — systematic defects cause
repeated failures, while sampling noise averages out.

This only works if we assume that the causes of failure are stable across runs. A broken instruction that causes 30% of
runs to fail today will cause roughly 30% to fail tomorrow, provided the model and configuration remain the same. That
assumption — that past pass rates are informative about future pass rates under the same conditions — is the
foundational reason for running multiple tests. Without it, testing would be pointless.

SPRT adds statistical rigor to this process. Rather than picking an arbitrary number of runs and eyeballing the results,
SPRT accumulates evidence run by run and stops as soon as there is enough to confidently distinguish a systematic
defect from noise. This matters because each run costs time and API calls; SPRT minimizes that cost while controlling
the risk of wrong verdicts.

## What Benchmark Results Allow You to Say

**With confidence:** "Under the model version and configuration tested, this skill's instructions reliably produce
compliant output for inputs similar to the eval set." Specifically, the pass rate against the eval set likely exceeds
95% (for ACCEPT) or falls below 85% (for REJECT), with at most a 5% chance of either verdict being wrong.

**Not with confidence:**

- That the skill will perform the same way after a model update or provider-side change. The verdict is a snapshot tied
  to the conditions at testing time. When those conditions change, re-benchmarking is needed.
- That the skill handles inputs the eval set does not cover. The verdict measures compliance against the specific test
  cases provided. A narrow eval set produces a narrow verdict. Production inputs that differ substantially from the eval
  set are untested territory.
- The exact pass rate. SPRT decides whether the rate is above 95% or below 85%; it does not estimate the rate itself.
  A skill at 96% and one at 100% both receive ACCEPT. The `pass_count` and `total_runs` fields provide a rough point
  estimate, but SPRT's guarantees are about the decision, not the estimate.
  
## Assumptions

SPRT requires two assumptions to deliver its guaranteed error rates. Both hold reasonably well for LLM skill testing,
but imperfectly.

**Independence.** SPRT assumes each run's outcome does not depend on previous runs. For LLM skill testing, this
assumption is reasonable because each run uses a different test case with different input content, and LLM API calls do
not carry state between requests. The primary source of dependence would be test cases that are so similar they trigger
the same failure mode for the same underlying reason — which is really one defect counted multiple times, not a
violation of independence per se. In practice, a well-designed eval set with diverse test cases satisfies this
assumption adequately. If observations are correlated, the effective sample size is smaller than the run count and the
true error rates may exceed the configured α and β, but the 10% indifference zone provides a buffer against moderate
correlation.

**Stationarity.** SPRT assumes the underlying pass rate does not change during testing. Within a single benchmark
session this holds well — the model version, API configuration, and infrastructure do not change mid-run. Across
sessions, stationarity can break: model updates, provider-side changes, or different deployment environments can shift
the true pass rate. An ACCEPT verdict from last week is not automatically valid this week if the model has changed.
Re-benchmark after known changes to the model or environment.

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

See "What Benchmark Results Allow You to Say" above for a plain-English interpretation of these verdicts.

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

1. **ACCEPT result** — The skill meets the compliance target (≥95% pass rate against the eval set, within statistical
   tolerance). The result applies to the model version and configuration tested; changes to either may require
   re-benchmarking.
2. **REJECT result** — The skill falls below acceptable compliance against the eval set (≤85% within statistical
   tolerance). The skill needs debugging or test case revision before it can be re-benchmarked.
3. **INCONCLUSIVE result** — The test reached its run limit without enough evidence to decide. This is rare and suggests
   the skill's true compliance is near the indifference zone boundary. Run additional tests or review the skill's
   implementation to understand why results are borderline.

**For developers:** Examine the `pass_count` and `fail_count` fields to understand which test cases are problematic.
High `fail_count` on specific test cases often indicates either a bug in the skill or an overly strict test case
definition. The `log_ratio` field shows the direction and magnitude of evidence; negative values that are close to zero
(e.g., `−1.5`) suggest the skill is near the pass/fail boundary and may benefit from focused debugging.
