# sprt-benchmarking-for-skill-builder

## Goal

Replace skill-builder's single-run benchmark with a statistically rigorous SPRT-based benchmark that achieves 95%
compliance with 95% confidence, and rename "eval" terminology to "benchmark" throughout.

## Background

The current skill-builder runs each benchmark test case once per configuration. A single passing run provides almost no
statistical confidence in the true compliance rate (95% CI lower bound ~2.5%). Additionally, "eval" terminology is used
inconsistently alongside "benchmark" — the aggregator, output file, and iteration language already use "benchmark."

This issue combines two changes:
1. SPRT-based multi-run benchmarking with deterministic grading for statistical confidence
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

### Deterministic grading over LLM grading
Assertions should be machine-checkable (string matching, regex, structural checks) rather than requiring LLM judgment.
This eliminates grader subagents entirely, cutting wall-clock time ~50%.

### Parallel waves of 4
Claude Code allows 4 concurrent background agents. SPRT decision logic runs after each agent completes (pipelined, not
batched per wave). If the likelihood ratio crosses a boundary mid-wave, remaining agents in that wave are wasted but
the cost is negligible with Haiku + caching.

### Hardening and benchmarking are complementary
Hardening improves instruction quality (fixes ambiguities). Benchmarking measures whether instructions produce
compliant output. The workflow is: benchmark → if non-compliant → harden → re-benchmark → repeat until compliant.

## Scope

### In scope
- `plugin/skills/skill-builder-agent/first-use.md` — primary target
- `plugin/skills/skill-builder-agent/e2e-dispute-trace.md` — terminology updates
- SPRT decision logic (Java tool or inline in skill instructions)
- Deterministic assertion grading (Java tool or Bash script)
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
   - Grade results deterministically (no grader subagents)
   - Feed pass/fail into SPRT decision function after each completion (pipelined)
   - SPRT parameters: H₀ ≥ 0.95, H₁ ≤ 0.85, α = 0.05, β = 0.05
   - Accept → compliant, Reject → run hardening → re-benchmark
6. Design deterministic assertion format (machine-checkable assertions)
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
- [ ] Assertions are deterministic (machine-checkable, no LLM grader subagents)
- [ ] SPRT check runs after each individual agent completion (pipelined), not batched per wave
- [ ] After hardening converges, benchmark re-runs to verify compliance improvement
- [ ] Compliance checklist updated to reflect all changes
