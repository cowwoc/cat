# sprt-benchmarking-for-skill-builder

## Goal

Replace skill-builder's single-run benchmark with a statistically rigorous SPRT-based benchmark that achieves 95%
compliance with 95% confidence, and rename "eval" terminology to "benchmark" throughout.

## Background

The current skill-builder runs each benchmark test case once per configuration. A single passing run provides almost no
statistical confidence in the true compliance rate (95% CI lower bound ~2.5%). Additionally, "eval" terminology is used
inconsistently alongside "benchmark" — the aggregator, output file, and iteration language already use "benchmark."

This issue combines two changes:
1. SPRT-based multi-run benchmarking with hybrid grading (deterministic + LLM) for statistical confidence
2. Consistent "benchmark" terminology throughout

## Design Decisions

### SPRT over fixed-sample testing
Wald's Sequential Probability Ratio Test allows early stopping when evidence is sufficient, reducing average sample
count by ~50% compared to fixed-sample tests (72 → ~35 runs on average). Each run uses a fresh subagent to ensure
statistical independence.

### Haiku for benchmark runs
Benchmark runs test instruction clarity, not model capability. A skill that Haiku follows correctly will be followed by
Opus. Haiku runs cost ~10-20x less than Opus, and automatic prompt caching gives 90% discount on input tokens for
subagents 2-N within each 5-minute window.

### Prefer deterministic grading, allow LLM grading
Assertions should be deterministic where feasible (string match, regex, structural checks) — these are graded inline
with no subagent overhead. However, since the skill-builder creates skills for arbitrary purposes, some assertions
require semantic judgment ("explanation is accurate," "code handles edge cases"). These assertions spawn Haiku grader
subagents. The skill-builder should maximize the ratio of deterministic to LLM-graded assertions when designing test
cases, but must not prohibit LLM-graded ones.

### Parallel waves of 4
Claude Code allows 4 concurrent background agents. SPRT decision logic runs after each agent completes (pipelined, not
batched per wave). If the likelihood ratio crosses a boundary mid-wave, remaining agents in that wave are wasted but
the cost is negligible with Haiku + caching.

### Early rejection across test cases
Each test case runs its own independent SPRT. As soon as any test case rejects (non-compliant), all remaining test
cases stop immediately and the workflow proceeds to hardening. For non-compliant skills, this typically catches the
problem within 3-5 runs of the weakest test case rather than completing ~35 runs across all cases.

### Inline deterministic grading
Eval-run subagents grade their own deterministic assertions (regex, string match, structural checks) before returning,
eliminating a separate grader subagent round-trip per run. Only semantic assertions that require judgment spawn a
separate Haiku grader subagent. The eval-run subagent returns pass/fail results for deterministic assertions alongside
its output metadata.

### Ephemeral run outputs
Individual run output files are written to temp files (no git commit), not committed per-run. Only the final
`benchmark.json` is committed after SPRT completes. This eliminates git lock contention across parallel agents and
reduces per-run latency. Run outputs are ephemeral — they exist only for grading, which happens immediately.

### Hardening and benchmarking are complementary
Hardening improves instruction quality (fixes ambiguities). Benchmarking measures whether instructions produce
compliant output. The workflow is: benchmark → if non-compliant → harden → re-benchmark → repeat until compliant.

### Hardening + benchmarking as atomic unit
Hardening without benchmarking leaves compliance unmeasured. Benchmarking without hardening means accepting whatever
compliance rate the first draft achieves. The two must always run together as a single phase — never one without the
other. When `effort` is `low`, skip this entire phase (accept the skill draft as-is after the single-run sanity check).
When `effort` is `medium` or `high`, run the full hardening + benchmark loop.

## Scope

### In scope
- `plugin/skills/skill-builder-agent/first-use.md` — primary target
- `plugin/skills/skill-builder-agent/e2e-dispute-trace.md` — terminology updates
- SPRT decision logic (Java tool or inline in skill instructions)
- Hybrid assertion grading: deterministic (Java/Bash) for structural checks, Haiku LLM for semantic checks
- Benchmark-then-harden feedback loop

### Out of scope
- Changes to adversarial TDD loop logic (hardening itself)
- Changes to skill-analyzer-agent or skill-grader-agent beyond terminology

## Changes

### Terminology rename
1. Rename commit message prefix: `eval:` → `benchmark:`
2. Rename directory: `eval-artifacts/` → `benchmark-artifacts/`
3. Rename variables: `EVAL_ARTIFACTS_DIR` → `BENCHMARK_ARTIFACTS_DIR`, `EVAL_SET_SHA` → `BENCHMARK_SET_SHA`
4. Update step labels and compliance checklist

### SPRT benchmark workflow
5. Replace single-run benchmark with SPRT loop:
   - Spawn waves of 4 Haiku eval-run subagents (fresh, independent)
   - Each subagent grades deterministic assertions inline and returns pass/fail with output
   - Spawn Haiku grader subagents only for semantic assertions
   - Write run outputs to temp files (no per-run git commits)
   - Feed pass/fail into per-test-case SPRT decision function after each completion (pipelined)
   - SPRT parameters: H₀ ≥ 0.95, H₁ ≤ 0.85, α = 0.05, β = 0.05
   - Early rejection: if any test case rejects, stop all cases and proceed to hardening
   - Accept (all cases) → compliant, Reject (any case) → harden → re-benchmark
   - Entire phase skipped when effort = low
6. Design hybrid assertion format: deterministic type (regex, string match, structural) graded inline; semantic type
   graded by Haiku subagent. Skill-builder should prefer deterministic assertions where possible.
7. Implement SPRT decision function
8. Add re-benchmark step after hardening converges

## Acceptance Criteria

- [ ] No occurrences of `eval-artifacts`, `EVAL_ARTIFACTS_DIR`, `EVAL_SET_SHA`, or `eval:` commit prefix in
  `plugin/skills/skill-builder-agent/first-use.md`
- [ ] No occurrences of `eval-artifacts` or `eval:` in `plugin/skills/skill-builder-agent/e2e-dispute-trace.md`
- [ ] All renamed terms use `benchmark-artifacts`, `BENCHMARK_ARTIFACTS_DIR`, `BENCHMARK_SET_SHA`, `benchmark:`
- [ ] Benchmark runs use Haiku model for eval-run subagents
- [ ] Each benchmark run uses a fresh (non-resumed) subagent
- [ ] SPRT decision logic implemented with parameters: H₀ ≥ 0.95, H₁ ≤ 0.85, α = 0.05, β = 0.05
- [ ] Assertion format supports both deterministic (machine-checkable) and semantic (LLM-graded) types
- [ ] Deterministic assertions are graded inline without subagent overhead
- [ ] Semantic assertions use Haiku grader subagents
- [ ] Skill-builder maximizes deterministic-to-semantic assertion ratio when generating test cases
- [ ] SPRT check runs after each individual agent completion (pipelined), not batched per wave
- [ ] After hardening converges, benchmark re-runs to verify compliance improvement
- [ ] Compliance checklist updated to reflect all changes
- [ ] Each test case runs its own independent SPRT; rejection of any case stops all remaining cases
- [ ] Eval-run subagents grade deterministic assertions inline and return pass/fail results
- [ ] Run outputs are written to temp files, not committed per-run; only benchmark.json is committed
- [ ] Hardening + benchmarking phase only runs when effort > low (skipped entirely at effort = low)
- [ ] Hardening and benchmarking are always paired — never one without the other
